package com.bvs.localdictation;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

/** Shared settings and the recognition intent used by both the IME and the settings screen. */
final class Speech {
    private Speech() {}

    /** Used until the user picks a language; never written to the preferences by the app itself. */
    static final String DEFAULT_LANGUAGE = "en-US";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** The language the user picked (kept across updates and rebuilds), else {@link #DEFAULT_LANGUAGE}. */
    static String language(Context c) {
        String v = prefs(c).getString("language", null);
        return v == null || v.trim().isEmpty() ? DEFAULT_LANGUAGE : v.trim();
    }

    static void setLanguage(Context c, String v) { prefs(c).edit().putString("language", v).apply(); }

    /** The user stopped dictation (key or voice "stop"): no auto-start until they tap Dictate again. */
    static boolean stoppedByUser(Context c) { return prefs(c).getBoolean("stoppedByUser", false); }

    static void setStoppedByUser(Context c, boolean v) { prefs(c).edit().putBoolean("stoppedByUser", v).apply(); }

    /** Start listening as soon as the voice keyboard is shown. */
    static boolean autoStart(Context c) { return prefs(c).getBoolean("autoStart", true); }

    static void setAutoStart(Context c, boolean v) { prefs(c).edit().putBoolean("autoStart", v).apply(); }

    /** Ask the recognizer for punctuation/capitalisation (Android 13+, if the engine supports it). */
    static boolean formatting(Context c) { return prefs(c).getBoolean("formatting", true); }

    static void setFormatting(Context c, boolean v) { prefs(c).edit().putBoolean("formatting", v).apply(); }

    /** TextPost rules (voice commands, digits) are Russian-specific. */
    static boolean isRussian(Context c) { return language(c).toLowerCase(java.util.Locale.ROOT).startsWith("ru"); }

    /** Spoken newline / paragraph / stop commands (Russian). */
    static boolean voiceCommands(Context c) { return prefs(c).getBoolean("voiceCommands", true); }

    static void setVoiceCommands(Context c, boolean v) { prefs(c).edit().putBoolean("voiceCommands", v).apply(); }

    /** Number words -> digits. */
    static boolean digits(Context c) { return prefs(c).getBoolean("digits", true); }

    static void setDigits(Context c, boolean v) { prefs(c).edit().putBoolean("digits", v).apply(); }

    /** Own continuous mic capture fed to the engine (Android 13+), avoids losing words between phrases. */
    static boolean continuousAudio(Context c) { return prefs(c).getBoolean("continuousAudio", true); }

    static void setContinuousAudio(Context c, boolean v) { prefs(c).edit().putBoolean("continuousAudio", v).apply(); }

    static Intent recognizeIntent(Context c) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language(c));
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.getPackageName());
        if (Build.VERSION.SDK_INT >= 33) {
            // Segmented session: one long session that returns each phrase via onSegmentResults
            // instead of ending (and having to be restarted) at every pause. Ends after 30 s of
            // silence. Engines that don't support it ignore these extras.
            i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 30_000L);
        }
        if (Build.VERSION.SDK_INT >= 33 && formatting(c)) {
            i.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
        }
        return i;
    }

    static String errorName(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "NETWORK_TIMEOUT";
            case SpeechRecognizer.ERROR_NETWORK: return "NETWORK";
            case SpeechRecognizer.ERROR_AUDIO: return "AUDIO";
            case SpeechRecognizer.ERROR_SERVER: return "SERVER";
            case SpeechRecognizer.ERROR_CLIENT: return "CLIENT";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "SPEECH_TIMEOUT";
            case SpeechRecognizer.ERROR_NO_MATCH: return "NO_MATCH";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "RECOGNIZER_BUSY";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "INSUFFICIENT_PERMISSIONS";
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS: return "TOO_MANY_REQUESTS";
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED: return "SERVER_DISCONNECTED";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED: return "LANGUAGE_NOT_SUPPORTED";
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE: return "LANGUAGE_UNAVAILABLE";
            case SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT: return "CANNOT_CHECK_SUPPORT";
            case SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS: return "CANNOT_LISTEN_TO_DOWNLOAD_EVENTS";
            default: return "ERROR_" + e;
        }
    }
}
