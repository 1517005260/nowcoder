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
