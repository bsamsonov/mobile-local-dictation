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

        // ---- the engine repeats the text shown before a finished phrase (log 16.11: 64 -> [8, 7] -> 64)
        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("Хорошо. Теперь проверим повтор"), 1000);
        t.partial(hyps("Хорошо.", "хорошо"), 2000);
        t.partial(hyps("Хорошо. Теперь проверим повтор"), 2001);    // stale: still holds the finished phrase
        check("repeated finished phrase is not shown again", out.text(), "Хорошо. | [shown] Теперь проверим повтор");
        t.result("", true, 4000);                                  // Stop right here
        check("repeated finished phrase is not typed again", out.text(), "Хорошо. | Теперь проверим повтор");

        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("Да. Да, конечно"), 1000);
        t.partial(hyps("Да.", "да"), 2000);
        t.partial(hyps(" Да, конечно"), 2001);                     // a real rest that starts the same way
        t.result("", true, 3000);
        check("rest starting with the finished words is kept", out.text(), "Да. | Да, конечно");

        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("Сегодня хорошая погода. Пойду"), 1000);
        t.partial(hyps("Сегодня хорошая погода.", "сегодня хорошая погода"), 2000);
        t.partial(hyps(" Сегодня хорошая погода. Пойду гулять"), 2001);   // the engine starts with it again
        t.partial(hyps(" Сегодня хорошая погода.", "сегодня хорошая погода"), 3000);   // and finishes it twice
        t.partial(hyps(" Пойду гулять."), 3001);
        t.result("", true, 5000);
        check("engine repeating a finished phrase in the next one", out.text(),
                "Сегодня хорошая погода. | Пойду гулять.");

        // ---- the user moves the cursor (or presses a key) while a phrase is shown
        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("Сегодня я хочу"), 1000);
        out.phrases.add(out.composing.trim());                     // the editor fixes the shown text
        out.composing = "";
        t.detach();
        t.partial(hyps("Сегодня я хочу записать"), 1100);
        check("after the cursor moved only new words are shown", out.text(), "Сегодня я хочу | [shown] записать");
        t.partial(hyps("Сегодня я хочу записать мысли.", "сегодня я хочу записать мысли"), 2000);
        check("finished phrase after the cursor moved: only the rest", out.text(), "Сегодня я хочу | записать мысли.");
        t.partial(hyps(" Дальше."), 2001);
        t.result("", true, 3000);
        check("next phrase is whole again", out.text(), "Сегодня я хочу | записать мысли. | Дальше.");

        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("сегодня я хочу"), 1000);
        out.phrases.add(out.composing.trim());
        out.composing = "";
        t.detach();
        t.partial(hyps("Сегодня я хотел записать"), 1100);       // the engine revised a fixed word
        t.result("", true, 3000);
        check("revised fixed words are not typed again", out.text(), "сегодня я хочу | записать");

        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        t.partial(hyps("Привет мир"), 1000);
        out.phrases.add(out.composing.trim());
        out.composing = "";
        t.detach();
        t.result("", true, 3000);                                  // Stop with nothing new
        check("Stop after the cursor moved types nothing twice", out.text(), "Привет мир");

        // log 16.11 16:56: Enter at 29 chars, at 73 and at 122; the engine finishes 73, then shows the rest
        out = new Typed();
        t = new PhraseTracker(out);
        t.sessionStart(true);
        String[] enter = {"Первая строка", "Первая строка вторая строка", "Первая строка вторая строка третья строка"};
        long now = 1000;
        for (String e : enter) {
            t.partial(hyps(e), now += 100);
            out.phrases.add(out.composing.trim());                 // Enter: the editor fixes the shown text
            out.composing = "";
            t.detach();
        }
        t.partial(hyps("Первая строка вторая строка.", "первая строка вторая строка"), now += 100);
        t.partial(hyps(" Третья строка"), now += 1);
        t.partial(hyps(" Третья строка четвёртая"), now += 300);
        t.partial(hyps(" Третья строка четвёртая.", "третья строка четвёртая"), now += 300);
        t.result("", true, now += 1000);
        check("Enter inside a long phrase of the engine: every word once", out.text(),
                "Первая строка | вторая строка | третья строка | четвёртая.");

        check("afterPrefix: text is a beginning of the prefix", TextPost.afterPrefix("один два три", "Один два"), "");
        check("afterPrefix keeps a new mark", TextPost.afterPrefix("привет мир", "Привет, мир. Как дела"), ". Как дела");
        check("afterPrefix drops a repeated mark", TextPost.afterPrefix("Привет, мир.", "Привет, мир. Как дела"), " Как дела");

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
