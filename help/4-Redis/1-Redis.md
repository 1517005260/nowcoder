# [Redis](https://redis.io/)入门

- Redis（Remote Dictionary Server）是一款基于键值对的NoSQL（not only sql）数据库，它的键都是字符串，值支持多种数据结构，包括：字符串`String`，哈希`hashes`，列表`lists`，集合`sets`，有序集合`sorted sets`等
- Redis将所有的数据都存放在内存中，所以它的读写性能非常快
- Redis还可以将内存中的数据以快照（RDB，直接将内存数据存在硬盘上，恢复快体积小，但是耗时间，适合攒着做一次）或日志（AOF，每执行一个redis命令就存一个命令，实时性快，体积大，回复慢）的形式保存到硬盘上，以保证数据的安全性
- Redis的典型应用场景：缓存，排行榜，计数器，社交网络（赞/踩），消息队列（非专业）等

[Github下载windows版](https://github.com/tporadowski/redis)

<b>记得把安装路径，比如`D:\Program Files\redis`配置到环境变量</b>


## 基本操作
1. 启动：

```bash
C:\Users\15170>redis-cli
127.0.0.1:6379>
```

2. Redis内置了16个数据库，索引index 0-15，可以这么切换:

```bash
127.0.0.1:6379> select 1
OK
127.0.0.1:6379[1]> select 0
OK
```

3. 刷新数据库（清除数据）：

```bash
127.0.0.1:6379> flushdb
OK
```

4. String

key多单词连接用冒号 过期时间可以不加

```bash
127.0.0.1:6379> set key value [expiration EX seconds|PX milliseconds] [NX|XX]


127.0.0.1:6379> set test:count 1
OK
127.0.0.1:6379> get test:count
"1"
127.0.0.1:6379> incr test:count
(integer) 2
127.0.0.1:6379> decr test:count
(integer) 1
```

5. Hash

```bash
127.0.0.1:6379> hset test:user id 1
(integer) 1
127.0.0.1:6379> hset test:user username peter
(integer) 1
127.0.0.1:6379> hget test:user id
"1"
127.0.0.1:6379> hget test:user username
"peter"
```

6. List（同时支持队列和栈，左进左出为栈，右进左出为队列）

左进右出时：

```bash
127.0.0.1:6379> lpush test:ids 101 102 103
(integer) 3
127.0.0.1:6379> llen test:ids
(integer) 3
127.0.0.1:6379> lindex test:ids 0
"103"
127.0.0.1:6379> lindex test:ids 2
"101"
127.0.0.1:6379> lrange test:ids 0 2
1) "103"
2) "102"
3) "101"
127.0.0.1:6379> rpop test:ids
"101"
127.0.0.1:6379> rpop test:ids
"102"
```

7. Set

```bash
127.0.0.1:6379> sadd test:teachers aaa bbb ccc ddd eee
(integer) 5
127.0.0.1:6379> scard test:teachers  //个数
(integer) 5
127.0.0.1:6379> spop test:teachers   //随机弹出一个元素 可用于实现抽奖
"eee"
127.0.0.1:6379> spop test:teachers
"aaa"
127.0.0.1:6379> smembers test:teachers
1) "bbb"
2) "ccc"
3) "ddd"
```

8. Sorted Set

```bash
127.0.0.1:6379> zadd test:students 10 aaa 20 bbb 30 ccc 40 ddd 50 eee
(integer) 5
127.0.0.1:6379> zcard test:students
(integer) 5
127.0.0.1:6379> zscore test:students ccc
"30"
127.0.0.1:6379> zrank test:students ccc
(integer) 2
127.0.0.1:6379> zrange test:students 0 2
1) "aaa"
2) "bbb"
3) "ccc"
```

9. 全局命令：

```bash
127.0.0.1:6379> keys *     // 查找所有key
1) "test:count"
2) "test:user"
3) "test:ids"
4) "test:teachers"
5) "test:students"
127.0.0.1:6379> keys test*  // 查找以test打头的key
1) "test:count"
2) "test:user"
3) "test:ids"
4) "test:teachers"
5) "test:students"
127.0.0.1:6379> type test:user
hash
127.0.0.1:6379> exists test:user
(integer) 1
127.0.0.1:6379> del test:user
(integer) 1
127.0.0.1:6379> exists test:user
(integer) 0
127.0.0.1:6379> expire test:students 10  // 10秒 
(integer) 1
127.0.0.1:6379> keys *
1) "test:count"
2) "test:ids"
3) "test:teachers"
4) "test:students"
127.0.0.1:6379> keys *
1) "test:count"
2) "test:ids"
3) "test:teachers"
```