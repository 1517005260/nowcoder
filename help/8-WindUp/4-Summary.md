# 项目总结

项目模块图：

![summary](/imgs/summary.png)

重点内容：下划线和@标识

项目架构图：

![structure](/imgs/structure.png)

- nginx主从 === 小集群防止单点故障 ==== 处理动态资源 ==== 负载均衡分发请求
- cdn === 处理静态资源  === 第三方服务器，因为全国各地都有，用户可以就近访问
- sqlDB === 读写分离 == db同步，分离请求
- 大多组件都可以集群，但sql数据库建议仅两个，否则涉及分布式事务，比较麻烦

***性能 可靠 安全*** 

# 常见面试知识点

### MySQL

* 存储引擎

MySQL是一个基于存储引擎的数据库，有很多存储引擎可供选择。我们本项目选择的是InnoDB，这是MySQL5.1之后的默认引擎，是支持事务的引擎。

由图，支持事务的引擎只有InnoDB和NDB，但是NDB是集群专用的引擎。MySQL一般能不做集群就不做集群，因为会涉及分布式事务。

![DBlist](/imgs/DBlist.png)

之后的事务、索引等都是基于各引擎实现的。我们主要了解InnoDB的实现机制就行。

* 事务
    * 事务的特性：原子性、一致性、隔离性、持久性
    * 事务的隔离
        * 并发异常：第一类丢失更新、第二类丢失更新、脏读、不可重复读、幻读
        * 隔离级别：Read Uncommited、Read Committed、Repeatable Read、Serializable
        * Spring事务管理：声明式事务、编程式事务
* 锁
    * 范围
        * 表级锁：开销小、加锁快，发生锁冲突的概率高、并发度低，不会出现死锁。
        * 行级锁：开销大、加锁慢，发生锁冲突的概率低、并发度高，会出现死锁。
* 索引（InnoDB）
    * 共享锁（S）：行级，读取一行；
    * 排他锁（X）：表级，更新一行；
    * 意向共享锁（IS）：表级，准备加共享锁；（加S之前要先加IS）
    * 意向排他锁（IX）：表级，准备加排他锁；
    * 间隙锁（NK）：行级，使用范围条件时（id > 100，但是只有101，102时），对范围内不存在的记录加锁（从id = 103开始加锁）。一是为了防止幻读，二是为了满足恢复和复制的需要。

冲突表如下：

|      |  IS  |  IX  |  S   |  X   |
| :--: | :--: | :--: | :--: | :--: |
|  IS  |      |      |      |  x   |
|  IX  |      |      |  x   |  x   |
|  S   |      |  x   |      |  x   |
|  X   |  x   |  x   |  x   |  x   |

* 加锁

    * 增加行级锁之前，InnoDB会<b>自动</b>给表加意向锁；
    * 执行DML语句时，InnoDB会<b>自动</b>给数据加排他锁；
    * 执行DQL语句时，<b>手动</b>
        * 共享锁（S）：SELECT...FROM...WHERE...LOCK IN SHARE MODE;
        * 排他锁（X）：SELECT...FROM...WHERE...FOR UPDATE;
        * 间隙锁（NK）：上述SQL采用范围条件时，InnoDB对不存在的记录自动增加间隙锁。

* 死锁

    * 场景
        * 事务1：UPDATE T SET...WHERE ID=1;UPDATE T SET...WHERE ID=2;
        * 事务2：UPDATE T SET...WHERE ID=2;UPDATE T SET...WHERE ID=1;
    * 解决方案
        * 一般InnoDB会自动检测到，并使一个事务回滚，另一个事务继续；
        * 设置超时等参数 innodb_lock_wait_timeout；
    * 避免死锁——死锁一般是可以通过代码避免的
        * 不同的业务并发访问多个表时，应约定以相同的顺序来访问这些表；
        * 以批量的方式处理数据时，应事先对数据排序，保证线程按固定的顺序来处理数据；
        * 在事务中，如果要更新记录，应直接申请足够级别的锁，即排他锁；

* 悲观锁（数据库MySQL等）

* 乐观锁（自定义）

    * 版本号机制

        * UPDATE..SET...,VERSION=#{version+1 // 更新版本} WHERE ... AND ... VERSION=#{version  // 对指定版本更新，否则失败}

    * CAS算法（Compare and swap）

      是一种无锁的算法实现有锁的机制，该算法涉及3个操作数（内存值V、旧值A、新值B），当V等于A时，采用原子方式用B的值更新V的值。该算法通常采用自旋操作，也叫自旋锁。它的缺点是：

        * ABA问题：某线程将A改为B，再改回A，则CAS会误认为A没被修改过。
        * 自旋操作采用循环的方式实现，若加锁时间长，则会给CPU带来巨大的开销。自旋：线程A加锁了，B不会阻塞而是循环等待
        * CAS只能保证一个共享变量的原子操作。

