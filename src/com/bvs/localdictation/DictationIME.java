package com.bvs.localdictation;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
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
import android.text.InputType;
import android.widget.Button;
import android.widget.ImageButton;
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
public class DictationIME extends InputMethodService
        implements RecognitionListener, MicService.StopListener, PhraseTracker.Sink {

    private static final String TAG = "LocalDictation";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    /** The recognizer already ran a session (see beginSession). */
    private boolean recognizerUsed;
    /** Listener of the current recognizer; events for an older one are dropped. */
    private Session session;
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
    /** Decides from the engine's events what is the phrase in progress and when it is finished. */
    private final PhraseTracker phrases = new PhraseTracker(this);
    /** Every 3 s of a session: is the engine still reading our audio? */
    private final Runnable feederCheck = this::checkFeeder;

    private void checkFeeder() {
        if (feeder == null || !listening) return;
        if (feeder.isBlocked()) {
            Log.w(TAG, "engine does not read EXTRA_AUDIO_SOURCE; falling back to its own mic");
            feederUnsupported = true;
            stopFeeder();
            recreateRecognizer();
            restartSoon(200);
        } else if (feeder.isBroken() && sessionLive) {
            // The engine closed the audio pipe but reported nothing: the session is dead.
            Log.w(TAG, "engine closed the audio pipe without ending the session; restarting");
            markHandoff();
            if (!phrases.sessionFailed(SystemClock.elapsedRealtime())) return;   // spoken "stop"
            recreateRecognizer();
            restartSoon(200);
        } else {
            handler.postDelayed(feederCheck, 3000);
        }
    }

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
    /** The text before the phrase ends with a punctuation mark. */
    private boolean segmentAfterMark;

    // Screen locked during dictation: recognized text is held here instead of being typed into
    // whatever window has input now (the lock screen), and inserted after unlock.
    private boolean locked;
    private String lockedPackage;
    /** Text before the cursor when the screen locked, for spacing/capitalisation of held text. */
    private String lockedBefore = "";
    private final StringBuilder held = new StringBuilder();
    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Log.d(TAG, "user present, locked=" + locked);
            handler.postDelayed(() -> resumeAfterLock(getCurrentInputEditorInfo()), 700);
        }
    };

    /** Phone microphone or Bluetooth headset (SCO / LE Audio) for the dictation audio. */
    private AudioRoute route;
    /** Incremented on every route setup, so a late "link is up" of an older setup is ignored. */
    private int routeGen;

    private TextView status;
    private Button mic;
    private Button source;

    // ---------------------------------------------------------------- UI

    @Override
    public void onCreate() {
        super.onCreate();
        // The service is created when the user switches to this keyboard from another one:
        // that is a request to dictate, so a remembered stop no longer applies.
        // Screen lock/unlock and moving between fields keep the service, so a stop is kept there.
        Speech.setStoppedByUser(this, false);
        route = new AudioRoute(this, this::onHeadsetsChanged);
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
    }

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
        ImageButton gear = new ImageButton(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setContentDescription(getString(R.string.key_settings));
        gear.setOnClickListener(v -> openSettings());
        row2.addView(gear, weight(1));
        source = key("", v -> toggleSource());
        source.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        source.setPadding(0, 0, 0, 0);
        row2.addView(source, weight(1));
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
        boolean headset = Speech.headsetMic(this);
        source.setText(headset ? "🎧" : "📱");
        source.setContentDescription(getString(headset ? R.string.key_source_headset : R.string.key_source_phone));
        mic.setText(listening ? R.string.mic_stop : R.string.mic_start);
        mic.setTextColor(listening ? Color.rgb(229, 57, 53) : Color.BLACK);
        if (msg != null) status.setText(msg);
        else if (!listening) status.setText(getString(R.string.status_idle, Speech.language(this)));
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        Log.d(TAG, "onStartInput restarting=" + restarting + " pkg=" + info.packageName + " locked=" + locked);
        resumeAfterLock(info);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        Log.d(TAG, "onStartInputView restarting=" + restarting + " pkg=" + info.packageName);
        resumeAfterLock(info);
        consecutiveErrors = 0;
        handler.removeCallbacks(autoStart);
        // A stop by the user is remembered (screen lock/unlock, other fields): no auto-start until Dictate is tapped.
        if (!restarting && !listening && Speech.autoStart(this) && !Speech.stoppedByUser(this)) {
            handler.postDelayed(autoStart, 250);
        }
        else updateUi(null);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        Log.d(TAG, "onFinishInputView listening=" + listening + " screenLocked=" + screenLocked());
        if (listening && !locked && screenLocked()) enterLocked();
        else if (!locked) haltListening();
        super.onFinishInputView(finishingInput);
    }

    private boolean screenLocked() {
        PowerManager pm = getSystemService(PowerManager.class);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        return !pm.isInteractive() || km.isKeyguardLocked();
    }

    /** Screen locked while dictating: keep listening, hold the text until unlock. */
    private void enterLocked() {
        locked = true;
        EditorInfo ei = getCurrentInputEditorInfo();
        lockedPackage = ei == null ? null : ei.packageName;
        held.setLength(0);
        InputConnection ic = getCurrentInputConnection();
        lockedBefore = "";
        if (ic != null) {
            // Drop the composing partial: the final result of this phrase goes to the held text.
            ic.setComposingText("", 1);
            ic.finishComposingText();
            CharSequence b = ic.getTextBeforeCursor(64, 0);
            lockedBefore = b == null ? "" : b.toString();
        }
        segmentStarted = false;
        Log.d(TAG, "screen locked: dictation continues, pkg=" + lockedPackage);
    }

    /** Input is back after a lock: insert held text into the same app, or give it to the clipboard. */
    private void resumeAfterLock(EditorInfo info) {
        if (!locked || screenLocked()) return;
        if (info == null || info.inputType == InputType.TYPE_NULL) return;   // no editor yet
        InputConnection ic = getCurrentInputConnection();
        boolean same = ic != null && info.packageName != null && info.packageName.equals(lockedPackage);
        locked = false;
        String text = held.toString();
        held.setLength(0);
        Log.d(TAG, "unlocked: same app=" + same + ", held " + text.length() + " chars");
        if (same) {
            if (!text.isEmpty()) ic.commitText(text, 1);
            segmentStarted = false;
            updateUi(listening ? readyStatus() : null);
            return;
        }
        haltListening();
        if (!text.isEmpty()) {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
            updateUi(getString(R.string.status_bg_clipboard));
        }
    }

    /** Stop tapped in the notification. */
    @Override
    public void onStopFromNotification() {
        if (!listening) return;
        listening = false;
        Speech.setStoppedByUser(this, true);
        stopFeeder();
        route.release();
        handler.removeCallbacksAndMessages(null);
        // stopListening() still delivers onResults for what was already said.
        if (recognizer != null) recognizer.stopListening();
        updateUi(getString(R.string.status_stopped));
    }

    /** Where recognized text goes now: null while the screen is locked (text is held instead). */
    private InputConnection target() {
        return locked ? null : getCurrentInputConnection();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(unlockReceiver);
        locked = false;
        haltListening();
        route.release();
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
            Speech.setStoppedByUser(this, true);
            stopFeeder();
            handler.removeCallbacksAndMessages(null);
            // stopListening() still delivers onResults for what was already said.
            if (recognizer != null) recognizer.stopListening();
            MicService.stop(this);
            route.release();
            updateUi(getString(R.string.status_stopped));
        } else {
            consecutiveErrors = 0;
            Speech.setStoppedByUser(this, false);
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
        if (recognizer == null) recreateRecognizer();
        listening = true;
        MicService.start(this, this);
        busyRetries = 0;
        openMic();
    }

    /** Sets up the audio route (phone or headset), then starts own capture and the first session. */
    private void openMic() {
        updateUi(getString(R.string.status_connecting));
        final int gen = ++routeGen;
        route.apply(Speech.headsetMic(this), () -> {
            if (!listening || gen != routeGen) return;
            if (feeder == null && !feederUnsupported && Build.VERSION.SDK_INT >= 33 && Speech.continuousAudio(this)) {
                feeder = new AudioFeeder();
                if (!startCapture()) feeder = null;
            }
            replayFrom = SystemClock.elapsedRealtime();
            beginSession();
        });
    }

    /**
     * Starts the microphone capture. Development hook: if test.pcm (raw 16 kHz 16-bit mono) is in
     * the app's external files directory, it is played to the engine instead of the microphone and
     * dictation stops at its end, so the keyboard can be tested end to end without speaking.
     */
    private boolean startCapture() {
        java.io.File test = new java.io.File(getExternalFilesDir(null), "test.pcm");
        if (!test.isFile()) return feeder.start(route.inputDevice());
        try {
            Log.w(TAG, "test audio instead of the microphone: " + test);
            return feeder.startFile(new java.io.FileInputStream(test), 3000,
                    () -> handler.post(() -> { if (listening) toggle(); }));
        } catch (IOException e) {
            return feeder.start(route.inputDevice());
        }
    }

    /** Microphone key: phone <-> headset. A running dictation goes on from the new source. */
    private void toggleSource() {
        boolean headset = !Speech.headsetMic(this);
        Speech.setHeadsetMic(this, headset);
        Log.d(TAG, "source: " + (headset ? "headset" : "phone"));
        if (listening) reroute();
        else updateUi(headset && route.findHeadset() == null ? getString(R.string.status_no_headset_idle) : null);
    }

    /** A headset connected or disconnected while dictating. */
    private void onHeadsetsChanged() {
        if (listening && route.needsReroute(Speech.headsetMic(this))) {
            Log.d(TAG, "headsets changed: rerouting");
            reroute();
        }
    }

    /** Restarts capture and the session on the current route, keeping dictation on. */
    private void reroute() {
        handler.removeCallbacksAndMessages(null);
        if (!phrases.sessionFailed(SystemClock.elapsedRealtime())) return;   // spoken "stop"
        InputConnection ic = target();
        if (ic != null) ic.finishComposingText();
        segmentStarted = false;
        stopFeeder();
        recreateRecognizer();
        openMic();
    }

    private void releaseMic() {
        MicService.stop(this);
        stopFeeder();
        route.release();
    }

    /** Ready status, with the headset in use (or its absence) when the headset source is chosen. */
    private String readyStatus() {
        String s = getString(R.string.status_ready, Speech.language(this));
        if (!Speech.headsetMic(this)) return s;
        if (route.headset() == null) return s + " · " + getString(R.string.status_no_headset);
        String n = route.headsetName();
        return s + " · 🎧 " + (n.isEmpty() ? "Bluetooth" : n);
    }

    private void beginSession() {
        if (!listening || recognizer == null) return;
        if (recognizerUsed) {
            // One recognizer per session: the engine may report an error for a session up to a second
            // after its result, and that late event must not end the session started in between.
            // The new recognizer needs a moment: started at once it fails with SERVER_DISCONNECTED.
            recreateRecognizer();
            handler.postDelayed(this::beginSession, 100);
            return;
        }
        segmentStarted = false;
        Log.d(TAG, "startListening " + Speech.language(this) + " feeder=" + (feeder != null));
        sessionLive = false;
        ParcelFileDescriptor pfd = null;
        if (feeder != null) {
            try {
                pfd = feeder.newSession(replayFrom);
            } catch (IOException e) {
                Log.w(TAG, "pipe failed", e);
                stopFeeder();
            }
        }
        // With our own audio the engine keeps one session across pauses (see PhraseTracker); with its
        // own microphone it ends the session at every pause unless the session is segmented.
        boolean continuous = pfd != null;
        phrases.sessionStart(continuous);
        Intent intent = Speech.recognizeIntent(this, !continuous);
        if (continuous) {
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioFeeder.RATE);
        }
        recognizerUsed = true;
        recognizer.startListening(intent);
        arm(4000);   // expect onReadyForSpeech (mic opened) soon
        updateUi(getString(R.string.status_connecting));
    }

    /** Hard stop (keyboard hidden): keep whatever partial text is already shown. */
    private void haltListening() {
        listening = false;
        releaseMic();
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) recognizer.cancel();
        InputConnection ic = target();
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
        // Do not close the pipe here: EOF right after end of speech makes the engine fail with
        // SODA_ERROR (ERROR_CLIENT) instead of delivering results. newSession() closes it later.
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
        String b;
        if (ic == null) {
            b = lockedBefore + held;
        } else {
            CharSequence before = ic.getTextBeforeCursor(64, 0);
            b = before == null ? "" : before.toString();
        }
        String trimmed = b.replaceAll("\\s+$", "");
        boolean endsWithSpace = !b.isEmpty() && Character.isWhitespace(b.charAt(b.length() - 1));
        segmentPrefix = (b.isEmpty() || endsWithSpace) ? "" : " ";
        segmentAfterMark = !trimmed.isEmpty() && ".,;:!?…".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0;
        segmentCapitalize = trimmed.isEmpty() || ".!?…".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0
                || b.replaceAll("[ \\t]+$", "").endsWith("\n");
        segmentStarted = true;
    }

    private String format(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return text != null && text.indexOf('\n') >= 0 ? "\n" : "";   // the engine's own "new line"
        boolean ru = Speech.isRussian(this);
        if (Speech.voiceCommands(this)) t = TextPost.voiceCommands(t);
        if (ru && Speech.digits(this)) t = TextPost.numbersToDigits(t);
        if (t.startsWith("\n")) return t;   // starts with a line break: no leading space, already capitalised
        if (",.;".indexOf(t.charAt(0)) >= 0) {
            // Starts with a mark from a spoken word ("запятая", "точка"): it goes right after the text.
            t = TextPost.dropLeadingMark(t, segmentAfterMark);
            if (t == null) return "";
            if (",.;".indexOf(t.charAt(0)) >= 0) return t;
        }
        if (segmentCapitalize) {
            t = t.substring(0, 1).toUpperCase() + t.substring(1);
        } else if (t.length() > 1 && Character.isUpperCase(t.charAt(0)) && Character.isLowerCase(t.charAt(1))) {
            // The engine capitalises every utterance after a pause; mid-sentence we lowercase it
            // (all-caps acronyms are left alone since their second letter is uppercase).
            t = t.substring(0, 1).toLowerCase() + t.substring(1);
        }
        return segmentPrefix + t;
    }

    /** Lengths of all hypotheses, e.g. "[8, 7]" (no text, see log privacy). */
    private static String hypLengths(Bundle b) {
        ArrayList<String> list = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (String h : list) sb.append(sb.length() > 1 ? ", " : "").append(h == null ? 0 : h.length());
        return sb.append(']').toString();
    }

    private static ArrayList<String> hypotheses(Bundle b) {
        ArrayList<String> list = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return list == null ? new ArrayList<>() : list;
    }

    private static String best(Bundle b) {
        ArrayList<String> list = hypotheses(b);
        return list.isEmpty() || list.get(0) == null ? "" : list.get(0);
    }

    // ---------------------------------------------------------------- RecognitionListener

    private void recreateRecognizer() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
        }
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        recognizerUsed = false;
        session = new Session();
        recognizer.setRecognitionListener(session);
    }

    /** Passes the events of one recognizer on, until that recognizer is replaced. */
    private final class Session implements RecognitionListener {
        private boolean stale(String event) {
            if (session == this) return false;
            Log.d(TAG, "late " + event + " of a replaced recognizer: ignored");
            return true;
        }
        @Override public void onReadyForSpeech(Bundle b) { if (!stale("ready")) DictationIME.this.onReadyForSpeech(b); }
        @Override public void onBeginningOfSpeech() { if (session == this) DictationIME.this.onBeginningOfSpeech(); }
        @Override public void onRmsChanged(float v) {}
        @Override public void onBufferReceived(byte[] b) {}
        @Override public void onEndOfSpeech() { if (session == this) DictationIME.this.onEndOfSpeech(); }
        @Override public void onPartialResults(Bundle b) { if (!stale("partial")) DictationIME.this.onPartialResults(b); }
        @Override public void onResults(Bundle b) { if (!stale("results")) DictationIME.this.onResults(b); }
        @Override public void onSegmentResults(Bundle b) { if (!stale("segment")) DictationIME.this.onSegmentResults(b); }
        @Override public void onEndOfSegmentedSession() { if (!stale("end")) DictationIME.this.onEndOfSegmentedSession(); }
        @Override public void onError(int e) { if (!stale("error " + Speech.errorName(e))) DictationIME.this.onError(e); }
        @Override public void onEvent(int t, Bundle b) {}
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
        if (listening) updateUi(readyStatus());
    }

    @Override public void onBeginningOfSpeech() {
        // The engine's own microphone: speech started, expect partial results soon. With our own
        // audio the engine reports "speech" right after every pause, also in silence.
        if (feeder == null) arm(7000);
        // Late events of a stopped session must not show "Listening" next to the Dictate button.
        if (listening) updateUi(getString(R.string.status_listening));
    }

    @Override public void onRmsChanged(float rmsdB) {}

    @Override public void onBufferReceived(byte[] buffer) {}

    @Override public void onEndOfSpeech() {
        phrases.endOfSpeech(SystemClock.elapsedRealtime());
        markHandoff();
        if (listening) updateUi(getString(R.string.status_recognizing));
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> hyps = hypotheses(partialResults);
        Log.d(TAG, "partial: " + hypLengths(partialResults) + " chars");
        handler.removeCallbacks(watchdog);
        phrases.partial(hyps, SystemClock.elapsedRealtime());
    }

    /** PhraseTracker.Sink: the phrase in progress, shown as composing (underlined) text. */
    @Override
    public void compose(String text) {
        InputConnection ic = target();
        if (ic == null) return;   // locked: the finished phrase goes to the held text
        if (!segmentStarted) decideSegmentFormat(ic);
        ic.setComposingText(format(text), 1);
    }

    @Override
    public void onResults(Bundle results) {
        Log.d(TAG, "results: " + hypLengths(results) + " chars");
        markHandoff();
        if (!phrases.result(best(results), true, SystemClock.elapsedRealtime())) return;
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
        Log.d(TAG, "segment: " + hypLengths(segmentResults) + " chars");
        phrases.result(best(segmentResults), false, SystemClock.elapsedRealtime());
    }

    /** Segmented mode: the whole session ended (long silence or stop). */
    @Override
    public void onEndOfSegmentedSession() {
        markHandoff();
        Log.d(TAG, "endOfSegmentedSession listening=" + listening);
        handler.removeCallbacks(watchdog);
        InputConnection ic = target();
        if (ic != null) ic.finishComposingText();
        segmentStarted = false;
        if (listening) restartSoon(100);
        else updateUi(null);
    }

    /**
     * PhraseTracker.Sink: types a finished phrase at the cursor in place of the composing text.
     * Returns false if dictation was stopped by the spoken "stop" command (Russian).
     */
    @Override
    public boolean commit(String text) {
        // No markHandoff() here: phrases are also committed in the middle of a live session, and
        // moving the replay point there would make the next session re-recognize typed audio.
        consecutiveErrors = 0;
        handler.removeCallbacks(watchdog);
        String beforeStop = Speech.isRussian(this) && Speech.voiceCommands(this) ? TextPost.stripStop(text) : null;
        boolean stopCommand = beforeStop != null;
        if (stopCommand) text = beforeStop;
        InputConnection ic = target();
        if (locked) {
            if (!PhraseTracker.blank(text)) {
                if (!segmentStarted) decideSegmentFormat(null);
                held.append(format(text));
            }
        } else if (ic != null) {
            if (!PhraseTracker.blank(text)) {
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
            Speech.setStoppedByUser(this, true);
            releaseMic();
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
        // The engine may fail instead of delivering the final result: the phrase in progress becomes
        // final as shown, so voice commands and digits still apply (also after Stop).
        boolean hadPhrase = phrases.hasPending();
        if (!phrases.sessionFailed(SystemClock.elapsedRealtime())) return;   // spoken "stop"
        if (listening && hadPhrase) {
            if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) recreateRecognizer();
            restartSoon(200);
            return;
        }
        InputConnection ic = target();
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
                    releaseMic();
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
                releaseMic();
                updateUi(getString(R.string.status_model_missing, Speech.language(this)));
                return;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                listening = false;
                releaseMic();
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
            releaseMic();
            updateUi(getString(R.string.status_error_giveup, Speech.errorName(error)));
            return;
        }
        updateUi(getString(R.string.status_error_retry, Speech.errorName(error)));
        restartSoon(400);
    }

    @Override public void onEvent(int eventType, Bundle params) {}
}
