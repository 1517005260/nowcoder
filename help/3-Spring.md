# [Spring](https://spring.io/)入门

## Spring全家桶
- Spring Framework：框架，是基石和核心
- Spring Boot：构建项目
- Spring Cloud：微服务，把大项目拆分成若干便于维护的小项目，再集成
- Spring Cloud Data Flow：数据集成

本项目着重用前两个技术

### Spring Framework
- Spring Core <b>核心</b>
    - IoC、AOP：面向对象、面向切面——管理对象`Bean`（类的不同时期）的思想
- Spring Data Access <b>管理数据库、事物</b>
    - Transactions、Spring MyBatis
- Web Servlet    <b>开发web</b>
    - Spring MVC
- Integration    <b>集成</b>
    - Email、Scheduling、AMQP、Security

### Spring IoC
- Inversion of Control：控制反转（减少耦合），是一种面向对象编程的设计思想
- Dependency Injection：依赖注入，是实现IoC思想的方式
- IoC Container：Ioc容器，是实现依赖注入的关键，本质上是一个工厂
  - 如下图，在给定不同的需要管理的类和相应的配置文件之后，container会自动为你打包可使用的类，即类是在高维相关的，类之间减少了耦合
    ![图解IoC](/imgs/IoC.png)

#### 代码实现——容器管理bean的基本方式：创建与获取
1. 有关上节课的代码`SpringApplication.run(CommunityApplication.class, args);`:
- 运行的时候是自动帮我们启动了Tomcat（免费的web服务器）和Spring容器
- 容器启动后，会自动扫描配置类`CommunityApplication`以及子包下的Bean（须有@Controller/Service/Component/Repository注解，即由@Component实现的注解，因业务不同可以选择）
- 注解就是嵌入在代码中的补充信息，是处于程序和注释的中间态
2. 在测试代码`/src/test`下演示
- 与主程序下的配置类关联：`@ContextConfiguration(classes = CommunityApplication.class)`
- 为了得到自动创建的Spring容器，需要实现接口如下所示，`ApplicationContext`就是容器（接口）：
```java
class CommunityApplicationTests implements ApplicationContextAware {
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    }
}
```
我们测试的方法就是直接输出这个容器：
```java
package com.nowcoder.community;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
class CommunityApplicationTests implements ApplicationContextAware {
	private ApplicationContext applicationContext;
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Test
	public void testApplicationContext(){
		System.out.println(applicationContext);
	}
}

```
输出如下，`GenericWebApplicationContext`就是实现的类名：
```bash
org.springframework.web.context.support.GenericWebApplicationContext@34a75079, started on Sat Mar 30 11:57:46 CST 2024
```
3. 使用这个容器来管理`Bean`
- 在主程序下新建一个`Bean`，我们可以新建一个包dao（data access object），专门存放用来访问数据库的`Bean`
- 新建接口`AlphaDao`和实现类`AlphaDaoHibernateImpl`用于演示
```java
package com.nowcoder.community.dao;

import org.springframework.stereotype.Repository;
//数据库注解
@Repository
public class AlphaDaoHibernateImpl implements AlphaDao{
    @Override
    public String select() {
        return "Hibernate";
    }
}
```
- 开始测试
```java
@Test
public void testApplicationContext(){
    System.out.println(applicationContext);

    //获取容器中自动装配的Bean
  AlphaDao alphaDao = applicationContext.getBean(AlphaDao.class);  //从容器中获得指定类型的bean
  System.out.println(alphaDao.select());  //调用查询方法并输出
}
```
输出：
```bash
org.springframework.web.context.support.GenericWebApplicationContext@34a75079, started on Sat Mar 30 12:15:17 CST 2024
Hibernate
```
4. 体会这么写的优势
- 假如说，你写项目的某一天，Mybatis诞生了，想要把Hibernate换成Mybatis
- 我们不用删除原来的`AlphaDaoHibernateImpl`，直接新建
```java
package com.nowcoder.community.dao;

import org.springframework.stereotype.Repository;

@Repository
public class AlphaDaoMybatisImpl implements AlphaDao{
    @Override
    public String select(){
        return "MyBatis";
    }
}
```
此时这行代码运行时会有问题：`AlphaDao alphaDao = applicationContext.getBean(AlphaDao.class);`
<br>
因为我们按照AlphaDao获取Bean，而有两个Bean都符合条件（AlphaDaoHibernateImpl和AlphaDaoMybatisImpl），存在了歧义<br>
此时只要期望获取的Bean上加注解`@Primary`即可<br>
所以改完的代码为：
```java
package com.nowcoder.community.dao;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class AlphaDaoMybatisImpl implements AlphaDao{
    @Override
    public String select(){
        return "MyBatis";
    }
}
```
运行测试方法，输出：
```bash
org.springframework.web.context.support.GenericWebApplicationContext@34a75079, started on Sat Mar 30 12:28:56 CST 2024
MyBatis
```
==> 原来之前程序中的Dao类都是由Hibernate方法实现的，我们调用Bean的地方会很多。如果需要批量替换，新建类加注解即可，调用的地方完全不用改变（依赖的是接口而不是bean）非常方便
<br>
<b>面向接口的编程思想</b>：降低耦合度
5. 新的问题出现了：如果有个奇葩需求，说我有部分地方需要用到原来的Hibernate技术，怎么办？`AlphaDao alphaDao = applicationContext.getBean(AlphaDao.class);`这行代码只能捕捉所有的Mybatis
- bean有默认名，就是类名的首字母小写，如果觉得名字长， 可以自定义，可以用注解更改：`@Repository("alphaHibernate")`
- 我们可以通过名字强制容器返回某个bean，代码如下：
```java
@Test
public void testApplicationContext(){
    System.out.println(applicationContext);

    //获取容器中自动装配的Bean
    AlphaDao alphaDao = applicationContext.getBean(AlphaDao.class);  //从容器中获得指定类型的bean    
    System.out.println(alphaDao.select());  //调用查询方法并输出
		
    alphaDao = applicationContext.getBean("alphaHibernate", AlphaDao.class);
    System.out.println(alphaDao.select());
}
```
输出：
```bash
org.springframework.web.context.support.GenericWebApplicationContext@34a75079, started on Sat Mar 30 12:39:34 CST 2024
MyBatis
Hibernate
```


