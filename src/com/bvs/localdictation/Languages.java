package com.bvs.localdictation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Language list for the settings drop-down, built from the engine's on-device language lists.
 * Pure Java (no Android SDK) so it is unit-tested in test/.
 */
final class Languages {
    private Languages() {}

    enum State { INSTALLED, PENDING, SUPPORTED, UNSUPPORTED }

    static final class Entry {
        final String tag;
        final State state;

        Entry(String tag, State state) {
            this.tag = tag;
            this.state = state;
        }

        @Override public String toString() { return tag + ":" + state; }
    }

    /** "ru_RU" / "RU-ru" -> "ru-ru": the form used to compare tags. */
    static String norm(String tag) {
        return tag == null ? "" : tag.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static String base(String tag) {
        String n = norm(tag);
        int i = n.indexOf('-');
        return i < 0 ? n : n.substring(0, i);
    }

    /**
     * Installed first, then downloading, then downloadable; each group sorted by tag, no duplicates.
     * The current language is added as UNSUPPORTED if the engine does not list it, so it stays visible.
     */
    static List<Entry> build(List<String> installed, List<String> pending, List<String> supported, String current) {
        List<Entry> out = new ArrayList<>();
        add(out, installed, State.INSTALLED);
        add(out, pending, State.PENDING);
        add(out, supported, State.SUPPORTED);
        if (current != null && !current.trim().isEmpty() && indexOf(out, current) < 0) {
            out.add(new Entry(current.trim(), State.UNSUPPORTED));
        }
        return out;
    }

    private static void add(List<Entry> out, List<String> tags, State state) {
        if (tags == null) return;
        List<String> sorted = new ArrayList<>(tags);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        for (String t : sorted) {
            if (t == null || t.trim().isEmpty() || exact(out, t) >= 0) continue;
            out.add(new Entry(t.trim(), state));
        }
    }

    private static int exact(List<Entry> list, String tag) {
        String n = norm(tag);
        for (int i = 0; i < list.size(); i++) if (norm(list.get(i).tag).equals(n)) return i;
        return -1;
    }

    /** Position of the tag: exact match, else the first entry with the same base language ("en-GB" ~ "en-US"); -1 if absent. */
    static int indexOf(List<Entry> list, String tag) {
        int i = exact(list, tag);
        if (i >= 0) return i;
        String b = base(tag);
        for (int j = 0; j < list.size(); j++) if (base(list.get(j).tag).equals(b)) return j;
        return -1;
    }

    /** "Russian (Russia)" for "ru-RU", in the UI language; the tag itself if Java does not know it. */
    static String displayName(String tag, Locale ui) {
        Locale l = Locale.forLanguageTag(tag.trim().replace('_', '-'));
        String name = l.getDisplayName(ui);
        if (name == null || name.isEmpty() || name.equalsIgnoreCase(tag)) return tag;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
