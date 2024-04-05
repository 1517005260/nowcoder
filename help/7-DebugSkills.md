# 项目调试技巧

## 常见[响应状态码](https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Status)的含义

1. 以`2`开头的都表示成功，最常见的是`200 OK`

2. 以`3`开头的都表示重定向，最常见的是`302 Found`
- 什么是`重定向`：比如客户端向服务器发请求删除数据，删除成功后返回的列表应该是查询“删完的列表”的页面<br>
  一种解决方法是 请求 -> 删除 -> 查询 -> 返回<br>
  ![no_redirect](/imgs/noRedirect.png)
  <br>
  但是这种方法有个问题：删除和查询本来是两个独立无耦合的功能，而经上述操作会将两个功能耦合在一起，当日后功能复杂后若出现问题会排查复杂<br>
  因此，我们引入了<b>重定向</b>：请求 -> 删除 -> 返回`302 + 建议访问地址（这里是查询的网页地址）` -> 浏览器访问查询页面 -> 返回结果<br>
  ![Redirection](/imgs/Redirection.png)
  <br>

3. 以`4`开头的是客户端报错，最常见的是`404 Not Found`:要访问的功能不存在（一般是链接路径错误）

4. 以`5`开头的是服务器报错，服务器在处理逻辑上有问题


==> 报错后，到对应的地方（服务端/客户端）进行断点调试

## 断点调试技巧

### 服务端

在java方法内部打断点，进行debug测试

![server_debug](/imgs/serverDebug.png)

- 启动服务后访问`localhost:8080:community/index`,在后台看见程序停止在断点处
  - F8：向下执行一行程序
  - F7：进入当前行所调用的方法
  - F9：跳到下一个断点，如果没有断点，则正常执行程序
### 客户端

即调试js
- 在网页端f12 + 进入js文件打断点

![client_debug](/imgs/clientDebug.png)

- 跟后端调试的三个功能对应的快捷键：F10，F11，F8

## 设置日志级别，并将日志输出到不同的终端——直观
优先于断点调试，但是比较麻烦

Spring Boot 默认内置日志记录工具：[logback](https://logback.qos.ch/manual/architecture.html)
- 使用了logger接口，级别递增：

```java
package org.slf4j; 
public interface Logger {

  // Printing methods: 
  public void trace(String message);
  public void debug(String message);  //开发调试
  public void info(String message);   //正常信息
  public void warn(String message); 
  public void error(String message);   //异常
}
// 比如启动info级别后，debug和trace级别会被忽视，只会输出剩下3级，一般最低级是debug，trace一般不用记
```

- 可以在配置文件里修改log等级：`logging.level.com.nowcoder.community = debug`，表示community文件夹下所有log为debug级

- 新建测试类测试logger：

```java
package com.nowcoder.community;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class LoggerTests {

    //static 可供所有类实例访问，final指定不可修改
    private static final Logger logger = LoggerFactory.getLogger(LoggerTests.class);

    @Test
    public void testLogger(){
        System.out.println(logger.getName());

        logger.debug("debug log");
        logger.info("info log");
        logger.warn("warn log");
        logger.error("error log");
    }
}

```

输出:

```bash
com.nowcoder.community.LoggerTests
2024-04-05T20:31:40.974+08:00 DEBUG 15344 --- [community] [           main] com.nowcoder.community.LoggerTests       : debug log
2024-04-05T20:31:40.974+08:00  INFO 15344 --- [community] [           main] com.nowcoder.community.LoggerTests       : info log
2024-04-05T20:31:40.974+08:00  WARN 15344 --- [community] [           main] com.nowcoder.community.LoggerTests       : warn log
2024-04-05T20:31:40.974+08:00 ERROR 15344 --- [community] [           main] com.nowcoder.community.LoggerTests       : error log
```

- 日志可持久化：在配置文件增加`logging.file.name={文件路径}`即可。
- 分级保存日志（filter），以及日志的超过5M就新建一个文件保存的功能（rolling-policy）：在`src/main/resources`下新建xml文件`logback-spring.xml`（即与配置文件同级，注意文件名不能改），详见官方[配置文件文档](https://logback.qos.ch/manual/configuration.html)

例子：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <contextName>community</contextName>
    <property name="LOG_PATH" value="D:/work/data"/>
    <property name="APPDIR" value="community"/>

    <!-- error file -->
    <appender name="FILE_ERROR" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${APPDIR}/log_error.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/${APPDIR}/error/log-error-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>5MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <append>true</append>
        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
            <pattern>%d %level [%thread] %logger{10} [%file:%line] %msg%n</pattern>
            <charset>utf-8</charset>
        </encoder>
        <filter class="ch.qos.logback.classic.filter.LevelFilter">
            <level>error</level>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
    </appender>
  
    <logger name="com.nowcoder.community" level="debug"/>

    <root level="info">
        <appender-ref ref="FILE_ERROR"/>
        <appender-ref ref="FILE_WARN"/>
        <appender-ref ref="FILE_INFO"/>
        <appender-ref ref="STDOUT"/>
    </root>

</configuration>
```
