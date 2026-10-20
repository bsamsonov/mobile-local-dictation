package com.bvs.localdictation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Voice-only "keyboard". Recognition goes strictly through
 * SpeechRecognizer.createOnDeviceSpeechRecognizer(), i.e. the system on-device engine,
 * never the cloud, regardless of network state. Text is inserted at the cursor of
 * whatever app is focused: partial results as composing (underlined) text, final
 * results committed.
 */
public class DictationIME extends InputMethodService implements RecognitionListener {

    private static final String TAG = "LocalDictation";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    /** User wants dictation on (mic toggled). Sessions are restarted while this is true. */
    private boolean listening;
    private int consecutiveErrors;
    private int busyRetries;

    // Own continuous mic capture fed to the engine (EXTRA_AUDIO_SOURCE), so nothing is lost while
    // a new session starts after each pause. Null if disabled or unsupported by the engine.
    private AudioFeeder feeder;
    private boolean feederUnsupported;
    /** Audio recorded after this moment (elapsedRealtime) has not been consumed by any session. */
    private long replayFrom;
    /** The current session became ready and has not ended yet. */
    private boolean sessionLive;
    private final Runnable feederCheck = () -> {
        if (feeder != null && feeder.isBlocked() && listening) {
            Log.w(TAG, "engine does not read EXTRA_AUDIO_SOURCE; falling back to its own mic");
            feederUnsupported = true;
            stopFeeder();
            recreateRecognizer();
            restartSoon(200);
        }
    };

    /** Debounced auto-start: some apps (Samsung Notes) do start/finish/start of input within ~150 ms. */
    private final Runnable autoStart = this::startListening;
    /** Fires if the engine stalls: mic never opens, or speech began but no text comes back. */
    private final Runnable watchdog = () -> {
        if (!listening) return;
        Log.w(TAG, "watchdog: recognizer stalled, rebinding");
        updateUi(getString(R.string.status_stalled));
        recreateRecognizer();
        restartSoon(300);
    };

    // Per-segment formatting state, decided on the first partial of each utterance.
    private boolean segmentStarted;
    private String segmentPrefix = "";
    private boolean segmentCapitalize;

    private TextView status;
    private Button mic;

