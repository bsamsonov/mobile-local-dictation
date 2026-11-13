package com.bvs.localdictation;

import java.util.List;

/**
 * Turns the engine's stream of recognition events into text edits: what to show as the phrase in
 * progress and when a phrase is finished. Pure Java (no Android), so recorded event traces are
 * replayed against it in tests.
 *
 * <p>Continuous session (own audio capture, not segmented): the engine keeps one session across
 * pauses. A partial result with one hypothesis is the text still in progress; it may hold several
 * sentences and is revised as a whole. A partial result with two or more hypotheses (formatted and
 * raw) is a finished phrase: the engine drops it from the following partial results, which carry
 * only what comes after it.
 *
 * <p>Other sessions (the engine's own microphone): a phrase ends with a final or segment result, or
 * with an error; the final result may cover only a piece of what was shown.
 */
final class PhraseTracker {

    interface Sink {
        /** Shows the phrase in progress, replacing what was shown for it before. */
        void compose(String text);

        /**
         * Types a finished phrase in place of the phrase in progress ("" keeps what is shown).
         * Returns false if this stopped the dictation (a spoken command).
         */
        boolean commit(String text);
    }

    /**
     * A partial result that does not continue the previous one is a new phrase (the engine closed the
     * previous one without saying so) if it comes after the engine reported the end of speech, or
     * after at least this pause. Otherwise it is a revision: those arrive within ~150 ms, new phrases
     * were observed after 0.5-2.9 s.
     */
    static final long NEW_PHRASE_GAP_MS = 600;
    /** A final result this soon after the same text was typed from a partial result is a repeat. */
    static final long REPEAT_MS = 1500;

    private final Sink sink;
    private boolean continuous;
    /** The session delivered its final result or failed: its late partial results are ignored. */
    private boolean ended = true;
    private String pending = "";
    private long pendingAt;
    /** When the words of the pending text last changed. */
    private long pendingChangedAt;
    private long endOfSpeechAt = -1;
    /** Text typed from a partial result (not from a final one), and when. */
    private String typed = "";
    private long typedAt;

    PhraseTracker(Sink sink) { this.sink = sink; }

    /** A new recognition session starts; nothing of the previous one is pending any more. */
    void sessionStart(boolean continuous) {
        this.continuous = continuous;
        ended = false;
        pending = "";
        endOfSpeechAt = -1;
    }

    /** The engine heard a pause. */
    void endOfSpeech(long now) { endOfSpeechAt = now; }

    /** True if a phrase in progress is shown and not typed yet. */
    boolean hasPending() { return !blank(pending); }

    /** A partial result. Returns false if a commit stopped the dictation. */
    boolean partial(List<String> hypotheses, long now) {
        String text = hypotheses == null || hypotheses.isEmpty() || hypotheses.get(0) == null ? "" : hypotheses.get(0);
        if (ended || blank(text)) return true;
        if (continuous && hypotheses.size() >= 2) {
            pending = "";
            typed = text;
            typedAt = now;
            return sink.commit(text);
        }
        if (!blank(pending) && TextPost.isNewPhrase(pending, text)
                && (now - pendingAt >= NEW_PHRASE_GAP_MS || endOfSpeechAt >= pendingChangedAt)) {
            if (!flush(now)) return false;
        }
        if (!TextPost.letters(text).equals(TextPost.letters(pending))) pendingChangedAt = now;
        pending = text;
        pendingAt = now;
        sink.compose(text);
        return true;
    }

    /**
     * A final result ({@code endsSession}) or a segment result. Returns false if the commit stopped
     * the dictation.
     */
    boolean result(String text, boolean endsSession, long now) {
        String fin = text == null ? "" : text;
        String shown = pending;
        pending = "";
        if (endsSession) ended = true;
        if (blank(shown) && now - typedAt < REPEAT_MS && TextPost.coveredBy(typed, fin)) return true;
        if (continuous) {
            // Finished phrases were already typed; what is left is the text in progress.
            if (blank(fin)) fin = shown;
        } else {
            String chosen = TextPost.pickFinal(shown, fin);
            if (!chosen.equals(fin)) {
                // The final covers only a piece of what was shown; typing it would erase the rest.
                fin = chosen;
                typed = chosen;
                typedAt = now;
            }
        }
        return sink.commit(fin);
    }

    /**
     * The session failed or is being replaced: the phrase in progress becomes final as shown.
     * Returns false if the commit stopped the dictation.
     */
    boolean sessionFailed(long now) {
        ended = true;
        return flush(now);
    }

    private boolean flush(long now) {
        if (blank(pending)) return true;
        String p = pending;
        pending = "";
        typed = p;
        typedAt = now;
        return sink.commit(p);
    }

    /** Nothing to type: empty or spaces only. A lone line break is text (the engine's "new line"). */
    static boolean blank(String s) {
        return s == null || (s.trim().isEmpty() && s.indexOf('\n') < 0);
    }
}
