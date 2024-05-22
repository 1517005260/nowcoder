package com.nowcoder.community.quartz;

import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.service.ElasticsearchService;
import com.nowcoder.community.service.LikeService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.RedisKeyUtil;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class PostScoreRefreshJob implements Job , CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(PostScoreRefreshJob.class);

    @Autowired
    private RedisTemplate redisTemplate; // 从redis中取数据

    @Autowired
    private DiscussPostService discussPostService; // 修改分数用

    @Autowired
    private LikeService likeService;  // 查询点赞数

    @Autowired
    private ElasticsearchService elasticsearchService; // 修改了帖子数据，es需要同步

    // 计算核心之一——网站的成立时间
    private static final Date startDate;
    static {
        try {
            startDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2024-05-22 00:00:00");
        } catch (ParseException e) {
            throw new RuntimeException("初始化网站创建时间失败: " + e);
        }
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        String redisKey = RedisKeyUtil.getPostScoreKey();
        BoundSetOperations operations = redisTemplate.boundSetOps(redisKey);  // 对于集合中一组数据的操作
        Long size = operations.size();

        if(size == 0 || size == null){
            // 没有任何变化
            logger.info("没有需要更新分数的帖子！定时任务取消！");
            return;
        }

        logger.info("帖子分数定时更新任务开始！正在刷新帖子分数......共" + size + "个");
        while (size != null && size > 0) {
            Integer postId = (Integer) operations.pop();
            if (postId != null) {
                logger.info("[任务执行] 刷新帖子分数: id = " + postId);
                refresh(postId);
            }
            size = operations.size();
        }
        logger.info("帖子分数定时更新任务结束！");
    }

    private void refresh(int postId){
        DiscussPost post = discussPostService.findDiscussPostById(postId);
        if (post == null) {
            logger.error("该帖子不存在! postId = " + postId);
            return;
        } else if (post.getStatus() == 2) {
            logger.error("该帖子已被删除! postId = " + postId);
            return;
        }

        // 加精
        boolean isWonderful = post.getStatus() == 1;
        // 评论数
        int commentCount = post.getCommentCount();
        // 点赞数
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, postId);

        // 计算分数
        // 1. log包含的数
        double weight = (isWonderful ? 75 : 0) + 10 * commentCount + 2 * likeCount;
        // 2. 计算score
        double score = Math.log10(Math.max(weight, 1))  // 注意log计算可能为负，这里需规避
                + (post.getCreateTime().getTime() - startDate.getTime()) / (1000 * 3600 * 24); // 毫秒换算为天

        // 更新分数
        discussPostService.updateScore(postId, score);

        // 更新es
        // 注意不能用传参进来的post，因为这个post的分数还是旧分数
        post.setScore(score);
        elasticsearchService.saveDiscussPost(post);
    }

}
