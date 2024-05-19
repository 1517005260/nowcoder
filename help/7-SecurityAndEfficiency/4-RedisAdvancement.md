# Redis高级数据类型——应用于网站的数据统计

- HyperLogLog 超级日志
  - 采用一种基数算法，用于完成独立总数的统计（比如：独立访客uv）
  - 占据空间小，无论统计多少个数据，只占用12K的内存空间，比集合Set的优势大
  - 不精确的统计算法，标准误差为0.81%
  - 可以通过`info memory`命令检查内存用量
- Bitmap 位图
  - 不是一种独立的数据结构，实际上就是字符串
  - 支持按位存取数据，可以将其看为byte数组
  - 适合存储大量的连续的数据的boolean（比如：签到，一年只有356b大小），是精确的值

## 语法演示

在RedisTests中测试：

```java
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
```