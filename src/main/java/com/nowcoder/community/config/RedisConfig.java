package com.nowcoder.community.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory){  // 指定key必须是String
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);  // 连接redis

        // 设置key的序列化方式
        template.setKeySerializer(RedisSerializer.string());

        // 设置value序列化方式（除了hash之外的value建议序列化为json）
        template.setValueSerializer(RedisSerializer.json());

        // 设置hash-key序列化方式
        template.setHashKeySerializer(RedisSerializer.string());

        // 设置hash-value序列化方式
        template.setHashValueSerializer(RedisSerializer.json());

        // 使得配置生效
        template.afterPropertiesSet();
        return template;
    }
}
