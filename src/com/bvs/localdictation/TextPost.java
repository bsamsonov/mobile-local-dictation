package com.bvs.localdictation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-processing of recognized Russian text (pure Java, no Android deps). The Cyrillic
 * literals below are functional data: command phrases and number words.
 * spoken newline commands -> line breaks, number words -> digits.
 */
final class TextPost {
    private TextPost() {}

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String NL = "(?<!\\p{L})";   // no letter before
    private static final String NR = "(?!\\p{L})";    // no letter after

    private static final Pattern PARAGRAPH = Pattern.compile(
            "[ \\t]*" + NL + "(?:новый абзац|с нового абзаца|new paragraph)" + NR + "[.,;:!?]?[ \\t]*", FLAGS);
    private static final Pattern NEWLINE = Pattern.compile(
            "[ \\t]*" + NL + "(?:новая строка|новую строку|с новой строки|перевод строки|энтер|new line|newline|enter)" + NR
                    + "[.,;:!?]?[ \\t]*", FLAGS);
    private static final Pattern AFTER_NEWLINE = Pattern.compile("\n(\\p{Ll})");

    // Spoken punctuation marks (Russian): the engine does not turn these words into characters.
    // The mark replaces the word together with the space and the engine's own mark around it.
    private static final String MARK_L = "[ \\t]*[,.;:]?[ \\t]*" + NL;
    private static final String MARK_R = NR + "[,.;:!?]?";
    private static final Pattern SEMICOLON = Pattern.compile(MARK_L + "точка с запятой" + MARK_R, FLAGS);
    private static final Pattern COMMA = Pattern.compile(MARK_L + "запятая" + MARK_R, FLAGS);
    /** Not "точка зрения" (point of view). */
    private static final Pattern PERIOD = Pattern.compile(MARK_L + "точка" + NR + "(?![ \\t]+зрения)[,.;:!?]?", FLAGS);
    private static final char SPOKEN = '\u0000';   // marks a period that came from the spoken word
    private static final Pattern AFTER_SPOKEN = Pattern.compile(SPOKEN + "([ \\t]*)(\\p{L})?");

    private static final Pattern STOP = Pattern.compile(
            "^(.*?)[\\s,;:—-]*" + NL + "стоп" + NR + "[\\s.!?…]*$", FLAGS | Pattern.DOTALL);

    /**
     * True if {@code next} (a partial result) starts a new phrase instead of revising {@code prev}.
     * The engine sometimes closes a phrase internally and starts the next one without sending a
     * final result; a revision keeps the beginning of the phrase, a new phrase does not. Compares
     * letters and digits only (case, spaces and punctuation ignored): a new phrase shares less
     * than half of the shorter text as a common prefix.
     */
    static boolean isNewPhrase(String prev, String next) {
        String a = letters(prev), b = letters(next);
        if (a.isEmpty() || b.isEmpty()) return false;
        int n = Math.min(a.length(), b.length()), common = 0;
        while (common < n && a.charAt(common) == b.charAt(common)) common++;
        return common * 2 < n;
    }

    /**
     * The text to commit for a phrase: the engine's final result, unless it is much shorter than the
     * last partial result shown for the same phrase. The engine sometimes returns a final result for
     * only a piece of what it showed (observed: 43 characters of partial text, final of 8), and
     * committing it would erase the rest. Less than 60% of the letters means a piece.
     */
    static String pickFinal(String partial, String fin) {
        String p = partial == null ? "" : partial, f = fin == null ? "" : fin;
        return letters(f).length() * 10 < letters(p).length() * 6 ? p : f;
    }

    /** True if the letters and digits of {@code text} (non-empty) occur in {@code committed}. */
    static boolean coveredBy(String committed, String text) {
        String t = letters(text);
        return !t.isEmpty() && letters(committed).contains(t);
    }

    /**
     * The part of {@code text} after its beginning {@code prefix}, compared by letters and digits
     * (case, spaces and punctuation ignored); "" if nothing follows, also if {@code text} is itself a
     * beginning of the prefix (all of it is already there). If {@code text} does not start
     * with the prefix (the engine revised those words): with {@code byWords}, the words after as many
     * words as the prefix has; otherwise null.
     */
    static String afterPrefix(String prefix, String text) { return afterPrefix(prefix, text, true); }

