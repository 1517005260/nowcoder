# 任务执行和调度

有些功能，并不是浏览器主动访问浏览器去处理的，而是服务器定时启动运行的，例如，每半小时清理临时文件、每小时计算帖子热度等

==> 任务调度组件解决（多线程，后台有个自动完成任务的线程）

项目中但凡涉及多线程，一定是通过线程池解决的，因为创建线程是有开销的且比较大，而线程池可以线程复用，可以节约资源

## 线程池

- JDK自带线程池
  - ExecutorService（普通线程池）
  - ScheduledExecutorService（定时线程，间隔任务）
- Spring线程池
  - ThreadPoolTaskExecutor
  - ThreadPoolTaskScheduler
- 分布式定时任务
  - [Spring Quartz](https://www.quartz-scheduler.org/)


![noQuartz](/imgs/noQuartz.png)

如图，在分布式环境下，每个server中有两类程序：普通（例如controller）和定时（例如scheduler），服务器由Nginx负载均衡代理

对于普通请求（注册、登录、查看首页等），分发给controller处理时，正常处理即可。同一个请求只有一个服务器上的controller来处理

而对于定时任务，两台服务器上的定时程序都是一样的，两个定时任务会重复。而JDK和Spring的线程池没有解决这个问题，需要引入Quartz

JDK和Spring的线程池的配置参数都是在内存里的，服务器之间的内存不共享。Quartz的定时任务参数是存在数据库里的，所以服务器之间可以共享信息。不同server的定时任务以抢锁的方式唯一确定一个任务的执行者。

![Quartz](/imgs/Quartz.png)

## 线程池的使用

新建测试类ThreadPoolTests

1. jdk线程池测试：

```java
package com.nowcoder.community;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class ThreadPoolTests {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolTests.class);

    // jdk普通线程池
    private ExecutorService executorService = Executors.newFixedThreadPool(5); // 包含5个线程

    // jdk定时线程池
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);

    // 阻塞程序，防止程序过快结束
    private void sleep(long m){
        try{
            Thread.sleep(m);  // 毫秒
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    @Test
    public void textJDKExecutorService(){
        Runnable task = new Runnable() {   // 给这个线程分配一个任务
            @Override
            public void run() {
                logger.debug("This is jdk ExecutorService");
            }
        };

        for(int i = 0; i < 10; i++){
            executorService.submit(task);
        }

        sleep(10000);  // 10s
    }

    @Test
    public void textJDKScheduledExecutorService(){
        Runnable task = new Runnable() {
            @Override
            public void run() {
                logger.debug("This is jdk ScheduledExecutorService");
            }
        };

        // 以固定频率定时执行,参数：任务、任务延迟多久才执行、时间间隔、时间单位
        scheduledExecutorService.scheduleAtFixedRate(task, 10000, 1000, TimeUnit.MILLISECONDS) ;
        sleep(30000); // 30s结束任务
    }
}
```

a. 普通线程输出：

```
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-3] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-2] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-4] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-5] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-3] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-2] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-4] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
2024-05-21 15:16:11,854 DEBUG [pool-1-thread-5] c.n.c.ThreadPoolTests [ThreadPoolTests.java:41] This is jdk ExecutorService
```

pool-1-thread-3是线程的名字，可见一直在复用池子里的5个线程

b. 定时线程输出：

```
...前10s没有任务执行
2024-05-21 15:22:29,291 DEBUG [pool-2-thread-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:58] This is jdk ScheduledExecutorService
......
2024-05-21 15:22:48,301 DEBUG [pool-2-thread-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:58] This is jdk ScheduledExecutorService
2024-05-21 15:22:49,297 DEBUG [pool-2-thread-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:58] This is jdk ScheduledExecutorService
```

由于设置了sleep(30000);所以30s后任务结束

2. Spring线程池

a. 先打开application.properties，配置启动后带几个线程等的参数：

```
# ThreadPool
spring.task.execution.pool.core-size = 5  # 设置线程池数量，由于涉及浏览器访问，所以数量不确定，需要后续预备工作
spring.task.execution.pool.max-size = 15 # 线程池容量不够时会扩容，这是扩容上限
spring.task.execution.pool.queue-capacity = 100 # 队列容量 到达扩容上限时，若有更多线程时的等待队列长度
spring.task.scheduling.pool.size=5 # 定时线程池大小，由于是系统定时任务，已知大小，其他不用配置
```

b. 配置ThreadPoolTaskScheduler

在Config目录下新建ThreadPoolConfig:

```java
package com.nowcoder.community.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling  // 启用Spring定时线程池
@EnableAsync  // 令异步注解@Async生效
public class ThreadPoolConfig {
}
```

c. 还是在原来的测试类中：

```java
// spring普通线程池
@Autowired
private ThreadPoolTaskExecutor taskExecutor;

// spring定时线程池
@Autowired
private ThreadPoolTaskScheduler taskScheduler;

@Test
public void springTaskExecutor(){
  Runnable task = new Runnable() {
    @Override
    public void run() {
      logger.debug("This is jdk Spring TaskExecutor");
    }
  };

  for(int i = 0; i < 10; i++){
    taskExecutor.submit(task);
  }
  sleep(10000);
}

@Test
public void springTaskScheduler(){
  Runnable task = new Runnable() {
    @Override
    public void run() {
      logger.debug("This is Spring TaskScheduler");
    }
  };

  Date startTime = new Date(System.currentTimeMillis() + 10000);  // 系统时 + 10s
  taskScheduler.scheduleAtFixedRate(task, startTime, 1000);  // 间隔1s
  sleep(30000); // 30s结束任务
}
```

d. 普通线程池输出：

```
2024-05-21 15:45:30,007 DEBUG [task-2] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
2024-05-21 15:45:30,006 DEBUG [task-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
2024-05-21 15:45:30,007 DEBUG [task-3] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
2024-05-21 15:45:30,007 DEBUG [task-4] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
2024-05-21 15:45:30,007 DEBUG [task-2] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
2024-05-21 15:45:30,007 DEBUG [task-5] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
2024-05-21 15:45:30,007 DEBUG [task-3] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
2024-05-21 15:45:30,007 DEBUG [task-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
2024-05-21 15:45:30,007 DEBUG [task-4] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
2024-05-21 15:45:30,007 DEBUG [task-2] c.n.c.ThreadPoolTests [ThreadPoolTests.java:83] This is jdk Spring TaskExecutor
```

可见也是有5个线程。虽然与之前的JDK普通线程的输出效果一致，但是本方法的好处在于可以灵活进行配置（application.properties），所以一般优先用本方法

e. 定时线程池输出：

```
...也是等待了10s再执行...
2024-05-21 15:51:50,177 DEBUG [scheduling-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:99] This is Spring TaskScheduler
2024-05-21 15:51:51,182 DEBUG [scheduling-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:99] This is Spring TaskScheduler
2024-05-21 15:51:52,190 DEBUG [scheduling-2] c.n.c.ThreadPoolTests [ThreadPoolTests.java:99] This is Spring TaskScheduler
2024-05-21 15:51:53,186 DEBUG [scheduling-1] c.n.c.ThreadPoolTests [ThreadPoolTests.java:99] This is Spring TaskScheduler
......
2024-05-21 15:52:09,186 DEBUG [scheduling-3] c.n.c.ThreadPoolTests [ThreadPoolTests.java:99] This is Spring TaskScheduler
2024-05-21 15:52:10,178 DEBUG [scheduling-3] c.n.c.ThreadPoolTests [ThreadPoolTests.java:99] This is Spring TaskScheduler
```

输出效果和刚才的JDK定时线程池一致

3. 简便调用Spring线程池的方法

a. 普通线程

在AlphaService新增：

```java
private static final Logger logger = LoggerFactory.getLogger(AlphaService.class);

// 线程池测试
@Async  // 让该方法在多线程的环境下被异步调用
public void execute1(){
  logger.debug("execute1");
}
```

Test方法新增：

```java
@Autowired
private AlphaService alphaService;

@Test
public void testEasyMethod(){
  // 相当于 Runnable 被封装在了 alphaService
  for(int i = 0; i < 10; i++){
    alphaService.execute1();
  }
  sleep(10000);
}
```

输出：

```
2024-05-21 15:59:55,463 DEBUG [task-1] c.n.c.s.AlphaService [AlphaService.java:126] execute1
2024-05-21 15:59:55,463 DEBUG [task-2] c.n.c.s.AlphaService [AlphaService.java:126] execute1
2024-05-21 15:59:55,463 DEBUG [task-3] c.n.c.s.AlphaService [AlphaService.java:126] execute1
2024-05-21 15:59:55,463 DEBUG [task-4] c.n.c.s.AlphaService [AlphaService.java:126] execute1
2024-05-21 15:59:55,463 DEBUG [task-2] c.n.c.s.AlphaService [AlphaService.java:126] execute1
2024-05-21 15:59:55,463 DEBUG [task-4] c.n.c.s.AlphaService [AlphaService.java:126] execute1
2024-05-21 15:59:55,463 DEBUG [task-5] c.n.c.s.AlphaService [AlphaService.java:126] execute1
2024-05-21 15:59:55,463 DEBUG [task-3] c.n.c.s.AlphaService [AlphaService.java:126] execute1
2024-05-21 15:59:55,463 DEBUG [task-1] c.n.c.s.AlphaService [AlphaService.java:126] execute1
2024-05-21 15:59:55,464 DEBUG [task-2] c.n.c.s.AlphaService [AlphaService.java:126] execute1
```

可见也是多线程的

b. 定时任务线程

在AlphaService新增：

```java
@Scheduled(initialDelay = 10000, fixedRate = 1000)  // 相当于在配置taskScheduler
public void execute2(){
    logger.debug("execute2");
}
```

在测试方法新增：

```java
public @Test void testEasyMethod2(){
    // 方法会被自动调
    sleep(30000);
}
```

可见输出与之前的定时任务效果一致

```
2024-05-21 16:04:54,371 DEBUG [scheduling-1] c.n.c.s.AlphaService [AlphaService.java:132] execute2
2024-05-21 16:04:55,356 DEBUG [scheduling-1] c.n.c.s.AlphaService [AlphaService.java:132] execute2
......
2024-05-21 16:05:13,366 DEBUG [scheduling-2] c.n.c.s.AlphaService [AlphaService.java:132] execute2
2024-05-21 16:05:14,366 DEBUG [scheduling-2] c.n.c.s.AlphaService [AlphaService.java:132] execute2
```

## 分布式定时任务：Quartz

0. Quartz核心组件简介

核心接口：中央调度器Scheduler

任务定义：Job接口

任务怎么做：配置Job--JobDetail接口  // 触发器Trigger -- Job什么时候、什么频率运行

配置信息会初始化到sql表里

1. 创建DB表：使用脚本tables_mysql_innodb.sql

```bash
C:\Users\15170>mysql -uroot -p
Enter password: ******
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 181
Server version: 8.3.0 MySQL Community Server - GPL

Copyright (c) 2000, 2024, Oracle and/or its affiliates.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> use community;
Database changed
mysql> source C:\Users\15170\Desktop\community\scripts\tables_mysql_innodb.sql
Query OK, 0 rows affected (0.00 sec)

Query OK, 0 rows affected (0.00 sec)

Query OK, 0 rows affected (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.01 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected, 1 warning (0.00 sec)

Query OK, 0 rows affected (0.07 sec)

Query OK, 0 rows affected, 5 warnings (0.03 sec)

Query OK, 0 rows affected, 3 warnings (0.02 sec)

Query OK, 0 rows affected (0.02 sec)

Query OK, 0 rows affected (0.03 sec)

Query OK, 0 rows affected (0.03 sec)

Query OK, 0 rows affected (0.02 sec)

Query OK, 0 rows affected (0.02 sec)

Query OK, 0 rows affected, 2 warnings (0.02 sec)

Query OK, 0 rows affected, 2 warnings (0.02 sec)

Query OK, 0 rows affected (0.02 sec)

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.01 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.03 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.04 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.05 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.02 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.01 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.02 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.01 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.04 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.04 sec)
Records: 0  Duplicates: 0  Warnings: 0

Query OK, 0 rows affected (0.00 sec)
```

导包：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

2. 新建了11张以qrtz_开头的表，我们主要关注：

a. qrtz_job_details -- job配置信息存储表

b. qrtz_simple_triggers -- 触发器信息存储表（简要）

c. qrtz_triggers -- 触发器信息存储表（完整）

d. qrtz_scheduler_state -- 定时器的所有状态

e. qrtz_locks -- 锁的相关信息

### 例子

1. 新建软件包quartz
2. 在包下创建示例AlphaJob

```java
package com.nowcoder.community.quartz;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class AlphaJob implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        System.out.println(Thread.currentThread().getName() + ": executed a Quartz Job.");
    }
}
```

3. 配置Job

在config下新建配置类QuartzConfig

```java
package com.nowcoder.community.config;

import com.nowcoder.community.quartz.AlphaJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;

// 配置 ——> 初始化到数据库，以后Quartz仅访问数据库不访问本配置文件
@Configuration
public class QuartzConfig {
    
    // FactoryBean 和 BeanFactory（IoC）：后者是IoC的容器顶层接口，而前者可简化Bean的实例化过程
    // ex：JobDetailFactoryBean 封装了JobDetail的详细实例化过程
    // 1.Spring通过FactoryBean封装了Bean的实例化过程
    // 2.我们可以将FactoryBean装配到Spring容器里，注入给其他bean，其他bean得到的是FactoryBean所管理的对象实例
    
    // 配置JobDetail
    @Bean
    public JobDetailFactoryBean alphaJobDetail(){
        JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(AlphaJob.class);
        factoryBean.setName("alphaJob");  // 名称是唯一的
        factoryBean.setGroup("alphaJobGroup");
        factoryBean.setDurability(true);  // 是持久保存的
        factoryBean.setRequestsRecovery(true); // 任务是可恢复的（redo）
        return factoryBean;
    }
    
    // 配置Trigger（SimpleTriggerFactoryBean or CronTriggerFactoryBean，前者简单后者复杂）
    @Bean
    public SimpleTriggerFactoryBean alphaTrigger(JobDetail alphaJobDetail){
        SimpleTriggerFactoryBean factoryBean = new SimpleTriggerFactoryBean();
        factoryBean.setJobDetail(alphaJobDetail);  // 是谁的触发器
        factoryBean.setName("alphaTrigger");
        factoryBean.setGroup("alphaTriggerGroup");
        factoryBean.setRepeatInterval(3000); // 频率：3s
        factoryBean.setJobDataMap(new JobDataMap()); // 存Job的状态，用默认的JobDataMap类型
        return factoryBean;
    }
}
```

现在启动服务后能正常运行，但是数据库中并没有数据，因为默认读取内存，还需要自己配置持久化到数据库

| **属性** | **FactoryBean** | **BeanFactory** |
|---|---|---|
| **定义** | `FactoryBean` 是Spring提供的一个特殊的bean，可以定制复杂bean的创建过程。 | `BeanFactory` 是Spring IoC容器的顶层接口，负责管理bean的生命周期。 |
| **作用** | 用于封装和定制bean的实例化逻辑，简化复杂bean的创建过程。 | 作为Spring容器的核心接口，负责创建、管理和配置bean。 |
| **实现方式** | 实现 `FactoryBean<T>` 接口，需要实现 `getObject()`, `getObjectType()`, `isSingleton()` 方法。 | 实现类包括 `DefaultListableBeanFactory`, `XmlBeanFactory` 等，提供标准的bean管理功能。 |
| **返回类型** | 返回由 `getObject()` 方法创建的实际bean实例。 | 直接管理和返回配置的bean实例。 |
| **用法** | 将 `FactoryBean` 实例注册到Spring容器中，其他bean通过 `FactoryBean` 获取实际的bean实例。 | 通过配置文件或注解定义bean，然后由容器管理其生命周期。 |
| **示例** | `JobDetailFactoryBean` 用于创建Quartz的 `JobDetail` 实例。 | `XmlBeanFactory` 通过读取XML配置文件创建和管理bean。 |
| **复杂性** | 适用于需要复杂实例化逻辑的bean。 | 适用于大多数标准bean的管理和实例化。 |
| **实例获取** | 通过 `&beanName` 获取 `FactoryBean` 实例，通过 `beanName` 获取 `FactoryBean` 创建的实际bean实例。 | 通过 `beanName` 直接获取bean实例。 |
| **生命周期** | `FactoryBean` 本身也是一个bean，由Spring容器管理其生命周期。 | 管理所有bean的生命周期，包括创建、初始化、销毁等。 |


4. quartz的持久化相关配置：

```
# Quartz
spring.quartz.job-store-type=jdbc  # 存储方式
spring.quartz.scheduler-name=communityScheduler  # 调度器的名字
spring.quartz.properties.org.quartz.scheduler.instanced=AUTO # 调度器id自动生成
spring.quartz.properties.org.quartz.jobStore.class=org.springframework.scheduling.quartz.LocalDataSourceJobStore  # 存进数据库的类
spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate  # jdbc驱动
spring.quartz.properties.org.quartz.jobStore.isClustered=true  # 采用集群方式
spring.quartz.properties.org.quartz.threadPool.class=org.quartz.simpl.SimpleThreadPool  # 用哪个线程池
spring.quartz.properties.org.quartz.threadPool.threadCount=5 # 线程数量
```

现在，一启动服务，本配置类的信息就会被加载到数据库，数据库里一旦有了数据，中央调度器Scheduler就会根据数据进行调度

输出：

```
communityScheduler_Worker-2: executed a Quartz Job.
communityScheduler_Worker-3: executed a Quartz Job.
communityScheduler_Worker-4: executed a Quartz Job.
2024-05-21 16:52:27,219 DEBUG [scheduling-1] c.n.c.s.AlphaService [AlphaService.java:132] execute2
communityScheduler_Worker-5: executed a Quartz Job.
2024-05-21 16:52:28,208 DEBUG [scheduling-1] c.n.c.s.AlphaService [AlphaService.java:132] execute2
2024-05-21 16:52:29,208 DEBUG [scheduling-2] c.n.c.s.AlphaService [AlphaService.java:132] execute2
2024-05-21 16:52:30,208 DEBUG [scheduling-1] c.n.c.s.AlphaService [AlphaService.java:132] execute2
communityScheduler_Worker-1: executed a Quartz Job.
```

现在数据库中也有相关的数据了

5. 删除测试任务

新建测试类QuartzTests

```java
package com.nowcoder.community;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class QuartzTests {

    // 删除job

    @Autowired
    private Scheduler scheduler;

    @Test
    public void testDeleteJob() throws SchedulerException {
        boolean result = scheduler.deleteJob(new JobKey("alphaJob", "alphaJobGroup"));
        System.out.println(result);
    }
}
```

现在数据库中除了Scheduler外的数据都没有了

但是下次启动服务时，还是会再度输出我们本节课的测试信息，现做如下解决：把配置类的`@Bean`注解注释掉即可