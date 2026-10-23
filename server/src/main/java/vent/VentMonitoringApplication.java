package vent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 환기 모니터링 서버의 진입점.
 *
 * <p>현장에 설치된 열회수 환기장치와 VEMC의 상태를 수집하고, 이상을 감지하며, A/S 신청을 돕는 Spring Boot 앱을 시작한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
@SpringBootApplication
public class VentMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(VentMonitoringApplication.class, args);
    }
}
