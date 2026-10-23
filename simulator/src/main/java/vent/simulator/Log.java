package vent.simulator;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 시뮬레이터용 간단한 콘솔 로그.
 *
 * <p>시연 중 터미널에서 흐름을 읽기 쉽도록 시각과 메시지만 한 줄로 찍는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Log() {
    }

    static void info(String format, Object... args) {
        System.out.println(LocalTime.now().format(TIME) + " " + String.format(format, args));
    }

    /** 실패를 표준 오류로 찍어, 로그에서 정상 흐름과 구분되게 한다. */
    static void error(String format, Object... args) {
        System.err.println(LocalTime.now().format(TIME) + " [오류] " + String.format(format, args));
    }
}
