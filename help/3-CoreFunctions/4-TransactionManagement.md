# 事务管理

- 什么是事务
  - 事务是由N步数据库操作序列组成的逻辑执行单元，这系列操作要么全部执行，要么全不执行
  - 如果中间有一个操作失败了，那么之前成功的操作需要全部回滚
- 事务的特性 `ACID`
  - 原子性 `Atomicity` 事务是应用中不可再分的最小执行体
  - 一致性 `Consistency` 事务执行的结果，必须使得诗句从一个一致性状态，变为另一个一致性的状态
  - 隔离性 `Isolation` （并发）各个事务的执行互不干扰，任何事务的内部操作对其他事务都是隔离的
  - 持久性 `Durability` 事务一旦提交，对数据所作的任何改变都要记录到永久存储器中

## 事务的隔离性——针对并发

- 常见并发异常——`同时读写，听谁的？`
  - 第一类丢失更新
  - 第二类丢失更新
  - 脏读
  - 不可重复读
  - 幻读
- 常见的隔离级别（由低到高，越高越安全，效率越低）
  - `Read Uncommitted` 读取未提交的数据
  - `Read Committed` 读取已提交的数据
  - `Repeatable Read` 可重复读
  - `Serializable` 串行化

### 第一类丢失更新

事务A的回滚，导致事务B的已更新的数据丢失

| 时间 | 事务1          | 事务2          |
|------|----------------|----------------|
| T1   | Read: N = 10   |                |
| T2   |                | Read: N = 10   |
| T3   |                | Write: N = 9   |
| T4   |                | Commit: N = 9  |
| T5   | Write: N = 11  |                |
| T6   | Rollback: N = 10 |                |


### 第二类丢失更新

事务A的提交，导致事务B已更新的数据丢失

| 时间 | 事务1            | 事务2          |
|------|----------------|----------------|
| T1   | Read: N = 10   |                |
| T2   |                | Read: N = 10   |
| T3   |                | Write: N = 9   |
| T4   |                | Commit: N = 9  |
| T5   | Write: N = 11  |                |
| T6   | Commit: N = 11 |                |


### 脏读

事务B读取了事务A未提交的数据

| 时间 | 事务1             | 事务2          |
|------|-----------------|--------------|
| T1   | Read: N = 10    |              |
| T2   | Write：N = 11    |              |
| T3   |                 | Read: N = 11 |
| T4   | Rollback:N = 10 |              |

### 不可重复读

同一事务对同一数据前后读取不一致

| 时间 | 事务1            | 事务2          |
|------|----------------|--------------|
| T1   | Read: N = 10   |              |
| T2   |                | Read: N = 10 |
| T3   | Write: N = 11  |              |
| T4   | Commit: N = 11 |              |
| T5   |                | Read：N = 11  |


### 幻读

多行的不可重复读

| 时间 | 事务1                        | 事务2                           |
|------|----------------------------|-------------------------------|
| T1   |                            | Select : id < 10 (1, 2, 3)    |
| T2   | Insert: id = 4             |                               |
| T3   | Commit : id = (1, 2, 3, 4) |                               |
| T4   |                            | Select : id < 10 (1, 2, 3, 4) |


### 事务隔离级别

隔离级别 与 并发异常的出现

| 隔离级别         | 第一类丢失更新 | 第二类丢失更新 | 脏读 | 不可重复读 | 幻读 |
|------------------|----------------|----------------|------|-------|------|
| Read Uncommitted | √              | √              | √    | √     | √    |
| Read Committed   | x              | x              | √    | √     | √    |
| Repeatable Read  | x              | x              | x    | x     | √    |
| Serializable     | x              | x              | x    | x     | x    |


可见，在性能和数据库完成性的双重需求下，常用的隔离级别是 读已提交（适用公司：高性能需求、低完整性需求） 和 可重复读 （幻读通过业务代码解决）

## 实现机制

1. 悲观锁（数据库） —— “认为并发一定会有问题，于是提前加锁解决”
- 共享锁（S锁）

