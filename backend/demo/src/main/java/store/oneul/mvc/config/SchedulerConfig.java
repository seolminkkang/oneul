package store.oneul.mvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄러 스레드 풀.
 *
 * ★ 왜 코드로 두는가
 * application.properties 는 .gitignore 대상(DB 비밀번호·API 키 때문)이라
 * 거기에 두면 레포에 반영되지 않는다. 클론한 사람은 기본값을 쓰게 된다.
 * 이 값은 비밀이 아니고 **동작에 영향을 주므로** 코드에 둔다.
 *
 * ★ 왜 1이 아니라 3인가
 * 스프링 부트 기본값은 **1**이다. 그러면 스케줄 작업들이 한 줄로 직렬화된다.
 * 현재 스케줄 작업이 둘 있다.
 *
 *   - ScheduledCancelRetryWorker : 5초 주기. 취소 실패 건을 재시도한다
 *   - RefundBatchScheduler       : 1시간 주기. 3,000건에 약 15초 걸린다
 *
 * 풀이 1이면 환급 배치가 도는 15초 동안 취소 재시도가 멈춘다.
 * **돈이 잘못 나간 건을 되돌리는 쪽이 밀리는 것**이라 나눠야 한다.
 *
 * 3으로 둔 이유: 현재 작업이 2개이고 하나가 길게 도는 동안 나머지가 밀리지 않으면
 * 충분하다. 작업이 늘면 같이 늘린다.
 */
@Configuration
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("scheduling-");
        // 종료 시 진행 중인 작업을 기다린다. 환급 도중에 끊기면 미환급 건이 남는다
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        return scheduler;
    }
}
