package com.bvs.localdictation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Plain-Java tests for Languages (no JUnit, no Gradle): run with ./build.sh test. */
public class LanguagesTest {
    private static int fail = 0;

    private static void eq(String what, Object got, Object want) {
        if (!Objects.equals(got, want)) {
            fail++;
            System.out.println("FAIL " + what + ": got [" + got + "] want [" + want + "]");
        }
    }

    public static void main(String[] args) {
        List<String> installed = Arrays.asList("ru-RU", "en-US");
        List<String> pending = Collections.singletonList("de-DE");
        List<String> supported = Arrays.asList("fr-FR", "en-US", "uk-UA", "ru-RU");

        // Order: installed, downloading, downloadable; sorted inside a group; no duplicates.
        eq("build", Languages.build(installed, pending, supported, "ru-RU").toString(),
                "[en-US:INSTALLED, ru-RU:INSTALLED, de-DE:PENDING, fr-FR:SUPPORTED, uk-UA:SUPPORTED]");
        // An unlisted current language stays visible.
        eq("build keeps current", Languages.build(installed, null, null, "xx-YY").toString(),
                "[en-US:INSTALLED, ru-RU:INSTALLED, xx-YY:UNSUPPORTED]");
        // Base-language match counts as listed.
        eq("build base match", Languages.build(installed, null, null, "ru").toString(),
                "[en-US:INSTALLED, ru-RU:INSTALLED]");
        eq("null lists", Languages.build(null, null, null, null).toString(), "[]");

        List<Languages.Entry> list = Languages.build(installed, pending, supported, null);
        eq("indexOf exact", Languages.indexOf(list, "ru-RU"), 1);
        eq("indexOf case/underscore", Languages.indexOf(list, "RU_ru"), 1);
        eq("indexOf base", Languages.indexOf(list, "uk"), 4);
        eq("indexOf absent", Languages.indexOf(list, "ja-JP"), -1);

        eq("display en", Languages.displayName("ru-RU", Locale.ENGLISH), "Russian (Russia)");
        eq("display unknown", Languages.displayName("qq", Locale.ENGLISH), "qq");

        System.out.println(fail == 0 ? "ALL OK" : fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
