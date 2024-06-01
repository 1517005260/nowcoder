package com.nowcoder.community.service;

import com.nowcoder.community.dao.DiscussPostMapper;
import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.RedisKeyUtil;
import com.nowcoder.community.util.SensitiveFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.*;

@Service
public class DiscussPostService implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(DiscussPostService.class);

    @Autowired
    private DiscussPostMapper discussPostMapper;

    @Autowired
    private SensitiveFilter sensitiveFilter;

    @Autowired
    private RedisTemplate redisTemplate;

    public List<DiscussPost> findDiscussPosts(int userId, int offset, int limit, int orderMode){
        return discussPostMapper.selectDiscussPosts(userId, offset,limit, orderMode);
    }

    public List<DiscussPost> findFolloweePosts(int userId, int offset, int limit){
        String followeeKey = RedisKeyUtil.getFolloweeKey(userId, ENTITY_TYPE_USER);
        Set<Integer> targetIds = redisTemplate.opsForZSet().range(followeeKey, 0, -1);
        if(targetIds == null){
            return null;
        } else{
            List<Integer> targetIdsList = new ArrayList<>(targetIds);
            if (targetIdsList.isEmpty()) {
                return Collections.emptyList(); // 返回空的列表
            } else {
                return discussPostMapper.selectFolloweePosts(offset, limit, targetIdsList);
            }
        }
    }

    public int findFolloweePostCount(int userId){
        String followeeKey = RedisKeyUtil.getFolloweeKey(userId, ENTITY_TYPE_USER);
        Set<Integer> targetIds = redisTemplate.opsForZSet().range(followeeKey, 0, -1);
        if(targetIds == null){
            return 0;
        }
        int cnt = 0;
        for(int id : targetIds){
            cnt += this.findDiscussPostRows(id);
        }
        return cnt;
    }

    public List<DiscussPost> findUserPosts(int userId){
        return discussPostMapper.selectUserPosts(userId);
    }

    public int findDiscussPostRows(int userId){
        return discussPostMapper.selectDiscussPostRows(userId);
    }

    public int addDiscussPost(DiscussPost discussPost){
        if(discussPost == null){
            throw new IllegalArgumentException("参数不能为空！");
        }

        //敏感词过滤：标题+内容
        //并且处理用户上传的标签，比如 <script>abcd</script> 即转义html
        discussPost.setTitle(HtmlUtils.htmlEscape(discussPost.getTitle()));
        discussPost.setContent(HtmlUtils.htmlEscape(discussPost.getContent()));
        discussPost.setTitle(sensitiveFilter.filter(discussPost.getTitle()));
        discussPost.setContent(sensitiveFilter.filter(discussPost.getContent()));

        return discussPostMapper.insertDiscussPost(discussPost);
    }

    // 更新帖子
    public void updatePost(DiscussPost discussPost){
        if(discussPost == null){
            throw new IllegalArgumentException("参数不能为空！");
        }

        discussPost.setTitle(HtmlUtils.htmlEscape(discussPost.getTitle()));
        discussPost.setContent(HtmlUtils.htmlEscape(discussPost.getContent()));
        discussPost.setTitle(sensitiveFilter.filter(discussPost.getTitle()));
        discussPost.setContent(sensitiveFilter.filter(discussPost.getContent()));

        discussPostMapper.updatePost(discussPost.getId(), discussPost.getTitle(), discussPost.getContent(), discussPost.getCreateTime());
    }

    public DiscussPost findDiscussPostById(int id){
        return discussPostMapper.selectDiscussPostById(id);
    }

    public int updateCommentCount(int id, int CommentCount){
        return discussPostMapper.updateCommentCount(id, CommentCount);
    }

    public int updateType(int id, int type){
        return discussPostMapper.updateType(id, type);
    }

    public int updateStatus(int id, int status){
        return discussPostMapper.updateStatus(id, status);
    }

    public int updateScore(int id, double score){
        return discussPostMapper.updateScore(id, score);
    }

    public void updatePostReadCount(int postId) {
        String redisKey = RedisKeyUtil.getPostReadKey(postId);
        // 如果键不存在，则从数据库获取初值
        Object readCountObj = redisTemplate.opsForValue().get(redisKey);
        if (readCountObj == null) {
            DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
            if (post != null) {
                redisTemplate.opsForValue().set(redisKey, post.getReadCount());
            } else {
                redisTemplate.opsForValue().set(redisKey, 0);
            }
        }
        // 增加访问量
        redisTemplate.opsForValue().increment(redisKey);
    }

    // 更新数据库中的阅读量
    public void updatePostReadCountInDatabase() {
        List<DiscussPost> posts = discussPostMapper.selectAllDiscussPosts();
        for (DiscussPost post : posts) {
            String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
            Object readCountObj = redisTemplate.opsForValue().get(redisKey);
            if (readCountObj != null && readCountObj instanceof Integer) {
                Integer readCount = (Integer) readCountObj;
                discussPostMapper.updateReadCount(post.getId(), readCount);
                redisTemplate.delete(redisKey);
            }
        }
        logger.info("阅读量写入MySQL任务结束！");
    }
}
