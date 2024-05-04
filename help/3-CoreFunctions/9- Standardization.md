# 统一处理异常 —— 针对Controller组件

## 前端——给用户的样式显示
错误文件的规范：HTML文件以错误码（404，500等）命名，放在 resources/templates/error下

记得对相应的文件进行thymeleaf的配置：

404.html:

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

<div class="container pl-5 pr-5 pt-3 pb-3 mt-3 mb-3" style="text-align: center">
<img th:src="@{/img/404.png}" >

<script th:src="@{/js/global.js}"></script>
```

500.html:

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

<div class="container pl-5 pr-5 pt-3 pb-3 mt-3 mb-3" style="text-align: center">
<img th:src="@{/img/error.png}" >

<script th:src="@{/js/global.js}"></script>
```

## 后端——Controller处理异常

由于Controller会调Service，Service会调Dao，所以底层的异常会全部上抛，即Controller接收所有的异常

- `@ControllerAdvice`
  - 用于修饰类，表示该类是Controller的全局配置类
  - 可以对Controller进行三种配置：异常处理方案，绑定数据方案，绑定参数方案
- `@ExceptionHandler`
  - 用于修饰方法，该方法会在Controller出现异常后被立即调用，用于处理捕获到的异常
- `@ModelAttribute`
  - 用于修饰方法，该方法会在Controller执行前被调用，用于为Model对象绑定统一参数
- `@DataBinder`
  - 用于修饰方法，该方法会在Controller执行前被调用，用于绑定参数的转换器

### 代码实现

1. 在HomeController新增：

```java
    // 重定向到错误页面
    @RequestMapping(path = "/error", method = RequestMethod.GET)
    public String getErrorPage(){
        return "/error/500";
    }
```

2. 声明Controller配置类：

```java
package com.nowcoder.community.controller.advice;

import com.nowcoder.community.util.CommunityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;
import java.io.PrintWriter;


@ControllerAdvice(annotations = Controller.class)  // 仅针对带有Controller注解的bean
public class ExceptionAdvice {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionAdvice.class);

    @ExceptionHandler({Exception.class})   // Exception是所有异常的父类，这里即表示处理所有异常
    public void handleException(Exception e, HttpServletResponse response, HttpServletRequest request) throws IOException {
        logger.error("服务器发送异常：" + e.getMessage());  // 简略记下错误
        for(StackTraceElement element : e.getStackTrace()){
            logger.error(element.toString());   // 详细记下每一条错误信息
        }

        // 判断：普通请求返回500.html，异步请求返回json/xml等数据
        String xRequestedWith = request.getHeader("x-requested-with");
        if("XMLHttpRequest".equals(xRequestedWith)){
            // 是异步请求
            response.setContentType("application/plain;charset=utf-8");  // 返回普通字符串（但我们知道这是json），由前端 $.parseJSON手动转成json格式
            PrintWriter pw = response.getWriter();
            pw.write(CommunityUtil.getJSONString(500, "服务器异常！"));
        }else {
            response.sendRedirect(request.getContextPath() + "/error");  // 重定向到 HomeController的 /error
        }
    }
}
```

# 统一记录日志  —— 针对Service组件

<b>不发生异常也要记录日志</b>

但是日志记录不应该只在service里，日志记录不只是业务需求，更是系统需求

## AOP

- Aspect Oriented Programming，面向切面编程（横向编程）
- AOP是一种编程思想，是对OOP的补充，可以进一步提高编程效率

![aop](/imgs/aop.png)

由图，可以类比金融时序分析的横截面数据

### AOP术语

1. 处理的业务组件——target

2. 面向切面——所有的代码都封装在aspect里
- aspect需要：
  - 1）声明切点（利用表达式），即具体要织入到哪些对象的哪些位置 `where`
  - 2）声明通知（具体系统逻辑），即具体织入逻辑  `how`

3. aspect和target交互——织入

4. 织入越早（越原始），速度越快，但是有些特殊情况处理不是很精细
- 织入点——joinPoint

![aop术语](/imgs/aop1.png)

### AOP的实现

- AspectJ（全面）
  - 语言级的实现，扩展了JAVA的语法（新定义了AOP语法）
  - 在编译期织入代码，有一个专门的编译器，用来生成遵守JAVA字节码规范的class文件
