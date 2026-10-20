package com.bvs.localdictation;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Records the microphone continuously and feeds it to recognition sessions through a pipe
 * (RecognizerIntent.EXTRA_AUDIO_SOURCE, Android 13+). The on-device engine ends a session at
 * every pause and only one session can run at a time, so with the engine's own mic capture the
 * words spoken while the next session starts are lost. Here every new session first receives the
 * audio recorded since the previous one stopped consuming, then live audio.
 */
final class AudioFeeder {
    private static final String TAG = "LocalDictation";
    static final int RATE = 16000;
    private static final int CHUNK_BYTES = RATE / 50 * 2;     // 20 ms of 16-bit mono
    private static final long KEEP_MS = 3000;                 // ring buffer length
    private static final long MAX_REPLAY_MS = 1800;

    private static final class Chunk {
        final long t; final byte[] data;
        Chunk(long t, byte[] data) { this.t = t; this.data = data; }
    }

    private final Object lock = new Object();
    private final ArrayDeque<Chunk> ring = new ArrayDeque<>();
    private AudioRecord rec;
    private Thread thread;
    private volatile boolean running;

    // Guarded by lock.
    private OutputStream out;
    private ParcelFileDescriptor readEnd;
    private long replayFrom = -1;

    /** Set when the pipe is full, i.e. the engine is not reading our audio. */
    private volatile boolean blocked;

    boolean start() {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE * 2 * 2));
        } catch (RuntimeException e) {
            Log.w(TAG, "AudioRecord failed", e);
            return false;
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            rec = null;
            return false;
        }
        rec.startRecording();
        running = true;
        thread = new Thread(this::loop, "AudioFeeder");
        thread.start();
        Log.d(TAG, "feeder started");
        return true;
    }

    void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) {}
            thread = null;
        }
        if (rec != null) {
            try { rec.stop(); } catch (RuntimeException ignored) {}
            rec.release();
            rec = null;
        }
        synchronized (lock) {
            closePipeLocked();
            ring.clear();
        }
        Log.d(TAG, "feeder stopped");
    }

    boolean isBlocked() { return blocked; }

    /**
     * Opens a pipe for the next session. Audio recorded since {@code sinceMs}
     * (SystemClock.elapsedRealtime) is written first. Returns the read end for the intent.
     */
    ParcelFileDescriptor newSession(long sinceMs) throws IOException {
        ParcelFileDescriptor[] p = ParcelFileDescriptor.createPipe();
        try {
            // Never block the recording thread: if the engine doesn't read, writes fail with EAGAIN.
            Os.fcntlInt(p[1].getFileDescriptor(), OsConstants.F_SETFL, OsConstants.O_NONBLOCK);
        } catch (Exception e) {
            Log.w(TAG, "O_NONBLOCK failed", e);
        }
        synchronized (lock) {
            closePipeLocked();
            out = new ParcelFileDescriptor.AutoCloseOutputStream(p[1]);
            readEnd = p[0];
            // Cap the replay below the 64 KB pipe buffer (~2 s) so a non-blocking write never overflows it.
            replayFrom = Math.max(sinceMs, SystemClock.elapsedRealtime() - MAX_REPLAY_MS);
            blocked = false;
        }
        return p[0];
    }

    /** Signals end of audio to the current session (EOF). */
    void endSession() {
        synchronized (lock) { closePipeLocked(); }
    }

    private void closePipeLocked() {
        if (out != null) { try { out.close(); } catch (IOException ignored) {} out = null; }
        if (readEnd != null) { try { readEnd.close(); } catch (IOException ignored) {} readEnd = null; }
        replayFrom = -1;
    }

    private void loop() {
        byte[] buf = new byte[CHUNK_BYTES];
        while (running) {
            int n = rec.read(buf, 0, buf.length);
            if (n <= 0) continue;
            long now = SystemClock.elapsedRealtime();
            byte[] data = Arrays.copyOf(buf, n);
            OutputStream o;
            List<byte[]> toWrite;
            synchronized (lock) {
                ring.addLast(new Chunk(now, data));
                while (!ring.isEmpty() && now - ring.peekFirst().t > KEEP_MS) ring.removeFirst();
                o = out;
                if (o == null) continue;
                if (replayFrom >= 0) {
                    toWrite = new ArrayList<>();
                    for (Chunk c : ring) if (c.t >= replayFrom) toWrite.add(c.data);  // includes `data`
                    replayFrom = -1;
                    Log.d(TAG, "feeder replay " + toWrite.size() * 20 + " ms");
                } else {
                    toWrite = java.util.Collections.singletonList(data);
                }
            }
            try {
                for (byte[] b : toWrite) o.write(b);
            } catch (IOException e) {
                // EAGAIN: pipe full (engine not reading); EPIPE: engine closed its end.
                if (!blocked) Log.d(TAG, "feeder write failed: " + e.getMessage());
                blocked = true;
            }
        }
    }
}