事务A对某数据加了共享锁之后，其他事务只能对该数据加共享锁，但不能加排他锁  `只能读`

- 排他锁（X锁）

事务A对某数据加了排他锁之后，其他事务对该数据既不能加共享锁，也不能加排他锁 `不能读也不能改`

2. 乐观锁（自定义） —— “认为并发也不会有问题，有问题了再解决”
- 版本号、时间戳等

在更新数据前，检查版本号是否发生变化。若变化则取消本次更新，否则更新数据并使版本号++

## Spring事务管理
- 声明式事务
  - 通过XML配置，声明某方法的事务特征
  - 通过注解，声明某方法的事务特征
- 编程式事务
  - 通过 `TransactionTemplate` 管理事务，并通过它执行数据库的操作

#### 事务传播机制：业务方法A可能会调用B的方法，而A和B都可能加上注解来管理事务，那我们听谁的？——涉及事务交叉
- REQUIRED：支持当前事务，A调B，A就是当前事务，若A不存在则创建新事物
- REQUIRES_NEW：创建一个新事务，并且暂停当前事务，A调B，B会强制暂停A，新开事务执行
- NESTED：如果存在当前事务，在嵌套在该事务执行，即B嵌套在A中有独立的提交和回滚，若A不存在同REQUIRED

### 代码演示——声明式事务

1. 在AlphaService中新增：

```java
@Autowired
private UserMapper userMapper;

@Autowired
private DiscussPostMapper discussPostMapper;

//事务管理演示
@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
//注解，使得这个方法的sql变成一个事务，任何该方法下的报错都会使已经修改的数据回滚
public Object save1(){
    //新增用户
    User user = new User();
    user.setUsername("alpha");
    user.setSalt(CommunityUtil.genUUID().substring(0, 5));
    user.setPassword(CommunityUtil.md5("123" + user.getSalt()));
    user.setEmail("alpha@qq.com");
    user.setHeaderUrl("http://image.nowcoder.com/head/99t.png");
    user.setCreateTime(new Date());
    userMapper.insertUser(user);

    // 新增帖子
    DiscussPost post = new DiscussPost();
    post.setUserId(user.getId());
    post.setTitle("你好");
    post.setContent("我是新人!");
    post.setCreateTime(new Date());
    discussPostMapper.insertDiscussPost(post);

    Integer.valueOf("abc");  // 将abc变成整数，人为制造错误

    return "ok";
}
```

2. 测试：

```java
package com.nowcoder.community;

import com.nowcoder.community.service.AlphaService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class TransactionTests {
    @Autowired
    private AlphaService alphaService;

    @Test
    public void testSave1(){
        Object o = alphaService.save1();
        System.out.println(o);
    }
}
```

报错后查看数据库发现没有新增数据，说明保证了ACID

### 代码演示——编程式事务

1. 在AlphaService中新增：

```java
@Autowired
private TransactionTemplate transactionTemplate;

public Object save2(){
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

    return transactionTemplate.execute(new TransactionCallback<Object>() {
        @Override
        public Object doInTransaction(TransactionStatus status) {
            //新增用户
            User user = new User();
            user.setUsername("beta");
            user.setSalt(CommunityUtil.genUUID().substring(0, 5));
            user.setPassword(CommunityUtil.md5("123" + user.getSalt()));
            user.setEmail("beta@qq.com");
            user.setHeaderUrl("http://image.nowcoder.com/head/999t.png");
            user.setCreateTime(new Date());
            userMapper.insertUser(user);

            // 新增帖子
            DiscussPost post = new DiscussPost();
            post.setUserId(user.getId());
            post.setTitle("你好");
            post.setContent("我是新人!");
            post.setCreateTime(new Date());
            discussPostMapper.insertDiscussPost(post);

            Integer.valueOf("abc");  // 将abc变成整数，人为制造错误

            return "ok";
        }
    });
}
```

2. 测试

```java
    @Test
    public void testSave2(){
        Object o = alphaService.save2();
        System.out.println(o);
    }
```

也发现程序错误后数据库没有更新


### 一般推荐简单的声明式事务