    static String afterPrefix(String prefix, String text, boolean byWords) {
        String p = letters(prefix), t = text == null ? "" : text;
        int i = 0, matched = 0;
        boolean mismatch = false;
        while (matched < p.length() && i < t.length()) {
            int cp = t.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                int lc = Character.toLowerCase(cp) == 'ё' ? 'е' : Character.toLowerCase(cp);
                if (p.codePointAt(matched) != lc) { mismatch = true; break; }
                matched += Character.charCount(lc);
            }
            i += Character.charCount(cp);
        }
        if (!mismatch && matched < p.length() && matched > 0) return "";   // text is a beginning of the prefix
        if (matched == p.length()) {
            String r = t.substring(i);
            // A sentence mark the fixed text already ends with is not typed again.
            String pt = prefix.trim();
            boolean endsWithMark = !pt.isEmpty() && ".!?…".indexOf(pt.charAt(pt.length() - 1)) >= 0;
            int j = 0;
            while (endsWithMark && j < r.length() && ".!?…".indexOf(r.charAt(j)) >= 0) j++;
            return blankText(r.substring(j)) ? "" : r.substring(j);
        }
        if (!byWords) return null;
        int n = words(prefix).length;
        String[] w = words(t);
        if (w.length <= n) return "";
        return " " + String.join(" ", java.util.Arrays.copyOfRange(w, n, w.length));
    }

    private static String[] words(String s) {
        String t = s == null ? "" : s.trim();
        return t.isEmpty() ? new String[0] : t.split("\\s+");
    }

    private static boolean blankText(String s) { return s.trim().isEmpty() && s.indexOf('\n') < 0; }

    /** Lowercase letters and digits of the text, nothing else. */
    static String letters(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        s.toLowerCase(Locale.ROOT).replace('ё', 'е').codePoints()
                .filter(Character::isLetterOrDigit).forEach(b::appendCodePoint);
        return b.toString();
    }

    /**
     * If the utterance ends with the word "stop" (Russian), returns the text before it (possibly
     * empty); otherwise null. Only the last word counts, so "stop" mid-sentence stays text.
     */
    static String stripStop(String s) {
        Matcher m = STOP.matcher(s == null ? "" : s);
        return m.matches() ? m.group(1) : null;
    }

    /** Spoken "new line" / "enter" / "new paragraph" (Russian and English) -> "\n" / "\n\n"; capitalises the next line. */
    static String voiceCommands(String s) {
        s = spokenMarks(s);
        s = PARAGRAPH.matcher(s).replaceAll("\n\n");
        s = NEWLINE.matcher(s).replaceAll("\n");
        Matcher m = AFTER_NEWLINE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, "\n" + m.group(1).toUpperCase(Locale.ROOT));
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * The spoken words "запятая", "точка", "точка с запятой" (comma, period, semicolon) -> the mark,
     * attached to the word before it; the word after a period is capitalised.
     */
    static String spokenMarks(String s) {
        s = SEMICOLON.matcher(s).replaceAll(";");
        s = COMMA.matcher(s).replaceAll(",");
        s = PERIOD.matcher(s).replaceAll("." + SPOKEN);
        if (s.indexOf(SPOKEN) < 0) return s;
        Matcher m = AFTER_SPOKEN.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String next = m.group(2) == null ? "" : m.group(2).toUpperCase(Locale.ROOT);
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + next));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Joins a phrase to the text before it when the phrase starts with a mark from a spoken word:
     * if that text already ends with a mark (the engine closes every phrase with a period), the
     * leading mark is dropped. Returns the phrase to format as usual, or null if it was only a mark
     * that is not needed.
     */
    static String dropLeadingMark(String phrase, boolean afterMark) {
        if (phrase.isEmpty() || ",.;".indexOf(phrase.charAt(0)) < 0 || !afterMark) return phrase;
        String rest = phrase.substring(1).trim();
        return rest.isEmpty() ? null : rest;
    }

    // ------------------------------------------------------------------ numbers

    private static final Map<String, Long> NUM = new HashMap<>();
    static {
        String[][] t = {
                {"0", "ноль", "нуль"}, {"1", "один", "одна", "одно", "одну"}, {"2", "два", "две"},
                {"3", "три"}, {"4", "четыре"}, {"5", "пять"}, {"6", "шесть"}, {"7", "семь"},
                {"8", "восемь"}, {"9", "девять"}, {"10", "десять"}, {"11", "одиннадцать"},
                {"12", "двенадцать"}, {"13", "тринадцать"}, {"14", "четырнадцать"},
                {"15", "пятнадцать"}, {"16", "шестнадцать"}, {"17", "семнадцать"},
                {"18", "восемнадцать"}, {"19", "девятнадцать"}, {"20", "двадцать"},
                {"30", "тридцать"}, {"40", "сорок"}, {"50", "пятьдесят"}, {"60", "шестьдесят"},
                {"70", "семьдесят"}, {"80", "восемьдесят"}, {"90", "девяносто"}, {"100", "сто"},
                {"200", "двести"}, {"300", "триста"}, {"400", "четыреста"}, {"500", "пятьсот"},
                {"600", "шестьсот"}, {"700", "семьсот"}, {"800", "восемьсот"}, {"900", "девятьсот"},
                {"1000", "тысяча", "тысячи", "тысяч", "тысячу"},
                {"1000000", "миллион", "миллиона", "миллионов"},
                {"1000000000", "миллиард", "миллиарда", "миллиардов"},
        };
        for (String[] row : t) for (int i = 1; i < row.length; i++) NUM.put(row[i], Long.parseLong(row[0]));
    }

    private static final Pattern WORD = Pattern.compile("\\p{L}+");

    private static Long value(String w) { return NUM.get(w.toLowerCase(Locale.ROOT).replace('ё', 'е')); }

    /**
     * Runs of number words separated by single spaces -> digits. A lone word 0..9
     * ("one", "five") stays a word, so ordinary prose is not mangled.
     */
    static String numbersToDigits(String s) {
        Matcher m = WORD.matcher(s);
        List<int[]> toks = new ArrayList<>();
        while (m.find()) toks.add(new int[]{m.start(), m.end()});
        StringBuilder out = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < toks.size(); ) {
            int[] a = toks.get(i);
            if (value(s.substring(a[0], a[1])) == null) { i++; continue; }
            int j = i;
            while (j + 1 < toks.size()
                    && value(s.substring(toks.get(j + 1)[0], toks.get(j + 1)[1])) != null
                    && s.substring(toks.get(j)[1], toks.get(j + 1)[0]).equals(" ")) j++;
            List<String> words = new ArrayList<>();
            for (int k = i; k <= j; k++) words.add(s.substring(toks.get(k)[0], toks.get(k)[1]));
            out.append(s, pos, a[0]).append(formatRun(words));
            pos = toks.get(j)[1];
            i = j + 1;
        }
        return out.append(s.substring(pos)).toString();
    }

    /** Split a run of number words into grammatically valid numbers and render each one. */
    private static String formatRun(List<String> words) {
        List<Long> values = new ArrayList<>();
        List<List<String>> origs = new ArrayList<>();
        long total = 0, cur = 0, lastMult = Long.MAX_VALUE;
        int count = 0;
        List<String> orig = new ArrayList<>();
        for (String w : words) {
            long v = value(w);
            if (count > 0 && !canAppend(total, cur, lastMult, v)) {
                values.add(total + cur); origs.add(orig);
                total = 0; cur = 0; lastMult = Long.MAX_VALUE; count = 0; orig = new ArrayList<>();
            }
            if (v >= 1000) {
                total += (cur == 0 ? 1 : cur) * v;
                cur = 0;
                lastMult = v;
            } else {
                cur += v;
            }
            count++;
            orig.add(w);
        }
        if (count > 0) { values.add(total + cur); origs.add(orig); }
        // Several numbers in a row (e.g. a phone number "five five five") -> always digits.
        boolean forceDigits = values.size() > 1;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            List<String> o = origs.get(i);
            parts.add(!forceDigits && o.size() == 1 && values.get(i) < 10 ? o.get(0) : Long.toString(values.get(i)));
        }
        return String.join(" ", parts);
    }

    private static boolean canAppend(long total, long cur, long lastMult, long v) {
        if (v == 0 || (total == 0 && cur == 0)) return false;        // zero only standalone
        if (v >= 1000) return v < lastMult;
        if (v >= 100) return cur == 0;                                // hundreds start a group
        long r = cur % 100;
        if (v >= 10) return r == 0;                                   // tens / teens
        return cur % 10 == 0 && !(r >= 10 && r <= 19);                // units
    }
}