    // ---------------------------------------------------------------- UI

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(32, 33, 36));
        int p = dp(8);
        root.setPadding(p, p, p, p);

        status = new TextView(this);
        status.setTextColor(Color.rgb(200, 200, 200));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, dp(6));
        root.addView(status);

        LinearLayout row1 = row();
        Button kbd = key("⌨", v -> switchBack());
        kbd.setOnLongClickListener(v -> { showPicker(); return true; });
        row1.addView(kbd, weight(1));
        mic = key("", v -> toggle());
        mic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mic.setTypeface(Typeface.DEFAULT_BOLD);
        row1.addView(mic, weight(3));
        Button del = key("⌫", v -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL));
        row1.addView(del, weight(1));
        root.addView(row1, new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout row2 = row();
        row2.addView(key("⚙", v -> openSettings()), weight(1));
        row2.addView(key(",", v -> commitRaw(",")), weight(1));
        row2.addView(key(".", v -> commitRaw(".")), weight(1));
        row2.addView(key("?", v -> commitRaw("?")), weight(1));
        row2.addView(key(getString(R.string.key_space), v -> commitRaw(" ")), weight(3));
        Button enter = key("↵", v -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER));
        enter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        enter.setTypeface(Typeface.DEFAULT_BOLD);
        enter.setPadding(0, 0, 0, 0);
        row2.addView(enter, weight(1));
        root.addView(row2, new LinearLayout.LayoutParams(-1, dp(52)));

        updateUi(null);
        return root;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private void openSettings() {
        haltListening();
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private Button key(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, -1, w);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void updateUi(String msg) {
        if (mic == null) return;
        mic.setText(listening ? R.string.mic_stop : R.string.mic_start);
        mic.setTextColor(listening ? Color.rgb(229, 57, 53) : Color.BLACK);
        if (msg != null) status.setText(msg);
        else if (!listening) status.setText(getString(R.string.status_idle, Speech.language(this)));
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        Log.d(TAG, "onStartInputView restarting=" + restarting + " pkg=" + info.packageName);
        consecutiveErrors = 0;
        handler.removeCallbacks(autoStart);
        if (!restarting && !listening && Speech.autoStart(this)) handler.postDelayed(autoStart, 250);
        else updateUi(null);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        haltListening();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onDestroy() {
        stopFeeder();
        haltListening();
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- control

    private void toggle() {
        if (listening) {
            listening = false;
            stopFeeder();
            handler.removeCallbacksAndMessages(null);
            // stopListening() still delivers onResults for what was already said.
            if (recognizer != null) recognizer.stopListening();
            updateUi(getString(R.string.status_stopped));
        } else {
            consecutiveErrors = 0;
            startListening();
        }
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateUi(getString(R.string.status_no_mic));
            return;
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            updateUi(getString(R.string.status_no_ondevice));
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
        }
        if (feeder == null && !feederUnsupported && Build.VERSION.SDK_INT >= 33 && Speech.continuousAudio(this)) {
            feeder = new AudioFeeder();
            if (!feeder.start()) feeder = null;
        }
        replayFrom = SystemClock.elapsedRealtime();
        listening = true;
        busyRetries = 0;
        beginSession();
    }

    private void beginSession() {
        if (!listening || recognizer == null) return;
        segmentStarted = false;
        Log.d(TAG, "startListening " + Speech.language(this) + " feeder=" + (feeder != null));
        sessionLive = false;
        Intent intent = Speech.recognizeIntent(this);
        if (feeder != null) {
            try {
                ParcelFileDescriptor pfd = feeder.newSession(replayFrom);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioFeeder.RATE);
            } catch (IOException e) {
                Log.w(TAG, "pipe failed", e);
                stopFeeder();
            }
        }
        recognizer.startListening(intent);
        arm(4000);   // expect onReadyForSpeech (mic opened) soon
        updateUi(getString(R.string.status_connecting));
    }

    /** Hard stop (keyboard hidden): keep whatever partial text is already shown. */
    private void haltListening() {
        listening = false;
        stopFeeder();
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) recognizer.cancel();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        segmentStarted = false;
        updateUi(null);
    }

    private void stopFeeder() {
        handler.removeCallbacks(feederCheck);
        if (feeder != null) {
            feeder.stop();
            feeder = null;
        }
    }

    /** First end-of-session event of a live session: the next session replays audio from here. */
    private void markHandoff() {
        if (!sessionLive) return;   // late event of an older session, or a session that never started
        sessionLive = false;
        replayFrom = SystemClock.elapsedRealtime() - 150;
        if (feeder != null) feeder.endSession();
    }

    private void arm(long ms) {
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, ms);
    }

    private void restartSoon(long delayMs) {
        if (!listening) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::beginSession, delayMs);
    }

    private void switchBack() {
        haltListening();
        if (!switchToPreviousInputMethod()) showPicker();
    }

    private void showPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showInputMethodPicker();
    }

    // ---------------------------------------------------------------- text insertion

    private void commitRaw(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.finishComposingText();
        ic.commitText(s, 1);
        segmentStarted = false;
    }

    /** Decide leading space / capitalisation from the text before the cursor. */
    private void decideSegmentFormat(InputConnection ic) {
        CharSequence before = ic.getTextBeforeCursor(64, 0);
        String b = before == null ? "" : before.toString();
        String trimmed = b.replaceAll("\\s+$", "");
        boolean endsWithSpace = !b.isEmpty() && Character.isWhitespace(b.charAt(b.length() - 1));
        segmentPrefix = (b.isEmpty() || endsWithSpace) ? "" : " ";
        segmentCapitalize = trimmed.isEmpty() || ".!?…".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0
                || b.replaceAll("[ \\t]+$", "").endsWith("\n");
        segmentStarted = true;
    }

    private String format(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return "";
        boolean ru = Speech.isRussian(this);
        if (ru && Speech.voiceCommands(this)) t = TextPost.voiceCommands(t);
        if (ru && Speech.digits(this)) t = TextPost.numbersToDigits(t);
        if (t.startsWith("\n")) return t;   // starts with a line break: no leading space, already capitalised
        if (segmentCapitalize) {
            t = t.substring(0, 1).toUpperCase() + t.substring(1);
        } else if (t.length() > 1 && Character.isUpperCase(t.charAt(0)) && Character.isLowerCase(t.charAt(1))) {
            // The engine capitalises every utterance after a pause; mid-sentence we lowercase it
            // (all-caps acronyms are left alone since their second letter is uppercase).
            t = t.substring(0, 1).toLowerCase() + t.substring(1);
        }
        return segmentPrefix + t;
    }

    private static String best(Bundle b) {
        if (b == null) return "";
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return list == null || list.isEmpty() || list.get(0) == null ? "" : list.get(0);
    }

    // ---------------------------------------------------------------- RecognitionListener

    private void recreateRecognizer() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
        }
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
    }

    @Override public void onReadyForSpeech(Bundle params) {
        Log.d(TAG, "readyForSpeech");
        sessionLive = true;
        if (feeder != null) {
            handler.removeCallbacks(feederCheck);
            handler.postDelayed(feederCheck, 3000);
        }
        handler.removeCallbacks(watchdog);
        busyRetries = 0;
        consecutiveErrors = 0;
        updateUi(getString(R.string.status_ready, Speech.language(this)));
    }

    @Override public void onBeginningOfSpeech() {
        arm(7000);   // speech started: expect partial results soon
        updateUi(getString(R.string.status_listening));
    }

    @Override public void onRmsChanged(float rmsdB) {}

    @Override public void onBufferReceived(byte[] buffer) {}

    @Override public void onEndOfSpeech() {
        markHandoff();
        updateUi(getString(R.string.status_recognizing));
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        String text = best(partialResults);
        Log.d(TAG, "partial: " + text.length() + " chars");
        handler.removeCallbacks(watchdog);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || text.trim().isEmpty()) return;
        if (!segmentStarted) decideSegmentFormat(ic);
        ic.setComposingText(format(text), 1);
    }

    @Override
    public void onResults(Bundle results) {
        Log.d(TAG, "results: " + best(results).length() + " chars");
        if (!commitFinal(results)) return;
        // Non-segmented mode. The engine may still deliver a second result for the same
        // utterance shortly after the first one; restarting immediately cancels it and
        // triggers a cancel/busy cascade during which speech is lost. Give it a grace period
        // (a late onResults simply reschedules this restart). Observed delay of the second
        // result on a Galaxy S23 is up to ~90 ms; a shorter grace means a shorter mic gap.
        if (listening) restartSoon(200);
        else updateUi(null);
    }

    /** Segmented mode (Android 13+): one finished phrase; the session keeps listening. */
    @Override
    public void onSegmentResults(Bundle segmentResults) {
        Log.d(TAG, "segment: " + best(segmentResults).length() + " chars");
        commitFinal(segmentResults);
    }

    /** Segmented mode: the whole session ended (long silence or stop). */
    @Override
    public void onEndOfSegmentedSession() {
        markHandoff();
        Log.d(TAG, "endOfSegmentedSession listening=" + listening);
        handler.removeCallbacks(watchdog);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        segmentStarted = false;
        if (listening) restartSoon(100);
        else updateUi(null);
    }

    /**
     * Commits a final (or segment) result at the cursor. Returns false if dictation was
     * stopped by the spoken "stop" command (Russian).
     */
    private boolean commitFinal(Bundle results) {
        markHandoff();
        consecutiveErrors = 0;
        handler.removeCallbacks(watchdog);
        String text = best(results);
        String beforeStop = Speech.isRussian(this) && Speech.voiceCommands(this) ? TextPost.stripStop(text) : null;
        boolean stopCommand = beforeStop != null;
        if (stopCommand) text = beforeStop;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (!text.trim().isEmpty()) {
                if (!segmentStarted) decideSegmentFormat(ic);
                ic.commitText(format(text), 1); // replaces the composing partial text
            } else if (stopCommand) {
                ic.commitText("", 1);           // drop the composing "stop" word
            } else {
                ic.finishComposingText();
            }
        }
        segmentStarted = false;
        if (stopCommand) {
            listening = false;
            stopFeeder();
            handler.removeCallbacksAndMessages(null);
            if (recognizer != null) recognizer.cancel();
            updateUi(getString(R.string.status_stopped_by_voice));
            return false;
        }
        return true;
    }

    @Override
    public void onError(int error) {
        Log.d(TAG, "error " + Speech.errorName(error) + " listening=" + listening);
        handler.removeCallbacks(watchdog);
        markHandoff();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        segmentStarted = false;
        if (!listening) { updateUi(null); return; }

        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                // Silence: release the old session explicitly, then keep listening.
                if (recognizer != null) recognizer.cancel();
                restartSoon(300);
                return;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                // The service may hold the previous session for several seconds after it ends
                // ("capacity is full"). Rebind and back off instead of giving up quickly.
                if (++busyRetries > 20) {
                    listening = false;
                    stopFeeder();
                    updateUi(getString(R.string.status_busy_giveup));
                    return;
                }
                recreateRecognizer();
                updateUi(getString(R.string.status_busy_wait));
                restartSoon(Math.min(500L * busyRetries, 2000L));
                return;
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                listening = false;
                stopFeeder();
                updateUi(getString(R.string.status_model_missing, Speech.language(this)));
                return;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                listening = false;
                stopFeeder();
                updateUi(getString(R.string.status_no_mic));
                return;
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
            case SpeechRecognizer.ERROR_CLIENT:
                recreateRecognizer();
                break;
            default:
                break;
        }
        if (++consecutiveErrors >= 5) {
            listening = false;
            stopFeeder();
            updateUi(getString(R.string.status_error_giveup, Speech.errorName(error)));
            return;
        }
        updateUi(getString(R.string.status_error_retry, Speech.errorName(error)));
        restartSoon(400);
    }

    @Override public void onEvent(int eventType, Bundle params) {}
}
