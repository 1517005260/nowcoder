package com.nowcoder.community.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling  // 启用Spring定时线程池
@EnableAsync  // 令异步注解@Async生效
public class ThreadPoolConfig {
}
