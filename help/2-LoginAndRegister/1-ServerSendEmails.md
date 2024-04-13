# 发送邮件功能（注册用）

总体步骤
- 邮箱设置
  - 启用客户端SMTP服务
- Spring Email
  - 导入jar包
  - 邮箱参数配置
  - 使用JavaMainSender发送邮件
- 模板引擎
  - 使用Thymeleaf发送HTML邮件

## 开启SMTP服务发送邮件
如图所示即可

![SMTP](/imgs/SMTP.png)

## Spring Email
流程：以下文测试类为例，22011854forum@sina.com -> sina.com -> gmail.com -> linkaigao77@gmail.com
<br>
实质上就是托管邮件发送

1. 继续在[这里](https://mvnrepository.com/)搜索jar包：spring boot starter mail
- 将这个配置复制到pom.xml里面，并重新用maven加载更改：
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
  <version>3.2.1</version>
</dependency>
```
2. 邮箱参数配置
- 在主配置文件中追加：

```
# mail
spring.mail.host=smtp.sina.com
spring.mail.port=465       # 发邮件端口号
spring.mail.username={用户名}@xxx.com
spring.mail.password={授权码}
spring.mail.protocol=smtps   # 加密smtp协议
spring.mail.properties.mail.smtp.ssl.enable=true  # 启用ssl协议
```

3. 写代码发送邮件，封装`MailClient`（server是sina.com）以反复使用

```java
package com.nowcoder.community.util;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 通用注解Component
@Component
public class MailClient {
    private static final Logger logger = LoggerFactory.getLogger(MailClient.class);  //日志记录

    @Autowired
    private JavaMailSender mailSender;

    //1.谁来发？——  22011854forum@sina.com
    @Value("${spring.mail.username}")
    private String from;

    // 2.谁来接？  3.发什么？
    public void sendMail(String to, String subject, String content){
        try {
            MimeMessage message = mailSender.createMimeMessage();  //创建邮件模板
            MimeMessageHelper helper = new MimeMessageHelper(message);  //用帮助类写邮件
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);  //格式：支持html
            mailSender.send(helper.getMimeMessage());
        } catch (MessagingException e) {
            logger.error("发送邮件失败，失败原因：" + e.getMessage());
        }
    }

}
```

写测试类测试：

```java
package com.nowcoder.community;

import com.nowcoder.community.util.MailClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class MailTests {
    @Autowired
    private MailClient mailClient;

    @Test
    public void testTextMail(){
        mailClient.sendMail("linkaigao77@gmail.com", "Test", "hello");
    }
}
```

## 模板引擎
通过模板引擎，将上文的纯文字邮件改进为html格式的邮件

原MailTests.java新增：

```java
@Autowired
private TemplateEngine templateEngine;  //模板引擎

@Test
public void testHTMLMail(){
  Context context = new Context();
  context.setVariable("username","sunday");   //key-value

  String content = templateEngine.process("/mail/demo",context);
  System.out.println(content);

  mailClient.sendMail("1517005260@qq.com", "HTML test", content);
}
```

html模板：

```html
<!DOCTYPE html>
<html lang="en" xml:th="https://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>邮件html模板示例</title>
</head>
<body>
<p>欢迎你，<span style="color:blue" th:text="${username}"></span>！</p>
</body>
</html>
```