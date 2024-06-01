package com.nowcoder.community.task;

import com.nowcoder.community.service.DiscussPostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PostReadCountSyncTask {

    private static final Logger logger = LoggerFactory.getLogger(PostReadCountSyncTask.class);

    @Autowired
    private DiscussPostService discussPostService;

    @Scheduled(fixedRate = 1000 * 60 * 5)  // 测试用，5分钟执行一次
    public void syncReadCountToDatabase() {
        logger.info("开始执行阅读量写入MySQL任务！");
        discussPostService.updatePostReadCountInDatabase();
    }
}
