package com.nowcoder.community.config;

import com.google.code.kaptcha.Producer;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class KaptchaConfig {

    //这样就会被装配到容器里了
    @Bean
    public Producer KaptchaConfig(){
        Properties properties = new Properties();
        properties.setProperty("kaptcha.image.width", "100");  //100px
        properties.setProperty("kaptcha.image.height", "40");
        properties.setProperty("kaptcha.textproducer.font.size", "32");
        properties.setProperty("kaptcha.textproducer.font.color", "black");
        properties.setProperty("kaptcha.textproducer.char.string", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYAZ");  //随机字符的范围
        properties.setProperty("kaptcha.textproducer.char.length", "4");  // 验证码4位
        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.NoNoise");  //噪声类，即验证码样式

        DefaultKaptcha kaptcha = new DefaultKaptcha();
        Config config = new Config(properties);
        kaptcha.setConfig(config);
        return kaptcha;
    }
}
