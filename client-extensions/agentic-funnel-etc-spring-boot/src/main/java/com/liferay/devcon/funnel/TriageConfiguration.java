package com.liferay.devcon.funnel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author José Abelenda
 */
@Configuration
@EnableScheduling
public class TriageConfiguration {

	@Bean
	public TaskExecutor triageTaskExecutor(
		@Value("${com.liferay.devcon.funnel.triage.pool.size}") int poolSize) {

		ThreadPoolTaskExecutor threadPoolTaskExecutor =
			new ThreadPoolTaskExecutor();

		threadPoolTaskExecutor.setCorePoolSize(poolSize);
		threadPoolTaskExecutor.setMaxPoolSize(poolSize);
		threadPoolTaskExecutor.setQueueCapacity(100);
		threadPoolTaskExecutor.setThreadNamePrefix("triage-");

		return threadPoolTaskExecutor;
	}

}
