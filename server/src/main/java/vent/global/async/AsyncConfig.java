package vent.global.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 백그라운드 작업 스레드 설정 (#10).
 *
 * <p>{@code @EnableAsync}는 앱 전체 설정이라 여기에 둔다. 지금 작업 스레드 풀은 AI 소견용 하나다. AI 소견은 외부 API를
 * 부르는 느린 작업이라 전용 스레드 2개에서 돌린다. MQTT 수신, HTTP 요청, 스케줄러 스레드를 붙잡지 않는다. 대기열이 가득 차면 버리고 로그만 남긴다. 버려진 신청은 접수됨으로 남아 재처리 스케줄러가 다시 맡는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "assessmentExecutor")
    public ThreadPoolTaskExecutor assessmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("assessment-");
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("AI 소견 대기열이 가득 차 버림, 재처리 스케줄러가 다시 맡는다"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