* B+Tree(InnoDB)

    * 数据分块存储，每一块称为一页；
    * 所有值都是按顺序存储的，并且每一个叶子到根的距离相同；
    * 非叶子节点存储数据的边界，叶子节点存储指向数据行的指针；
    * 通过边界缩小数据的范围，从而避免全表扫描，加快了查找的速度。

![b+](/imgs/b+.png)

### Redis

* 数据类型

|  数据类型   | 最大存储数据量 |
| :---------: |:-------:|
|     key     |  512M   |
|   string    |  512M   |
|    hash     | 2^32-1  |
|    list     | 2^32-1  |
|     set     | 2^32-1  |
| sorted set  |    /    |
|   bitmap    |  512M   |
| hyperloglog |   12K   |

* 过期策略

  Redis会把设置了过期时间的key放入一个独立的字典里，在key过期时并不会立刻删除它。

  Redis会通过如下两种策略，来删除过期的key：

    * 惰性删除

      客户端访问某个key时，Redis会检查该key是否过期，若过期则删除。

    * 定期扫描

      Redis默认每秒执行10次过期扫描（配置hz选项），扫描策略如下：

        1. 从过期字典中随机选择20个key；
        2. 删除这20个key中已过期的key；
        3. 如果过期的key的比例超过25%，则重复步骤1；

* 淘汰策略

  当Redis占用内存超出最大限制（maxmemory）时，可采用如下策略（maxmemory-policy），让Redis淘汰一些数据，以腾出空间继续提供读写服务：

    * noeviction：对可能导致增大内存的命令返回错误（大多数写命令，DEL除外）；
    * volatile-ttl：在设置了过期时间的key中，选择剩余寿命（TTL）最短的key，将其淘汰；
    * volatile-lru：在设置了过期时间的key中，选择最少使用的key（LRU），将其淘汰；
    * volatile-random：在设置了过期时间的key中，随机选择一些key，将其淘汰；
    * allkeys-lru：在所有的key中，选择最少使用的key（LRU），将其淘汰；
    * allkeys-random：在所有的key中，随机选择一些key，将其淘汰；

  LRU算法：

    * 维护一个链表，用于顺序存储被访问过的key。在访问数据时，最新访问过的key将被移动到表头，即最近访问的key在表头，最少访问的key在表尾。

  近似LRU算法（Redis）

    * 给每个key维护一个时间戳，淘汰时随机采样5个key，从中淘汰掉最旧的key。如果还是超出内存限制，则继续随机采样淘汰。
    * 优点：比LRU算法节约内存，却可以取得非常近似的效果。

* 缓存穿透

    * 场景

      查询根本不存在的数据（恶意攻击网站），使得请求直达存储层，导致其负载过大，甚至宕机。

![redis-miss](/imgs/redis-1.png)

  * 解决方案：

      1. 缓存空对象：存储层未命中后，仍然将空值存入缓存层。再次访问该数据时，缓存层会直接返回空值。
      2. 布隆过滤器：将所有存在的key提前存入布隆过滤器，在访问缓存层之前，先通过过滤器拦截，若请求的是不存在的key，则直接返回空值。

* 缓存击穿

    * 场景

      一份热点数据，它的访问量非常大。在其缓存失效瞬间，大量请求直达存储层，导致服务崩溃。

    * 解决方案：

        1. 加互斥锁：对数据的访问加互斥锁，当一个线程访问该数据时，其他线程只能等待。这个线程访问过后，缓存中的数据将被 重建，届时其他线程就可以直接从缓存取值。
        2. 永不过期：不设置过期时间，所以不会出现上述问题，这是“物理“上的不过期。为每个value设置逻辑过期时间，当发现该值逻辑过期时，使用单独的线程重建缓存。

* 缓存雪崩

    * 场景

      由于某些原因，缓存层不能提供服务，导致所有请求直达存储层，造成存储层宕机。

    * 解决方案：

        1. 避免同时过期：设置过期时间时，附加一个随机数，避免大量的key同时过期。
        2. 构建高可用的Redis缓存：部署多个Redis实例，个别节点宕机，依然可以保持服务的整体可用。
        3. 构建多级缓存：增加本地缓存，在存储层前面多加一级屏障，降低请求直达存储层的几率。
        4. 启用限流和降级措施：对存储层增加限流措施，当请求超出限制时，对其提供降级服务。

