package com.nowcoder.community.service;

import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LikeService implements CommunityConstant {
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DiscussPostService discussPostService;

    // 点赞
    public void like(int userId, int entityType, int entityId, int entityUserId){
        // 使用redis事务，由于涉及用户对实体的赞和另一个用户自己收到的赞两个redis”表“
        redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
                // 注意：传进来的userId是点赞的人的id，而现在我们要找到被赞的人的id，即entity的作者
                // 但是我们不能直接用entity找作者。1. 还要区分type，麻烦 2. 还要访问数据库，违背了redis高效的初衷
                // 因此，我们重构原方法的传参，把实体作者传进来
                String userLikeKey = RedisKeyUtil.getUserLikeKey(entityUserId);
                String userPostKey = RedisKeyUtil.getUserPostKey(userId);

                boolean isMember = operations.opsForSet().isMember(entityLikeKey, userId);  // 查询放在事务之外

                operations.multi();
                // 未赞过，实体赞和用户收到的赞同步增加，否则同步减少
                if(isMember){
                    operations.opsForSet().remove(entityLikeKey, userId);
                    operations.opsForValue().decrement(userLikeKey);
                    if (entityType == ENTITY_TYPE_POST) {
                        DiscussPost post = discussPostService.findDiscussPostById(entityId);
                        operations.opsForZSet().remove(userPostKey, String.valueOf(post.getId()));
                    }
                }else{
                    operations.opsForSet().add(entityLikeKey, userId);
                    operations.opsForValue().increment(userLikeKey);
                    if (entityType == ENTITY_TYPE_POST) {
                        DiscussPost post = discussPostService.findDiscussPostById(entityId);
                        operations.opsForZSet().add(userPostKey, String.valueOf(post.getId()), System.currentTimeMillis());
                    }
                }

                return operations.exec();
            }
        });
    }

    // 统计点赞数量
    public long findEntityLikeCount(int entityType, int entityId){
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        return redisTemplate.opsForSet().size(entityLikeKey);
    }

    // 查看某个人对某个实体的点赞状态（不用boolean是因为int还能表现出“踩”等其他需求）
    public int findEntityLikeStatus(int userId, int entityType, int entityId){
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        return redisTemplate.opsForSet().isMember(entityLikeKey, userId) ? 1 : 0;  // 1赞 0无
    }

    // 查询一个用户收到的赞
    public int findUserLikeCount(int userId){
        String userLikeKey = RedisKeyUtil.getUserLikeKey(userId);
        Integer count = (Integer)redisTemplate.opsForValue().get(userLikeKey);
        return count == null ? 0 : count.intValue();
    }

    // 查询一个用户点过赞的帖子
    public List<DiscussPost> findUserLikePosts(int userId) {
        String redisKey = RedisKeyUtil.getUserPostKey(userId);

        // 获取存储在redis ZSet中的帖子ID，按score降序排列
        Set<String> postIds = redisTemplate.opsForZSet().reverseRange(redisKey, 0, -1);
        if (postIds == null) {
            return null;
        }

        // 根据帖子ID从数据库获取帖子详情
        List<DiscussPost> likePosts = new ArrayList<>();
        for (String postId : postIds) {
            DiscussPost post = discussPostService.findDiscussPostById(Integer.parseInt(postId));
            if (post != null && post.getStatus() != 2) {
                likePosts.add(post);
            }
        }

        return likePosts;
    }
}
