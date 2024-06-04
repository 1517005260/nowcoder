package com.nowcoder.community.service;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.nowcoder.community.dao.DiscussPostMapper;
import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.RedisKeyUtil;
import com.nowcoder.community.util.SensitiveFilter;
import jakarta.annotation.PostConstruct;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DiscussPostService implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(DiscussPostService.class);

    @Autowired
    private DiscussPostMapper discussPostMapper;

    @Autowired
    private SensitiveFilter sensitiveFilter;

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${caffeine.posts.max-size}")
    private int maxSize;

    @Value("${caffeine.posts.expire-seconds}")
    private int expireSeconds;

    // 帖子列表（热帖）缓存
    private LoadingCache<String, List<DiscussPost>> postListCache;

    // 缓存帖子总数
    private LoadingCache<Integer, Integer> postRowsCache;

    // 初始化缓存
    @PostConstruct
    public void init(){
        postListCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .build(new CacheLoader<String, List<DiscussPost>>() {
                    @Override
                    public @Nullable List<DiscussPost> load(String key) throws Exception { // load实质上查询了数据库
                        if(key == null || key.length() ==0){
                            throw new IllegalArgumentException("参数错误！");
                        }
                        String[] params = key.split(":");
                        if(params == null && params.length != 2){
                            throw new IllegalArgumentException("参数错误！");
                        }
                        int offset = Integer.parseInt(params[0]);
                        int limit = Integer.parseInt(params[1]);

                        // 可以在这里访问redis建立多级缓存，如果没有数据再进入db查找

                        logger.debug("load post list from DB!");
                        return discussPostMapper.selectDiscussPosts(0, offset, limit, 1);
                    }
                });

        postRowsCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .build(new CacheLoader<Integer, Integer>() {
                    @Override
                    public @Nullable Integer load(Integer key) throws Exception {
                        logger.debug("load post rows from DB !");
                        return discussPostMapper.selectDiscussPostRows(key);
                    }
                });
    }

    public List<DiscussPost> findDiscussPosts(int userId, int offset, int limit, int orderMode){
         //offset 和 limit 作为key标识一页
        if(userId == 0 && orderMode ==1){
            // 仅缓存热帖
            return postListCache.get(offset + ":" + limit); // 直接从缓存返回结果
        }
        logger.debug("load post list from DB!");
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

    public int findDiscussPostRows(int userId){
        if(userId == 0){
            // 首页查询时缓存数量
            return postRowsCache.get(userId);
        }
        logger.debug("load post rows from DB !");
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
        logger.info("阅读量写入MySQL任务开始！");
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

    // 通知粉丝有未读帖子
    public void notifyFollowersNewPost(int userId, int postId) {
        String followerKey = RedisKeyUtil.getFollowerKey(ENTITY_TYPE_USER, userId);
        Set<Integer> followerIds = redisTemplate.opsForZSet().range(followerKey, 0, -1);
        if (followerIds != null) {
            for (int followerId : followerIds) {
                String unreadKey = RedisKeyUtil.getFolloweePostUnreadKey(followerId);
                redisTemplate.opsForZSet().add(unreadKey, postId, System.currentTimeMillis());
            }
        }
    }


    // 检查是否有未读的帖子
    public boolean hasUnreadPosts(int userId) {
        String unreadKey = RedisKeyUtil.getFolloweePostUnreadKey(userId);
        Long count = redisTemplate.opsForZSet().zCard(unreadKey);
        return count != null && count > 0;
    }

    // 删除未读帖子的标记
    public void clearUnreadPosts(int userId) {
        String unreadKey = RedisKeyUtil.getFolloweePostUnreadKey(userId);
        redisTemplate.delete(unreadKey);
    }

    // 找到所有已经删除的帖子
    public List<DiscussPost> findDeletedPosts(){
        return discussPostMapper.selectDeletedDiscussPosts();
    }
}
