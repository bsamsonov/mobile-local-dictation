package com.bvs.localdictation;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
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
    /** Test audio (raw 16 kHz 16-bit mono PCM) played instead of the microphone; null normally. */
    private InputStream file;
    private Runnable fileEnd;
    private Thread thread;
    private volatile boolean running;

    // Guarded by lock.
    private OutputStream out;
    private ParcelFileDescriptor readEnd;
    private long replayFrom = -1;

    /** Set when the pipe is full, i.e. the engine is not reading our audio. */
    private volatile boolean blocked;
    /** Set when the engine closed its end of the current session's pipe. */
    private volatile boolean broken;

    /** Starts recording; {@code input} is the preferred microphone (null: the system picks). */
    boolean start(AudioDeviceInfo input) {
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
        if (input != null && !rec.setPreferredDevice(input)) Log.w(TAG, "preferred input not set");
        rec.startRecording();
        running = true;
        thread = new Thread(this::loop, "AudioFeeder");
        thread.start();
        Log.d(TAG, "feeder started, input type=" + (input == null ? -1 : input.getType()));
        return true;
    }

    /**
     * Test mode: feeds raw PCM (16 kHz, 16-bit, mono) at real-time pace instead of the microphone,
     * then {@code tailMs} of silence, then calls {@code onEnd} (on the feeder thread) once.
     */
    boolean startFile(InputStream pcm, long tailMs, Runnable onEnd) {
        file = pcm;
        fileTail = tailMs;
        fileEnd = onEnd;
        running = true;
        thread = new Thread(this::loop, "AudioFeeder");
        thread.start();
        Log.d(TAG, "feeder started from a file");
        return true;
    }

    private long fileTail;
    private long fileNext;

    /** Next 20 ms of the test file (silence after its end), paced by the clock. */
    private int readFile(byte[] buf) {
        long now = SystemClock.elapsedRealtime();
        if (fileNext == 0) fileNext = now;
        fileNext += 20;
        if (fileNext > now) {
            try { Thread.sleep(fileNext - now); } catch (InterruptedException e) { return 0; }
        }
        int n = 0;
        try {
            while (file != null && n < buf.length) {
                int r = file.read(buf, n, buf.length - n);
                if (r < 0) { file.close(); file = null; break; }
                n += r;
            }
        } catch (IOException e) {
            file = null;
        }
        if (n < buf.length) {
            Arrays.fill(buf, n, buf.length, (byte) 0);
            if (n == 0 && fileEnd != null && (fileTail -= 20) <= 0) {
                Runnable r = fileEnd;
                fileEnd = null;
                r.run();
            }
        }
        return buf.length;
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

    boolean isBroken() { return broken; }

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
            broken = false;
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
            int n = rec != null ? rec.read(buf, 0, buf.length) : readFile(buf);
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
                // EAGAIN: pipe full (engine not reading). EPIPE: the engine closed its end, which it
                // does at the end of a session. EBADF: we closed this pipe for the next session.
                boolean full = e.getCause() instanceof ErrnoException
                        ? ((ErrnoException) e.getCause()).errno == OsConstants.EAGAIN
                        : String.valueOf(e.getMessage()).contains("EAGAIN");
                boolean current;
                synchronized (lock) { current = o == out; }
                if (!current) continue;
                if (!blocked && !broken) Log.d(TAG, "feeder write failed: " + e.getMessage());
                if (full) blocked = true;
                else broken = true;
            }
        }
    }
}
