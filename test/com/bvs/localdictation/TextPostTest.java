package com.bvs.localdictation;

import java.util.Objects;

/**
 * Plain-Java tests for TextPost (no JUnit, no Gradle): run with ./build.sh test.
 * Russian strings are test fixtures.
 */
public class TextPostTest {
    public static void main(String[] args) {
        String[][] cases = {
                {"двадцать пять минут", "25 минут"},
                {"в две тысячи двадцать шестом году", "в 2020 шестом году"}  /* ordinals not handled */,
                {"две тысячи двадцать шесть", "2026"},
                {"он один пришёл и пять раз сказал", "он один пришёл и пять раз сказал"},
                {"сто пять рублей", "105 рублей"},
                {"триста тысяч", "300000"},
                {"полтора миллиона", "полтора 1000000"},
                {"пять пять пять", "5 5 5"},
                {"десять", "10"},
                {"ноль", "ноль"},
                {"Двадцать один", "21"},
                {"тысяча девятьсот восемьдесят четыре", "1984"},
                {"один миллион двести тысяч триста", "1200300"},
        };
        int fail = 0;
        for (String[] c : cases) {
            String got = TextPost.numbersToDigits(c[0]);
            if (!got.equals(c[1])) { fail++; System.out.println("FAIL num: [" + c[0] + "] -> [" + got + "] want [" + c[1] + "]"); }
        }
        String[][] cmd = {
                {"Привет. Новая строка. как дела", "Привет.\nКак дела"},
                {"первое новая строка второе", "первое\nВторое"},
                {"конец мысли. Новый абзац. начало", "конец мысли.\n\nНачало"},
                {"строка новая", "строка новая"},
                {"Новая строка", "\n"},
        };
        for (String[] c : cmd) {
            String got = TextPost.voiceCommands(c[0]);
            if (!got.equals(c[1])) { fail++; System.out.println("FAIL cmd: [" + c[0] + "] -> [" + got.replace("\n", "\\n") + "] want [" + c[1].replace("\n", "\\n") + "]"); }
        }
        String[][] stop = {
                {"Стоп", ""},
                {"стоп.", ""},
                {"Всё готово. Стоп.", "Всё готово."},
                {"всё готово стоп", "всё готово"},
                {"знак стоп на углу", null},
                {"стоп-кран", null},
                {"нажми на стопку", null},
        };
        for (String[] c : stop) {
            String got = TextPost.stripStop(c[0]);
            if (!Objects.equals(got, c[1])) { fail++; System.out.println("FAIL stop: [" + c[0] + "] -> [" + got + "] want [" + c[1] + "]"); }
        }
        System.out.println(fail == 0 ? "ALL OK" : fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
