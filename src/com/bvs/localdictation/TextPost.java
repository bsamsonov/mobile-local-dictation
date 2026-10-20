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
            "[ \\t]*" + NL + "(?:новый абзац|с нового абзаца)" + NR + "[.,;:!?]?[ \\t]*", FLAGS);
    private static final Pattern NEWLINE = Pattern.compile(
            "[ \\t]*" + NL + "(?:новая строка|новую строку|с новой строки|перевод строки)" + NR
                    + "[.,;:!?]?[ \\t]*", FLAGS);
    private static final Pattern AFTER_NEWLINE = Pattern.compile("\n(\\p{Ll})");

    private static final Pattern STOP = Pattern.compile(
            "^(.*?)[\\s,;:—-]*" + NL + "стоп" + NR + "[\\s.!?…]*$", FLAGS | Pattern.DOTALL);

    /**
     * If the utterance ends with the word "stop" (Russian), returns the text before it (possibly
     * empty); otherwise null. Only the last word counts, so "stop" mid-sentence stays text.
     */
    static String stripStop(String s) {
        Matcher m = STOP.matcher(s == null ? "" : s);
        return m.matches() ? m.group(1) : null;
    }

    /** Spoken "new line" / "new paragraph" (Russian) -> "\n" / "\n\n"; capitalises the next line. */
    static String voiceCommands(String s) {
        s = PARAGRAPH.matcher(s).replaceAll("\n\n");
        s = NEWLINE.matcher(s).replaceAll("\n");
        Matcher m = AFTER_NEWLINE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, "\n" + m.group(1).toUpperCase(Locale.ROOT));
        m.appendTail(sb);
        return sb.toString();
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
