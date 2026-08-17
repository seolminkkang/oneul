package store.oneul.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ★ @EnableScheduling 이 없으면 @Scheduled 가 전혀 동작하지 않는다 (2026-08-17 추가).
 *
 * 스프링 부트는 TaskScheduler 빈까지만 자동 구성하고, @Scheduled 를 실제로 읽어
 * 등록하는 것은 @EnableScheduling 이다. 이게 빠져 있어서
 * ScheduledCancelRetryWorker(취소 재시도, 5초 주기)가 한 번도 돌지 않았다.
 *
 * 확인 방법: Redis 큐(payment:cancel:queue)에 항목을 넣고 20초를 기다렸는데
 * 그대로 남아 있었다. 즉 취소 실패 건이 큐에 쌓인 뒤 영구 방치되고
 * cancel_fail_log 에도 들어가지 않았다.
 */
@SpringBootApplication
@EnableScheduling
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