#### 代码实现——容器管理bean的基本方式：初始化和销毁
1. 新建业务service包
- 新建例子类AlphaService，并加上可供容器访问的业务注解
- 增加构造、初始化、销毁方法
```java
package com.nowcoder.community.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

@Service
public class AlphaService {
    
    //构造
    public AlphaService(){
        System.out.println("实例化AlphaService");
    }
    
    //初始化——在构造之后调用
    @PostConstruct
    public void init(){
        System.out.println("初始化AlphaService");
    }
    
    //销毁之前调用 on_destroy
    @PreDestroy
    public void destroy(){
        System.out.println("销毁AlphaService");
    }
}
```
2. 再新写一个测试方法管理bean：
```java
@Test
public void testBeanManagement(){
    AlphaService alphaService = applicationContext.getBean(AlphaService.class);
    System.out.println(alphaService);
}
```
观察控制台的输出：
```bash
C:\Users\15170\.jdks\corretto-17.0.10\bin\java.exe -ea -Didea.test.cyclic.buffer.size=1048576 "-javaagent:D:\Program Files\IntelliJ IDEA 2023.3.4\lib\idea_rt.jar=63385:D:\Program Files\IntelliJ IDEA 2023.3.4\bin" -Dfile.encoding=UTF-8 -classpath "C:\Users\15170\.m2\repository\org\junit\platform\junit-platform-launcher\1.10.2\junit-platform-launcher-1.10.2.jar;D:\Program Files\IntelliJ IDEA 2023.3.4\lib\idea_rt.jar;D:\Program Files\IntelliJ IDEA 2023.3.4\plugins\junit\lib\junit5-rt.jar;D:\Program Files\IntelliJ IDEA 2023.3.4\plugins\junit\lib\junit-rt.jar;C:\Users\15170\Desktop\community\target\test-classes;C:\Users\15170\Desktop\community\target\classes;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-thymeleaf\3.2.4\spring-boot-starter-thymeleaf-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter\3.2.4\spring-boot-starter-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-logging\3.2.4\spring-boot-starter-logging-3.2.4.jar;C:\Users\15170\.m2\repository\ch\qos\logback\logback-classic\1.4.14\logback-classic-1.4.14.jar;C:\Users\15170\.m2\repository\ch\qos\logback\logback-core\1.4.14\logback-core-1.4.14.jar;C:\Users\15170\.m2\repository\org\apache\logging\log4j\log4j-to-slf4j\2.21.1\log4j-to-slf4j-2.21.1.jar;C:\Users\15170\.m2\repository\org\apache\logging\log4j\log4j-api\2.21.1\log4j-api-2.21.1.jar;C:\Users\15170\.m2\repository\org\slf4j\jul-to-slf4j\2.0.12\jul-to-slf4j-2.0.12.jar;C:\Users\15170\.m2\repository\jakarta\annotation\jakarta.annotation-api\2.1.1\jakarta.annotation-api-2.1.1.jar;C:\Users\15170\.m2\repository\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar;C:\Users\15170\.m2\repository\org\thymeleaf\thymeleaf-spring6\3.1.2.RELEASE\thymeleaf-spring6-3.1.2.RELEASE.jar;C:\Users\15170\.m2\repository\org\thymeleaf\thymeleaf\3.1.2.RELEASE\thymeleaf-3.1.2.RELEASE.jar;C:\Users\15170\.m2\repository\org\attoparser\attoparser\2.0.7.RELEASE\attoparser-2.0.7.RELEASE.jar;C:\Users\15170\.m2\repository\org\unbescape\unbescape\1.1.6.RELEASE\unbescape-1.1.6.RELEASE.jar;C:\Users\15170\.m2\repository\org\slf4j\slf4j-api\2.0.12\slf4j-api-2.0.12.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-web\3.2.4\spring-boot-starter-web-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-json\3.2.4\spring-boot-starter-json-3.2.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.15.4\jackson-databind-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.15.4\jackson-annotations-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.15.4\jackson-core-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\datatype\jackson-datatype-jdk8\2.15.4\jackson-datatype-jdk8-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\datatype\jackson-datatype-jsr310\2.15.4\jackson-datatype-jsr310-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\module\jackson-module-parameter-names\2.15.4\jackson-module-parameter-names-2.15.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-tomcat\3.2.4\spring-boot-starter-tomcat-3.2.4.jar;C:\Users\15170\.m2\repository\org\apache\tomcat\embed\tomcat-embed-core\10.1.19\tomcat-embed-core-10.1.19.jar;C:\Users\15170\.m2\repository\org\apache\tomcat\embed\tomcat-embed-el\10.1.19\tomcat-embed-el-10.1.19.jar;C:\Users\15170\.m2\repository\org\apache\tomcat\embed\tomcat-embed-websocket\10.1.19\tomcat-embed-websocket-10.1.19.jar;C:\Users\15170\.m2\repository\org\springframework\spring-web\6.1.5\spring-web-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-beans\6.1.5\spring-beans-6.1.5.jar;C:\Users\15170\.m2\repository\io\micrometer\micrometer-observation\1.12.4\micrometer-observation-1.12.4.jar;C:\Users\15170\.m2\repository\io\micrometer\micrometer-commons\1.12.4\micrometer-commons-1.12.4.jar;C:\Users\15170\.m2\repository\org\springframework\spring-webmvc\6.1.5\spring-webmvc-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-aop\6.1.5\spring-aop-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-context\6.1.5\spring-context-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-expression\6.1.5\spring-expression-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-web-services\3.2.4\spring-boot-starter-web-services-3.2.4.jar;C:\Users\15170\.m2\repository\com\sun\xml\messaging\saaj\saaj-impl\3.0.3\saaj-impl-3.0.3.jar;C:\Users\15170\.m2\repository\jakarta\xml\soap\jakarta.xml.soap-api\3.0.1\jakarta.xml.soap-api-3.0.1.jar;C:\Users\15170\.m2\repository\org\jvnet\staxex\stax-ex\2.1.0\stax-ex-2.1.0.jar;C:\Users\15170\.m2\repository\jakarta\activation\jakarta.activation-api\2.1.3\jakarta.activation-api-2.1.3.jar;C:\Users\15170\.m2\repository\org\eclipse\angus\angus-activation\2.0.2\angus-activation-2.0.2.jar;C:\Users\15170\.m2\repository\jakarta\xml\ws\jakarta.xml.ws-api\4.0.1\jakarta.xml.ws-api-4.0.1.jar;C:\Users\15170\.m2\repository\org\springframework\spring-oxm\6.1.5\spring-oxm-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\ws\spring-ws-core\4.0.10\spring-ws-core-4.0.10.jar;C:\Users\15170\.m2\repository\org\springframework\ws\spring-xml\4.0.10\spring-xml-4.0.10.jar;C:\Users\15170\.m2\repository\org\glassfish\jaxb\jaxb-runtime\4.0.5\jaxb-runtime-4.0.5.jar;C:\Users\15170\.m2\repository\org\glassfish\jaxb\jaxb-core\4.0.5\jaxb-core-4.0.5.jar;C:\Users\15170\.m2\repository\org\glassfish\jaxb\txw2\4.0.5\txw2-4.0.5.jar;C:\Users\15170\.m2\repository\com\sun\istack\istack-commons-runtime\4.1.2\istack-commons-runtime-4.1.2.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-devtools\3.2.4\spring-boot-devtools-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot\3.2.4\spring-boot-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-autoconfigure\3.2.4\spring-boot-autoconfigure-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-test\3.2.4\spring-boot-starter-test-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-test\3.2.4\spring-boot-test-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-test-autoconfigure\3.2.4\spring-boot-test-autoconfigure-3.2.4.jar;C:\Users\15170\.m2\repository\com\jayway\jsonpath\json-path\2.9.0\json-path-2.9.0.jar;C:\Users\15170\.m2\repository\jakarta\xml\bind\jakarta.xml.bind-api\4.0.2\jakarta.xml.bind-api-4.0.2.jar;C:\Users\15170\.m2\repository\net\minidev\json-smart\2.5.0\json-smart-2.5.0.jar;C:\Users\15170\.m2\repository\net\minidev\accessors-smart\2.5.0\accessors-smart-2.5.0.jar;C:\Users\15170\.m2\repository\org\ow2\asm\asm\9.3\asm-9.3.jar;C:\Users\15170\.m2\repository\org\assertj\assertj-core\3.24.2\assertj-core-3.24.2.jar;C:\Users\15170\.m2\repository\net\bytebuddy\byte-buddy\1.14.12\byte-buddy-1.14.12.jar;C:\Users\15170\.m2\repository\org\awaitility\awaitility\4.2.0\awaitility-4.2.0.jar;C:\Users\15170\.m2\repository\org\hamcrest\hamcrest\2.2\hamcrest-2.2.jar;C:\Users\15170\.m2\repository\org\junit\jupiter\junit-jupiter\5.10.2\junit-jupiter-5.10.2.jar;C:\Users\15170\.m2\repository\org\junit\jupiter\junit-jupiter-api\5.10.2\junit-jupiter-api-5.10.2.jar;C:\Users\15170\.m2\repository\org\opentest4j\opentest4j\1.3.0\opentest4j-1.3.0.jar;C:\Users\15170\.m2\repository\org\junit\platform\junit-platform-commons\1.10.2\junit-platform-commons-1.10.2.jar;C:\Users\15170\.m2\repository\org\apiguardian\apiguardian-api\1.1.2\apiguardian-api-1.1.2.jar;C:\Users\15170\.m2\repository\org\junit\jupiter\junit-jupiter-params\5.10.2\junit-jupiter-params-5.10.2.jar;C:\Users\15170\.m2\repository\org\junit\jupiter\junit-jupiter-engine\5.10.2\junit-jupiter-engine-5.10.2.jar;C:\Users\15170\.m2\repository\org\junit\platform\junit-platform-engine\1.10.2\junit-platform-engine-1.10.2.jar;C:\Users\15170\.m2\repository\org\mockito\mockito-core\5.7.0\mockito-core-5.7.0.jar;C:\Users\15170\.m2\repository\net\bytebuddy\byte-buddy-agent\1.14.12\byte-buddy-agent-1.14.12.jar;C:\Users\15170\.m2\repository\org\objenesis\objenesis\3.3\objenesis-3.3.jar;C:\Users\15170\.m2\repository\org\mockito\mockito-junit-jupiter\5.7.0\mockito-junit-jupiter-5.7.0.jar;C:\Users\15170\.m2\repository\org\skyscreamer\jsonassert\1.5.1\jsonassert-1.5.1.jar;C:\Users\15170\.m2\repository\com\vaadin\external\google\android-json\0.0.20131108.vaadin1\android-json-0.0.20131108.vaadin1.jar;C:\Users\15170\.m2\repository\org\springframework\spring-core\6.1.5\spring-core-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-jcl\6.1.5\spring-jcl-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-test\6.1.5\spring-test-6.1.5.jar;C:\Users\15170\.m2\repository\org\xmlunit\xmlunit-core\2.9.1\xmlunit-core-2.9.1.jar" com.intellij.rt.junit.JUnitStarter -ideVersion5 -junit5 com.nowcoder.community.CommunityApplicationTests,testBeanManagement
12:54:42.137 [main] INFO org.springframework.boot.devtools.restart.RestartApplicationListener -- Restart disabled due to context in which it is running

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.4)

2024-03-30T12:54:42.385+08:00  INFO 12376 --- [community] [           main] c.n.community.CommunityApplicationTests  : Starting CommunityApplicationTests using Java 17.0.10 with PID 12376 (started by 15170 in C:\Users\15170\Desktop\community)
2024-03-30T12:54:42.387+08:00  INFO 12376 --- [community] [           main] c.n.community.CommunityApplicationTests  : No active profile set, falling back to 1 default profile: "default"
2024-03-30T12:54:43.166+08:00  WARN 12376 --- [community] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2024-03-30T12:54:43.193+08:00  INFO 12376 --- [community] [           main] .w.s.a.s.AnnotationActionEndpointMapping : Supporting [WS-Addressing August 2004, WS-Addressing 1.0]
实例化AlphaService
初始化AlphaService
2024-03-30T12:54:43.736+08:00  WARN 12376 --- [community] [           main] ion$DefaultTemplateResolverConfiguration : Cannot find template location: classpath:/templates/ (please add some templates, check your Thymeleaf configuration, or set spring.thymeleaf.check-template-location=false)
2024-03-30T12:54:43.860+08:00  INFO 12376 --- [community] [           main] c.n.community.CommunityApplicationTests  : Started CommunityApplicationTests in 1.729 seconds (process running for 2.573)
com.nowcoder.community.service.AlphaService@1b46392c
销毁AlphaService

进程已结束，退出代码为 0
```
- 确实执行了“实例化——初始化——销毁”的步骤。而且我们可以发现：bean在整个容器创建到销毁的过程中只会被实例化一次，是单例的，因为main方法只有一个
<br>
再进行测试:
```java
@Test
public void testBeanManagement(){
    AlphaService alphaService = applicationContext.getBean(AlphaService.class);
    System.out.println(alphaService);

    alphaService = applicationContext.getBean(AlphaService.class);
    System.out.println(alphaService);
}
```
输出：
```bash
C:\Users\15170\.jdks\corretto-17.0.10\bin\java.exe -ea -Didea.test.cyclic.buffer.size=1048576 "-javaagent:D:\Program Files\IntelliJ IDEA 2023.3.4\lib\idea_rt.jar=63439:D:\Program Files\IntelliJ IDEA 2023.3.4\bin" -Dfile.encoding=UTF-8 -classpath "C:\Users\15170\.m2\repository\org\junit\platform\junit-platform-launcher\1.10.2\junit-platform-launcher-1.10.2.jar;D:\Program Files\IntelliJ IDEA 2023.3.4\lib\idea_rt.jar;D:\Program Files\IntelliJ IDEA 2023.3.4\plugins\junit\lib\junit5-rt.jar;D:\Program Files\IntelliJ IDEA 2023.3.4\plugins\junit\lib\junit-rt.jar;C:\Users\15170\Desktop\community\target\test-classes;C:\Users\15170\Desktop\community\target\classes;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-thymeleaf\3.2.4\spring-boot-starter-thymeleaf-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter\3.2.4\spring-boot-starter-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-logging\3.2.4\spring-boot-starter-logging-3.2.4.jar;C:\Users\15170\.m2\repository\ch\qos\logback\logback-classic\1.4.14\logback-classic-1.4.14.jar;C:\Users\15170\.m2\repository\ch\qos\logback\logback-core\1.4.14\logback-core-1.4.14.jar;C:\Users\15170\.m2\repository\org\apache\logging\log4j\log4j-to-slf4j\2.21.1\log4j-to-slf4j-2.21.1.jar;C:\Users\15170\.m2\repository\org\apache\logging\log4j\log4j-api\2.21.1\log4j-api-2.21.1.jar;C:\Users\15170\.m2\repository\org\slf4j\jul-to-slf4j\2.0.12\jul-to-slf4j-2.0.12.jar;C:\Users\15170\.m2\repository\jakarta\annotation\jakarta.annotation-api\2.1.1\jakarta.annotation-api-2.1.1.jar;C:\Users\15170\.m2\repository\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar;C:\Users\15170\.m2\repository\org\thymeleaf\thymeleaf-spring6\3.1.2.RELEASE\thymeleaf-spring6-3.1.2.RELEASE.jar;C:\Users\15170\.m2\repository\org\thymeleaf\thymeleaf\3.1.2.RELEASE\thymeleaf-3.1.2.RELEASE.jar;C:\Users\15170\.m2\repository\org\attoparser\attoparser\2.0.7.RELEASE\attoparser-2.0.7.RELEASE.jar;C:\Users\15170\.m2\repository\org\unbescape\unbescape\1.1.6.RELEASE\unbescape-1.1.6.RELEASE.jar;C:\Users\15170\.m2\repository\org\slf4j\slf4j-api\2.0.12\slf4j-api-2.0.12.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-web\3.2.4\spring-boot-starter-web-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-json\3.2.4\spring-boot-starter-json-3.2.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.15.4\jackson-databind-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.15.4\jackson-annotations-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.15.4\jackson-core-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\datatype\jackson-datatype-jdk8\2.15.4\jackson-datatype-jdk8-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\datatype\jackson-datatype-jsr310\2.15.4\jackson-datatype-jsr310-2.15.4.jar;C:\Users\15170\.m2\repository\com\fasterxml\jackson\module\jackson-module-parameter-names\2.15.4\jackson-module-parameter-names-2.15.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-tomcat\3.2.4\spring-boot-starter-tomcat-3.2.4.jar;C:\Users\15170\.m2\repository\org\apache\tomcat\embed\tomcat-embed-core\10.1.19\tomcat-embed-core-10.1.19.jar;C:\Users\15170\.m2\repository\org\apache\tomcat\embed\tomcat-embed-el\10.1.19\tomcat-embed-el-10.1.19.jar;C:\Users\15170\.m2\repository\org\apache\tomcat\embed\tomcat-embed-websocket\10.1.19\tomcat-embed-websocket-10.1.19.jar;C:\Users\15170\.m2\repository\org\springframework\spring-web\6.1.5\spring-web-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-beans\6.1.5\spring-beans-6.1.5.jar;C:\Users\15170\.m2\repository\io\micrometer\micrometer-observation\1.12.4\micrometer-observation-1.12.4.jar;C:\Users\15170\.m2\repository\io\micrometer\micrometer-commons\1.12.4\micrometer-commons-1.12.4.jar;C:\Users\15170\.m2\repository\org\springframework\spring-webmvc\6.1.5\spring-webmvc-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-aop\6.1.5\spring-aop-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-context\6.1.5\spring-context-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-expression\6.1.5\spring-expression-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-web-services\3.2.4\spring-boot-starter-web-services-3.2.4.jar;C:\Users\15170\.m2\repository\com\sun\xml\messaging\saaj\saaj-impl\3.0.3\saaj-impl-3.0.3.jar;C:\Users\15170\.m2\repository\jakarta\xml\soap\jakarta.xml.soap-api\3.0.1\jakarta.xml.soap-api-3.0.1.jar;C:\Users\15170\.m2\repository\org\jvnet\staxex\stax-ex\2.1.0\stax-ex-2.1.0.jar;C:\Users\15170\.m2\repository\jakarta\activation\jakarta.activation-api\2.1.3\jakarta.activation-api-2.1.3.jar;C:\Users\15170\.m2\repository\org\eclipse\angus\angus-activation\2.0.2\angus-activation-2.0.2.jar;C:\Users\15170\.m2\repository\jakarta\xml\ws\jakarta.xml.ws-api\4.0.1\jakarta.xml.ws-api-4.0.1.jar;C:\Users\15170\.m2\repository\org\springframework\spring-oxm\6.1.5\spring-oxm-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\ws\spring-ws-core\4.0.10\spring-ws-core-4.0.10.jar;C:\Users\15170\.m2\repository\org\springframework\ws\spring-xml\4.0.10\spring-xml-4.0.10.jar;C:\Users\15170\.m2\repository\org\glassfish\jaxb\jaxb-runtime\4.0.5\jaxb-runtime-4.0.5.jar;C:\Users\15170\.m2\repository\org\glassfish\jaxb\jaxb-core\4.0.5\jaxb-core-4.0.5.jar;C:\Users\15170\.m2\repository\org\glassfish\jaxb\txw2\4.0.5\txw2-4.0.5.jar;C:\Users\15170\.m2\repository\com\sun\istack\istack-commons-runtime\4.1.2\istack-commons-runtime-4.1.2.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-devtools\3.2.4\spring-boot-devtools-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot\3.2.4\spring-boot-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-autoconfigure\3.2.4\spring-boot-autoconfigure-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-starter-test\3.2.4\spring-boot-starter-test-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-test\3.2.4\spring-boot-test-3.2.4.jar;C:\Users\15170\.m2\repository\org\springframework\boot\spring-boot-test-autoconfigure\3.2.4\spring-boot-test-autoconfigure-3.2.4.jar;C:\Users\15170\.m2\repository\com\jayway\jsonpath\json-path\2.9.0\json-path-2.9.0.jar;C:\Users\15170\.m2\repository\jakarta\xml\bind\jakarta.xml.bind-api\4.0.2\jakarta.xml.bind-api-4.0.2.jar;C:\Users\15170\.m2\repository\net\minidev\json-smart\2.5.0\json-smart-2.5.0.jar;C:\Users\15170\.m2\repository\net\minidev\accessors-smart\2.5.0\accessors-smart-2.5.0.jar;C:\Users\15170\.m2\repository\org\ow2\asm\asm\9.3\asm-9.3.jar;C:\Users\15170\.m2\repository\org\assertj\assertj-core\3.24.2\assertj-core-3.24.2.jar;C:\Users\15170\.m2\repository\net\bytebuddy\byte-buddy\1.14.12\byte-buddy-1.14.12.jar;C:\Users\15170\.m2\repository\org\awaitility\awaitility\4.2.0\awaitility-4.2.0.jar;C:\Users\15170\.m2\repository\org\hamcrest\hamcrest\2.2\hamcrest-2.2.jar;C:\Users\15170\.m2\repository\org\junit\jupiter\junit-jupiter\5.10.2\junit-jupiter-5.10.2.jar;C:\Users\15170\.m2\repository\org\junit\jupiter\junit-jupiter-api\5.10.2\junit-jupiter-api-5.10.2.jar;C:\Users\15170\.m2\repository\org\opentest4j\opentest4j\1.3.0\opentest4j-1.3.0.jar;C:\Users\15170\.m2\repository\org\junit\platform\junit-platform-commons\1.10.2\junit-platform-commons-1.10.2.jar;C:\Users\15170\.m2\repository\org\apiguardian\apiguardian-api\1.1.2\apiguardian-api-1.1.2.jar;C:\Users\15170\.m2\repository\org\junit\jupiter\junit-jupiter-params\5.10.2\junit-jupiter-params-5.10.2.jar;C:\Users\15170\.m2\repository\org\junit\jupiter\junit-jupiter-engine\5.10.2\junit-jupiter-engine-5.10.2.jar;C:\Users\15170\.m2\repository\org\junit\platform\junit-platform-engine\1.10.2\junit-platform-engine-1.10.2.jar;C:\Users\15170\.m2\repository\org\mockito\mockito-core\5.7.0\mockito-core-5.7.0.jar;C:\Users\15170\.m2\repository\net\bytebuddy\byte-buddy-agent\1.14.12\byte-buddy-agent-1.14.12.jar;C:\Users\15170\.m2\repository\org\objenesis\objenesis\3.3\objenesis-3.3.jar;C:\Users\15170\.m2\repository\org\mockito\mockito-junit-jupiter\5.7.0\mockito-junit-jupiter-5.7.0.jar;C:\Users\15170\.m2\repository\org\skyscreamer\jsonassert\1.5.1\jsonassert-1.5.1.jar;C:\Users\15170\.m2\repository\com\vaadin\external\google\android-json\0.0.20131108.vaadin1\android-json-0.0.20131108.vaadin1.jar;C:\Users\15170\.m2\repository\org\springframework\spring-core\6.1.5\spring-core-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-jcl\6.1.5\spring-jcl-6.1.5.jar;C:\Users\15170\.m2\repository\org\springframework\spring-test\6.1.5\spring-test-6.1.5.jar;C:\Users\15170\.m2\repository\org\xmlunit\xmlunit-core\2.9.1\xmlunit-core-2.9.1.jar" com.intellij.rt.junit.JUnitStarter -ideVersion5 -junit5 com.nowcoder.community.CommunityApplicationTests,testBeanManagement
12:59:56.218 [main] INFO org.springframework.boot.devtools.restart.RestartApplicationListener -- Restart disabled due to context in which it is running

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.4)

2024-03-30T12:59:56.478+08:00  INFO 3732 --- [community] [           main] c.n.community.CommunityApplicationTests  : Starting CommunityApplicationTests using Java 17.0.10 with PID 3732 (started by 15170 in C:\Users\15170\Desktop\community)
2024-03-30T12:59:56.479+08:00  INFO 3732 --- [community] [           main] c.n.community.CommunityApplicationTests  : No active profile set, falling back to 1 default profile: "default"
2024-03-30T12:59:57.246+08:00  WARN 3732 --- [community] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2024-03-30T12:59:57.273+08:00  INFO 3732 --- [community] [           main] .w.s.a.s.AnnotationActionEndpointMapping : Supporting [WS-Addressing August 2004, WS-Addressing 1.0]
实例化AlphaService
初始化AlphaService
2024-03-30T12:59:57.781+08:00  WARN 3732 --- [community] [           main] ion$DefaultTemplateResolverConfiguration : Cannot find template location: classpath:/templates/ (please add some templates, check your Thymeleaf configuration, or set spring.thymeleaf.check-template-location=false)
2024-03-30T12:59:57.879+08:00  INFO 3732 --- [community] [           main] c.n.community.CommunityApplicationTests  : Started CommunityApplicationTests in 1.666 seconds (process running for 2.531)
com.nowcoder.community.service.AlphaService@787d1f9c
com.nowcoder.community.service.AlphaService@787d1f9c
销毁AlphaService

进程已结束，退出代码为 0
```
可以发现bean是单例的
3. 如果希望bean是多例的（少见），只要在希望多例的bean上加注解`@Scope("prototype")`
- 再运行测试方法，输出：
```bash

实例化AlphaService
初始化AlphaService
com.nowcoder.community.service.AlphaService@10618775
实例化AlphaService
初始化AlphaService
com.nowcoder.community.service.AlphaService@5aea8994
```
可以发现在每次get的时候实例化了一个新bean，hashcode也不一样