- SpringAOP（不全面，但是性价比最高）
  - 纯Java实现，不需要专门的编译过程，也不需要特殊的类装载器
  - 在运行时通过代理的方式织入代码，只支持方法类型的连接点
  - 支持对Aspect的集成——SpringAOP解决不了了再上AspectJ

### SpringAOP

代理：为调用对象生成一个代理对象，操作基于代理对象（副本）

- JDK动态代理
  - JAVA提供动态代理技术，可以在运行时创建接口的代理实例
  - Spring AOP默认采用这种方式，在接口的代理实例中织入代码
- CGLib动态代理
  - 采用底层的字节码技术，在运行时创建子类代理实例
  - 当目标对象不存在接口时，Spring AOP会采用本方法，在子类实例中织入代码

## 代码实现

1. 新建软件包aspect

引入依赖：

```xml
<dependency>
  <groupId>org.aspectj</groupId>
  <artifactId>aspectjweaver</artifactId>
  <version>1.9.7</version>
</dependency>
```

2. 示例：

```java
package com.nowcoder.community.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class AlphaAspect {

    // 空方法即可，在注解里声明where
    @Pointcut("execution (* com.nowcoder.community.service.*.*(..))")
    // 处理：        接受所有的返回值      service下所有类 的所有方法 的所有的参数
    public void pointcut(){}

    // 注解声明when

    @Before("pointcut()")  //在连接点开始时织入
    public void before(){
        System.out.println("before");
    }

    @After("pointcut()")   //在连接点之后
    public void after(){
        System.out.println("after");
    }

    @AfterReturning("pointcut()")  //在返回值以后
    public void afterReturning(){
        System.out.println("afterReturning");
    }

    @AfterThrowing("pointcut()")  // 在抛异常之后
    public void afterThrowing(){
        System.out.println("afterThrowing");
    }

    @Around("pointcut()")   // 既在前面，也在之后织入
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{   //参数为连接点
        System.out.println("around before");
        Object obj = joinPoint.proceed();   // 调用目标组件的方法
        System.out.println("around after");
        return  obj;
    }
}
```

3. 测试，可以看到针对某一个service方法，控制台输出：

```bash
around before
before
2024-05-04 20:28:24,525 DEBUG [http-nio-8080-exec-1] c.n.c.d.U.selectById [BaseJdbcLogger.java:135] ==>  Preparing: select id, username, password, salt, email, type, status, activation_code, header_url, create_time from user where id = ?
2024-05-04 20:28:24,525 DEBUG [http-nio-8080-exec-1] c.n.c.d.U.selectById [BaseJdbcLogger.java:135] ==> Parameters: 153(Integer)
2024-05-04 20:28:24,527 DEBUG [http-nio-8080-exec-1] c.n.c.d.U.selectById [BaseJdbcLogger.java:135] <==      Total: 1
afterReturning
after
around after
```

4. 统一记录日志
- 时期：before
- 格式：用户（IP地址） + 某时刻 + 访问了某功能

```java
package com.nowcoder.community.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
@Aspect
public class ServiceLogAspect {
    private static final Logger logger = LoggerFactory.getLogger(ServiceLogAspect.class);

    @Pointcut("execution (* com.nowcoder.community.service.*.*(..))")  // 对所有业务记录日志
    public void pointcut(){}

    @Before("pointcut()")
    public void before(JoinPoint joinPoint){
        // 用户 1.2.3.4 在 xxx 时间 访问了 xxx 方法
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getRemoteHost();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String target = joinPoint.getSignature().getDeclaringTypeName() + "." +
                joinPoint.getSignature().getName();  // 类名.方法名
        logger.info(String.format("用户 [%s] 在 [%s] 访问了 [%s]", ip, now, target));
    }
}
```

可以发现输出：
```
2024-05-04 20:48:28,180 INFO [http-nio-8080-exec-1] c.n.c.a.ServiceLogAspect [ServiceLogAspect.java:34] 用户 [127.0.0.1] 在 [2024-05-04 20:48:28] 访问了 [com.nowcoder.community.service.UserService.findUserById]
```