* 分布式锁

    * 场景

      修改时，经常需要将数据读取到内存，在内存中修改后再存回去。在分布式应用中，可能多个进程同时执行上述操作，而读取和修改非原子操作，所以会产生冲突。增加分布式锁，可以解决此类问题。

    * 基本原理

        * 同步锁：在多个线程都能访问到的地方，做一个标记，标识该数据的访问权限。
        * 分布式锁：在多个进程都能访问到的地方，做一个标记，标识该数据的访问权限。

    * 实现方式

        1. 基于数据库实现分布式锁；
        2. 基于Redis实现分布式锁；
        3. 基于Zookeeper实现分布式锁；

    * Redis实现分布式锁的原则

        1. 安全属性：独享。在任一时刻，只有一个客户端持有锁。
        2. 活性A：无死锁。即便持有锁的客户端崩溃或者网络被分裂，锁仍然可以被获取。
        3. 活性B：容错。只要大部分Redis节点都活着，客户端就可以获取和释放锁。

    * 单Redis实例实现分布式锁

        1. 获取锁使用命令：

           ```
           SET resource_name my_random_value NX PX 30000
           ```

           NX：仅在key不存在时才执行成功。PX：设置锁的自动过期时间。

        2. 通过Lua脚本释放锁：

           ```
           if redis.call("get",KEYS[1]) == ARGV[1] then 
               return redis.call("del",KEYS[1])
           else return 0 end
           ```

           可以避免删除别的客户端获取成功的锁：

           A加锁 --> A阻塞 --> 因超时释放锁 --> B加锁 --> A恢复 --> 释放锁

    * 多Redis实例实现分布式锁

      Redlock算法，该算法有现成的实现，其Java版本的库为Redisson。

        1. 获取当前Unix时间，以毫秒为单位。
        2. 依次尝试从N个实例，使用相同的key和随机值获取锁，并设置响应超时时间。如果服务器没有在规定时间内响应，客户端应该尽快尝试另外一个Redis实例。
        3. 客户端使用当前时间减去开始获取锁的时间，得到获取锁使用的时间。当且仅当大多数的Redis节点都取到锁，并且使用的时间小于锁失效的时间时，锁才算取得成功。
        4. 如果取到了锁，key的真正有效时间等于有效时间减去获取锁使用的时间。
        5. 如果获取锁失败，客户端应该在所有的Redis实例上进行解锁。

### Spring

* Spring IOC
    * Bean的作用域

|    作用域     |    使用范围    |                             描述                             |
| :-----------: | :------------: | :----------------------------------------------------------: |
|   singleton   | 所有Spring应用 |               在容器中只存在一个实例，默认值。               |
|   prototype   | 所有Spring应用 | 在容器中存在多个实例，即每次获取该Bean时，都会创建一个新实例。 |
|    request    | SpringWeb应用  |                 为每个请求创建一个新的实例。                 |
|    session    | SpringWeb应用  |                 为每个会话创建一个新的实例。                 |
| globalSession | SpringWeb应用  |    为全局的session创建一个实例，只在Portlet上下文中有效。    |
|  application  | SpringWeb应用  |               为整个Web应用创建一个新的实例。                |

* Spring AOP

    * AOP的术语

      Target（Joinpoint）<-- Weaving <-- Aspect(Pointcut(s.find*(..))、Advice(q前、后、返回、异常))

        1. 编译时织入，需使用特殊的编译器。
        2. 装载时织入，需使用特殊的类装载器。
        3. 运行时织入，需为目标生成代理对象。

* Spring MVC

    1. **客户端**发出请求访问服务器时，由**DispatcherServlet**处理。
    2. DispatcherServlet调用**HandlerMapping**(根据访问路径找到对应Controller)。
    3. HandlerMapping给DispatcherServlet返回**HandlerExecutionChain**对象（封装了Controller和拦截器）。
    4. DispatcherServlet调用拦截器的preHandle()方法，接着调用**HandlerAdapter**(内部调了Controller)。
    5. HandlerAdapter返回**ModelAndView**给DispatcherServlet，返回后调用postHandle()方法。
    6. DispatcherServlet调用**ViewResolver**(视图解析器)。
    7. ViewResolver解析**View**，由模板引擎渲染，（拦截器的afterCompletion()方法）返回客户端。

![sum](/imgs/Spring%20Mvc%20Sum.png)