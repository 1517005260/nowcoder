package com.nowcoder.community.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory){  // 指定key必须是String
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);  // 连接redis

        // 设置key的序列化方式
        template.setKeySerializer(RedisSerializer.string());

        // 设置value序列化方式为GenericJackson2JsonRedisSerializer，以便处理复杂对象
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        // 设置hash-key序列化方式
        template.setHashKeySerializer(RedisSerializer.string());

        // 设置hash-value序列化方式为GenericJackson2JsonRedisSerializer
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        // 使得配置生效
        template.afterPropertiesSet();
        return template;
    }
}