#### 代码实现——在容器中装配第三方bean
- 之前都是加载自己写的bean
- 此时我们不能直接加注解
- 我们需要自己写配置类，然后装配第三方的bean
1. 新建一个包存所有的配置类：`config`
- 新建测试AlphaConfig，注意只有程序的入口需要`@SpringBootApplication`，一般的注解只要`@Configuration`
- 加载bean需要注解`@Bean`
```java
package com.nowcoder.community.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;

@Configuration
public class AlphaConfig {
    
    @Bean
    public SimpleDateFormat simpleDateFormat(){
        //方法名就是bean的名字
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //这个方法返回的对象将会装配到容器里
    }
}
```
2. 新写方法测试
```java
@Test
public void testBeanConfig(){
    SimpleDateFormat simpleDateFormat = applicationContext.getBean(SimpleDateFormat.class);
    System.out.println(simpleDateFormat.format(new Date()));
}
```
输出：
```bash
2024-03-30 13:17:19
```

#### 优化写法——自动获取bean而非我们主动获取：依赖注入
比如我们需要使用AlphaDao，我们不需要这么写：
```
AlphaDao alphaDao = applicationContext.getBean(AlphaDao.class);
System.out.println(alphaDao.select());
```
我们可以直接让Spring把AlphaDao注入给一个属性，看看能否直接取到目标bean：
```java
@Autowired
private AlphaDao alphaDao;

@Test
public void testDI(){
    //测试依赖注入
    System.out.println(alphaDao);
}
```
输出:
```bash
com.nowcoder.community.dao.AlphaDaoMybatisImpl@787178b
```
指定名字：
```java
@Autowired
@Qualifier("alphaHibernate")
private AlphaDao alphaDao;

@Test
public void testDI(){
    //测试依赖注入
    System.out.println(alphaDao);
}
```
输出：
```bash
com.nowcoder.community.dao.AlphaDaoHibernateImpl@626c569b
```
==> 因此，我们使用bean的时候，<b>起名+注解</b>即可

