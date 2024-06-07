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

    @Scheduled(fixedRate = 1000 * 60 * 60 * 24)  // 每天一次
    public void syncReadCountToDatabase() {
        discussPostService.updatePostReadCountInDatabase();
    }
}
