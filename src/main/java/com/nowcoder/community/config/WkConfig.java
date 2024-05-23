package com.nowcoder.community.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class  WkConfig {

    private static final Logger logger = LoggerFactory.getLogger(WkConfig.class);

    @Value("${wk.image.storage}")
    private String WkImageStorage;

    @PostConstruct // 主服务启动前先检查有无目录
    public void init(){
        File file = new File(WkImageStorage);
        if(!file.exists()){
            file.mkdir();
            logger.info("创建wk图片目录：" + WkImageStorage);
        }
    }
}
