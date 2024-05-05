# Spring整合redis

- 引入依赖：`spring-boot-starter-data-redis`
- 配置redis
  - 配置数据库参数
  - 编写配置类，构造RedisTemplate
- 访问Redis
  - `redisTemplate.opsForValue()`
  - `redisTemplate.opsForHash()`
  - `redisTemplate.opsForList()`
  - `redisTemplate.opsForSet()`
  - `redisTemplate.opsForZSet()`

## 代码实现

1. 导包

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
  <version>3.2.5</version>
</dependency>
```

2. 配置

在application.properties里：

```
# redis——>RedisProperties
spring.data.redis.database = 11   # 使用index=11的数据库，可以自己选
spring.data.redis.host=localhost  # 本机端口6379
spring.data.redis.port=6379
```

redis配置类：

```java
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
        template.setKeySerializer(RedisSerializer.string()); //无论键的原始数据类型是什么，它们都将被转换成字符串格式
        
        // 设置value序列化方式（除了hash之外的value建议序列化为json）
        template.setValueSerializer(RedisSerializer.json());  //这样可以将Java对象转换为JSON格式的字符串，便于在Redis中存储复杂数据结构。JSON格式易于人阅读和跨语言使用，也支持数据结构的层次性。
        
        // 设置hash-key序列化方式
        template.setHashKeySerializer(RedisSerializer.string());
        
        // 设置hash-value序列化方式
        template.setHashValueSerializer(RedisSerializer.json());
        
        // 使得配置生效
        template.afterPropertiesSet();
        return template;
    }
}
```

3. 测试——和上次的演示内容一致

```java
package com.nowcoder.community;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class RedisTests {

    @Autowired
    private RedisTemplate redisTemplate;

    @Test
    public void testString(){
        String redisKey = "test:count";
        redisTemplate.opsForValue().set(redisKey, 1);
        System.out.println(redisTemplate.opsForValue().get(redisKey));
        System.out.println(redisTemplate.opsForValue().increment(redisKey));
        System.out.println(redisTemplate.opsForValue().decrement(redisKey));
    }

    @Test
    public void testHash(){
        String redisKey = "test:user";
        redisTemplate.opsForHash().put(redisKey,"id",1);
        redisTemplate.opsForHash().put(redisKey,"username","peter");
        System.out.println(redisTemplate.opsForHash().get(redisKey,"id"));
        System.out.println(redisTemplate.opsForHash().get(redisKey,"username"));
    }

    @Test
    public void testList(){
        String redisKey = "test:ids";
        redisTemplate.opsForList().leftPush(redisKey, 101);
        redisTemplate.opsForList().leftPush(redisKey, 102);
        redisTemplate.opsForList().leftPush(redisKey, 103);
        System.out.println(redisTemplate.opsForList().size(redisKey));
        System.out.println(redisTemplate.opsForList().index(redisKey, 0));
        System.out.println(redisTemplate.opsForList().range(redisKey, 0,2));
        System.out.println(redisTemplate.opsForList().rightPop(redisKey));
        System.out.println(redisTemplate.opsForList().rightPop(redisKey));
        System.out.println(redisTemplate.opsForList().rightPop(redisKey));
    }

    @Test
    public void testSet(){
        String redisKey = "test:teachers";
        redisTemplate.opsForSet().add(redisKey, "aaa","bbb","ccc");
        System.out.println(redisTemplate.opsForSet().size(redisKey));
        System.out.println(redisTemplate.opsForSet().pop(redisKey));
        System.out.println(redisTemplate.opsForSet().members(redisKey));
    }

    @Test
    public void testSortedSet(){
        String redisKey = "test:students";
        redisTemplate.opsForZSet().add(redisKey,"aaa",80);
        redisTemplate.opsForZSet().add(redisKey,"bbb",70);
        redisTemplate.opsForZSet().add(redisKey,"ccc",60);
        redisTemplate.opsForZSet().add(redisKey,"ddd",50);
        System.out.println(redisTemplate.opsForZSet().zCard(redisKey));
        System.out.println(redisTemplate.opsForZSet().score(redisKey, "aaa"));
        System.out.println(redisTemplate.opsForZSet().rank(redisKey, "aaa"));
        System.out.println(redisTemplate.opsForZSet().reverseRank(redisKey, "aaa"));
        System.out.println(redisTemplate.opsForZSet().range(redisKey, 0,2));
    }

    @Test
    public void testPublic(){
        redisTemplate.delete("test:user");
        System.out.println(redisTemplate.hasKey("test:user"));
        redisTemplate.expire("test:students",10, TimeUnit.SECONDS);
    }

    // 多次访问同一个key
    @Test
    public void testBoundOperations(){
        String redisKey = "test:count";
        BoundValueOperations operations = redisTemplate.boundValueOps(redisKey);
        operations.increment();  // 等价于 redisTemplate.opsForValue().increment("test:count")
        System.out.println(operations.get());
    }
}
```

4. redis的事务管理机制：不完全的ACID

启用事务后，若执行一个redis命令，不会立刻执行这个命令，而是把这个命令先放到一个队列里，直到一个事务完成后再一股脑地将所有命令发送给redis服务器执行

<b>所以，不能在事务中间进行查询，否则没有结果</b>

由于redis独特的事务机制，所以一般不会使用声明式事务，否则整个方法都不能进行查询操作。一般均使用编程式事务，限定某几行代码为一个事务

示例：

```java
    // 编程式事务
    @Test
    public void testTransaction(){
        Object obj = redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                String redisKey = "test:tx";

                operations.multi();  //启用事务

                operations.opsForSet().add(redisKey,"aaa");
                operations.opsForSet().add(redisKey,"bbb");
                operations.opsForSet().add(redisKey,"ccc");

                System.out.println(operations.opsForSet().members(redisKey));

                return operations.exec(); //结束事务
            }
        });
        System.out.println(obj);
    }
```

发现输出：

```
[]
[1, 1, 1, [ccc, bbb, aaa]]
```

可以发现事务结束之前是查询为空的

三个1代表每个命令改变的数据行数（个数）都是1

最终obj也显示了查询的正确结果