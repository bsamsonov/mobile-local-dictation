package com.bvs.localdictation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Plain-Java tests for PhraseTracker: run with ./build.sh test. The files in test/traces are engine
 * events recorded on a phone by the trace tool (./build.sh trace) from synthesized Russian speech
 * (debug/make-test-audio.sh); the Russian strings are test fixtures.
 */
public class PhraseTrackerTest {
    private static int fail;

    /** Records what the tracker types; {@code stopAt} makes a commit containing it stop the dictation. */
    private static final class Typed implements PhraseTracker.Sink {
        final List<String> phrases = new ArrayList<>();
        String composing = "";
        String stopAt;
        @Override public void compose(String text) { composing = text; }
        @Override public boolean commit(String text) {
            if (!PhraseTracker.blank(text)) phrases.add(text.trim().isEmpty() ? "\\n" : text.trim());
            else if (!composing.isEmpty()) phrases.add(composing.trim());   // "" keeps what is shown
            composing = "";
            return stopAt == null || !text.contains(stopAt);
        }
        String text() {
            List<String> all = new ArrayList<>(phrases);
            if (!composing.isEmpty()) all.add("[shown] " + composing.trim());
            return String.join(" | ", all);
        }
    }

    private static void check(String name, String got, String want) {
        if (got.equals(want)) return;
        fail++;
        System.out.println("FAIL " + name + "\n  got  [" + got + "]\n  want [" + want + "]");
    }

    private static List<String> hyps(String... h) { return Arrays.asList(h); }

    /** Replays a recorded trace the way the keyboard feeds the tracker. */
    private static String replay(String file, boolean continuous) throws IOException {
        Typed out = new Typed();
        PhraseTracker t = new PhraseTracker(out);
        t.sessionStart(continuous);
        for (String line : Files.readAllLines(Paths.get("test/traces", file), StandardCharsets.UTF_8)) {
            String[] f = line.split("\\|", 3);
            if (f.length < 3) continue;
            long now = Long.parseLong(f[0]);
            String text = f[2].replace("\\n", "\n");
            switch (f[1]) {
                case "partial": t.partial(Arrays.asList(text.split(" ## ", -1)), now); break;
                case "results": t.result(text.equals("<null>") ? "" : text.split(" ## ", -1)[0], true, now); break;
                case "segment": t.result(text.split(" ## ", -1)[0], false, now); break;
                case "endSpeech": t.endOfSpeech(now); break;
                case "error":
                    // The keyboard makes the phrase in progress final and starts a new session.
                    t.sessionFailed(now);
                    t.sessionStart(continuous);
                    break;
                default: break;
            }
        }
        return out.text();
    }