#### 综合运用依赖注入
访问网站时的调用顺序：`controller处理请求` -> `调用业务组件service处理业务` -> `业务组件调用dao访问数据库`
<br>
==> 它们之间的关系可以通过依赖注入的方式实现
1. AlphaService需要调用AlphaDao
```java
//依赖注入
@Autowired
private AlphaDao alphaDao;

public String find() {
    return alphaDao.select();
}
```
2. AlphaController需要调用AlphaService
```java
@Autowired
private AlphaService alphaService;

//被浏览器访问需要加注解
@RequestMapping("/data")
@ResponseBody
public String getData(){
  return alphaService.find();
}
```
现在访问`localhost:8080/community/alpha/data`可以看见Mybatis。访问过程如上2->1

# 总结
## Bean和类的关系

在Spring框架中，"Bean"通常指的是由Spring容器管理的对象。这些对象是类的实例（对象），而非简单的"不同时期的类"。通过注解（如`@Component`、`@Service`等）标识的类，使得Spring在启动时能够创建其实例，这个实例就是所谓的Bean。

## Spring Boot启动和容器

启动一个Spring Boot应用时，自动配置并启动Spring应用上下文（ApplicationContext），即容器（Container）。这个容器负责应用中的Beans管理，包括创建、生命周期管理、依赖注入等。

## 注解和Bean的管理

注解（如`@Service`）指示Spring容器这个类是服务层组件，需要被容器管理。容器通过扫描注解自动识别并管理Bean，帮助Spring识别要管理的Bean，同时表明Bean间的角色和职责。

## 低耦合

使用Spring框架实现低耦合的优势之一是，Bean间的依赖关系通过依赖注入（Dependency Injection, DI）实现，非硬编码方式。这意味着，Bean的依赖（如其他Bean）由容器在运行时注入，Bean可专注于业务逻辑。

## IoC和依赖注入

控制反转（Inversion of Control, IoC）是设计原则，用于减少耦合。在Spring中，主要通过依赖注入（DI）实现。例如，`@Autowired`注解告诉Spring容器，为`alphaDao`属性注入实例，使`alphaDao.select()`方法能调用时，`alphaDao`已是被Spring容器管理并注入的Bean。