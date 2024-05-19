package com.nowcoder.community;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.*;
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

    // 高级数据结构测试

    //hyperloglog：统计20w个重复数据的去重总数
    @Test
    public void testHyperLogLog(){
        String redisKey = "test:hll:01";

        for(int i = 1; i <= 100000; i ++){
            redisTemplate.opsForHyperLogLog().add(redisKey, i);
        }

        for(int i = 1; i <= 100000; i ++){
            int r = (int) (Math.random() * 100000 + 1);  // [0,1) * 100000 + 1
            redisTemplate.opsForHyperLogLog().add(redisKey, r);
        }

        // 去重之后显然应该为10w个数字
        long  size = redisTemplate.opsForHyperLogLog().size(redisKey);
        System.out.println(size);
        // 发现输出99562，这是个不精确的数字，但是误差比较小
    }

    // hyperloglog: 数据的合并
    // ex. 统计用户7天内的uv
    // 将3组数据合并，再统计合并后的重复数据的独立总数
    @Test
    public void testHyperLogLogUnion(){
        String redisKey2 = "test:hll:02";
        for(int i = 1; i <= 10000; i ++){
            redisTemplate.opsForHyperLogLog().add(redisKey2, i);
        }
        String redisKey3 = "test:hll:03";
        for(int i = 5001; i <= 15000; i ++){
            redisTemplate.opsForHyperLogLog().add(redisKey3, i);
        }
        String redisKey4 = "test:hll:04";
        for(int i = 10001; i <= 20000; i ++){
            redisTemplate.opsForHyperLogLog().add(redisKey4, i);
        }

        // 显然去重后为2w个数据
        String UnionKey = "test:hll:union";
        redisTemplate.opsForHyperLogLog().union(UnionKey, redisKey2, redisKey3, redisKey4);
        System.out.println(redisTemplate.opsForHyperLogLog().size(UnionKey));
        //输出19891，也是一个近似值
    }

    // bitmap:统计一组数据的boolean值
    @Test
    public void testBitMap(){
        String redisKey = "test:bm:01";
        redisTemplate.opsForValue().setBit(redisKey, 1, true);
        redisTemplate.opsForValue().setBit(redisKey, 4, true);
        redisTemplate.opsForValue().setBit(redisKey, 7, true);
        redisTemplate.opsForValue().setBit(redisKey, 10, true);
        // 默认就是false，不用单独存

        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 0));
        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 1));
        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 4));
        // 输出：false true true

        // 统计：redis底层连接
        Object o = redisTemplate.execute(new RedisCallback() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                return connection.bitCount(redisKey.getBytes());
            }
        });
        System.out.println(o); // 输出：4，因为只有4个true
     }

     // bitmap: 多组boolean的与或非运算
    // 本例：3组数据的OR
    @Test
    public void testBitMapOps(){
        String redisKey2 = "test:bm:02";
        redisTemplate.opsForValue().setBit(redisKey2, 0, true);
        redisTemplate.opsForValue().setBit(redisKey2, 1, true);
        redisTemplate.opsForValue().setBit(redisKey2, 2, true);

        String redisKey3 = "test:bm:03";
        redisTemplate.opsForValue().setBit(redisKey3, 2, true);
        redisTemplate.opsForValue().setBit(redisKey3, 3, true);
        redisTemplate.opsForValue().setBit(redisKey3, 4, true);

        String redisKey4 = "test:bm:04";
        redisTemplate.opsForValue().setBit(redisKey4, 4, true);
        redisTemplate.opsForValue().setBit(redisKey4, 5, true);
        redisTemplate.opsForValue().setBit(redisKey4, 6, true);

        String redisKey = "test:bm:or";
        Object o = redisTemplate.execute(new RedisCallback() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                connection.bitOp(RedisStringCommands.BitOperation.OR,
                        redisKey.getBytes(), redisKey2.getBytes(), redisKey3.getBytes(), redisKey4.getBytes());
                return connection.bitCount(redisKey.getBytes());
            }
        });
        System.out.println(o);
        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 0));
        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 1));
        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 2));
        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 3));
        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 4));
        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 5));
        System.out.println(redisTemplate.opsForValue().getBit(redisKey, 6));
        // 输出全true
    }
}