    public static void main(String[] args) throws IOException {
        // ---- recorded traces: every phrase exactly once, nothing lost
        check("continuous, formatting on", replay("continuous-formatted.trace", true),
                "Сегодня я хочу записать несколько мыслей на проекте."
                + " | Если я начинаю говорить после паузы, то часть теста иногда пропадает. Это очень неудобно."
                + " | Нужно разобраться в причинах."
                + " | Новая строка. Первый пункт проверить распознавание на длинных фразах без остановки."
                + " | Когда я говорю быстро и не делаю пауз между словами."
                + " | Второй пункт посмотреть, что происходит с короткими кусками."
                + " | Перевод строки третий пункт записать 25 примеров и сравнитель."
                + " | Результат новая строка."
                + " | И последнее нужно выложить приложение в магазин.");
        check("continuous, formatting off", replay("continuous-unformatted.trace", true),
                "Сегодня я хочу записать несколько мыслей на проекте"
                + " | если я начинаю говорить после паузы"
                + " | то часть теста иногда пропадает"
                + " | это очень неудобно нужно разобраться в причинах"
                + " | новая строка"
                + " | первый пункт проверить распознавание на длинных фразах без остановки когда я говорю быстро и не делаю пауз между словами"
                + " | второй пункт"
                + " | посмотреть что происходит"
                + " | с короткими кусками"
                + " | перевод строки"
                + " | третий пункт записать 25 примеров и сравнитель результат"
                + " | новая строка"
                + " | И последнее нужно выложить приложение в магазин");
        check("continuous, Stop in the middle of a phrase", replay("continuous-stop.trace", true),
                "Сегодня я хочу записать несколько мыслей на проекте."
                + " | Если я начинаю говорить после паузы, то часть теста иногда.");

        // ---- continuous session
        Typed out = new Typed();
        PhraseTracker t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("Первая фраза. Вторая"), 1000);
        t.partial(hyps("Первая фраза.", "первая фраза"), 2000);   // two hypotheses: finished
        t.partial(hyps(" Вторая."), 2001);                         // the rest, right away: not a new phrase to commit
        check("finished phrase, then the rest", out.text(), "Первая фраза. | [shown] Вторая.");
        t.partial(hyps(" Вторая фраза."), 2100);
        t.result("", true, 5000);                                  // Stop: no text in the result
        check("Stop types what is shown", out.text(), "Первая фраза. | Вторая фраза.");
        t.partial(hyps("поздний"), 5001);
        check("partial after the session ended is ignored", out.text(), "Первая фраза. | Вторая фраза.");

        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("Одна фраза.", "одна фраза"), 1000);
        t.result("Одна фраза.", true, 1200);
        check("final repeating a finished phrase is dropped", out.text(), "Одна фраза.");

        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("первая"), 1000);
        t.partial(hyps("вторая"), 1100);                           // 100 ms later: a revision
        check("quick change of the text is a revision", out.text(), "[shown] вторая");
        t.partial(hyps("третья"), 3000);                           // after a pause: a new phrase
        check("new text after a pause is a new phrase", out.text(), "вторая | [shown] третья");
        t.partial(hyps("третья"), 3400);
        t.endOfSpeech(3450);
        t.partial(hyps(" третья"), 3460);                          // the finished phrase once more, unmarked
        t.partial(hyps("четвёртая"), 3700);                        // 240 ms later, but after the pause
        check("new text after the end of speech is a new phrase", out.text(), "вторая | третья | [shown] четвёртая");
        t.sessionFailed(3500);
        t.sessionFailed(3600);
        check("failed session types the phrase once", out.text(), "вторая | третья | четвёртая");

        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("\n"), 1000);
        check("a lone line break is text", out.composing, "\n");

        out = new Typed();
        out.stopAt = "стоп";
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("текст"), 1000);
        boolean go = t.partial(hyps("Текст стоп.", "текст стоп"), 1500);
        check("commit that stops the dictation is reported", String.valueOf(go), "false");

        // ---- the engine's own microphone (segmented session)
        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(false);
        t.partial(hyps("Если я начинаю говорить, то часть текста пропадает"), 1000);
        t.result("пропадает", true, 1100);                         // a piece of what was shown
        t.partial(hyps("Если я начинаю говорить, то часть текста"), 1101);   // late partial of the ended session
        t.sessionFailed(1102);                                     // and its late error
        check("piece as the final, late partial and error: typed once", out.text(),
                "Если я начинаю говорить, то часть текста пропадает");

        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(false);
        t.partial(hyps("привет мир"), 1000);
        t.partial(hyps("Привет, мир.", "привет мир"), 1100);       // not continuous: an ordinary partial
        t.result("Привет, мир!", false, 1200);
        check("segment result replaces the partial", out.text(), "Привет, мир!");

        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(false);
        t.partial(hyps("первая фраза"), 1000);
        t.sessionFailed(1100);
        t.sessionStart(false);
        t.result("Первая фраза.", true, 1500);                     // the late final of the failed session
        check("late final of a typed phrase is dropped", out.text(), "первая фраза");

        if (fail == 0) System.out.println("PhraseTrackerTest: OK");
        else { System.out.println(fail + " failure(s)"); System.exit(1); }
    }
}
