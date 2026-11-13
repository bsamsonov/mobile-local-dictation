package com.bvs.localdictation;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

/**
 * Development tool, built only by "./build.sh trace" into a separate package. Plays a PCM file to
 * the on-device recognizer through the same session loop as the keyboard and logs every engine
 * event WITH its text (tag LDTrace), so a recognition problem can be reproduced without speaking
 * and replayed in unit tests. Not part of the released app.
 *
 * adb shell am startservice -n com.bvs.localdictation.trace/com.bvs.localdictation.TraceService \
 *     --es file /sdcard/Android/data/com.bvs.localdictation.trace/files/test.pcm --es lang ru-RU
 */
public class TraceService extends Service implements RecognitionListener {
    private static final String TAG = "LDTrace";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private AudioFeeder feeder;
    private boolean running;
    private long t0;
    private long replayFrom;
    private boolean sessionLive;
    private boolean hadPartial;

    private String lang;
    private boolean segmented, formatting, recreate;
    private long silenceMs, restartMs;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        if (running) stopRun("restart");
        foreground();
        String path = in.getStringExtra("file");
        File f = path != null ? new File(path) : new File(getExternalFilesDir(null), "test.pcm");
        lang = in.getStringExtra("lang") == null ? "ru-RU" : in.getStringExtra("lang");
        segmented = in.getBooleanExtra("segmented", true);
        formatting = in.getBooleanExtra("formatting", true);
        recreate = in.getBooleanExtra("recreate", true);
        silenceMs = in.getLongExtra("silence", 30_000L);
        restartMs = in.getLongExtra("restart", 200L);
        t0 = SystemClock.elapsedRealtime();
        ev("START", f + " lang=" + lang + " segmented=" + segmented + " formatting=" + formatting
                + " recreate=" + recreate + " silence=" + silenceMs + " restart=" + restartMs);
        try {
            feeder = new AudioFeeder();
            feeder.startFile(new FileInputStream(f), in.getLongExtra("tail", 4000L),
                    () -> handler.post(() -> endOfFile(in.getStringExtra("stop"))));
        } catch (Exception e) {
            ev("FAIL", e.toString());
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        replayFrom = SystemClock.elapsedRealtime();
        beginSession();
        return START_NOT_STICKY;
    }

    /** Started from adb while the app has no window: must be a foreground service to be allowed to run. */
    private void foreground() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        nm.createNotificationChannel(new android.app.NotificationChannel("trace", "Trace",
                android.app.NotificationManager.IMPORTANCE_LOW));
        android.app.Notification n = new android.app.Notification.Builder(this, "trace")
                .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Dictation trace").build();
        try {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            Log.i(TAG, "0|fgs|microphone");
        } catch (RuntimeException e) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            Log.i(TAG, "0|fgs|dataSync (" + e.getMessage() + ")");
        }
    }

    private void ev(String name, String text) {
        Log.i(TAG, (SystemClock.elapsedRealtime() - t0) + "|" + name + "|" + (text == null ? "" : text.replace("\n", "\\n")));
    }

    /** "stop": what the keyboard does on Stop: "listening" = stopListening(), "eof" = close the pipe first. */
    private void endOfFile(String stop) {
        if (stop == null) { stopRun("end of file"); return; }
        ev("STOP", stop);
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (stop.contains("eof")) feeder.endSession();
        if (stop.contains("listening")) recognizer.stopListening();
        handler.postDelayed(() -> stopRun("end of file"), 3000);
    }

    private void stopRun(String why) {
        ev("DONE", why);
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (feeder != null) { feeder.stop(); feeder = null; }
        if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); recognizer = null; }
        stopSelf();
    }

    @Override public void onDestroy() {
        if (running) stopRun("destroyed");
        super.onDestroy();
    }

    private void beginSession() {
        if (!running) return;
        sessionLive = false;
        hadPartial = false;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        if (segmented) {
            i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs);
        }
        if (formatting) i.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
        try {
            ParcelFileDescriptor pfd = feeder.newSession(replayFrom);
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd);
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioFeeder.RATE);
        } catch (Exception e) {
            ev("FAIL", e.toString());
            stopRun("pipe");
            return;
        }
        ev("startListening", "replay " + (SystemClock.elapsedRealtime() - replayFrom) + " ms");
        recognizer.startListening(i);
    }

    private void markHandoff() {
        if (!sessionLive) return;
        sessionLive = false;
        replayFrom = SystemClock.elapsedRealtime() - 150;
    }

    private void restartSoon(long ms) {
        if (!running) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::beginSession, ms);
    }

    private void recreate() {
        if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); }
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
    }

    private static String all(Bundle b) {
        ArrayList<String> l = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null ? "<null>" : String.join(" ## ", l);
    }

    @Override public void onReadyForSpeech(Bundle params) { sessionLive = true; ev("ready", ""); }
    @Override public void onBeginningOfSpeech() { ev("beginSpeech", ""); }
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { markHandoff(); ev("endSpeech", ""); }

    @Override public void onPartialResults(Bundle b) {
        String t = all(b);
        if (!t.trim().isEmpty()) hadPartial = true;
        ev("partial", t);
    }

    @Override public void onResults(Bundle b) {
        ev("results", all(b));
        markHandoff();
        hadPartial = false;
        restartSoon(restartMs);
    }

    @Override public void onSegmentResults(Bundle b) { ev("segment", all(b)); hadPartial = false; }

    @Override public void onEndOfSegmentedSession() {
        markHandoff();
        ev("endSegmented", "");
        restartSoon(100);
    }

    @Override public void onError(int error) {
        ev("error", Speech.errorName(error) + (hadPartial ? " (partial pending)" : ""));
        markHandoff();
        if (!running) return;
        if (hadPartial) {
            if (recreate && (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) recreate();
            restartSoon(restartMs);
            return;
        }
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                recognizer.cancel();
                restartSoon(300);
                return;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                recreate();
                restartSoon(500);
                return;
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
            case SpeechRecognizer.ERROR_CLIENT:
                recreate();
                break;
            default:
                break;
        }
        restartSoon(400);
    }

    @Override public void onEvent(int eventType, Bundle params) {}
}
