# Redis数据持久化到数据库

定时将redis的数据持久化到mysql中，然后清空redisKey，防止key过多导致性能下降，本末倒置

本次将刚刚新增的ReadCount持久化到数据库，采用定时任务

1. 表discuss_post新增字段read_count，默认全为0

```sql
ALTER TABLE discuss_post
ADD COLUMN read_count INT DEFAULT 0;
```

2. DiscussPost实体新增：

```java
private int readCount;

public int getReadCount() {
    return readCount;
}

public void setReadCount(int readCount) {
    this.readCount = readCount;
}

@Override
public String toString() {
    return "DiscussPost{" +
            "id=" + id +
            ", userId=" + userId +
            ", title='" + title + '\'' +
            ", content='" + content + '\'' +
            ", type=" + type +
            ", status=" + status +
            ", createTime=" + createTime +
            ", commentCount=" + commentCount +
            ", score=" + score +
            ", readCount=" + readCount +
            '}';
}
```

3. dao

DiscussPostMapper新增：

```java
int updateReadCount(int id, int readCount);

// 全体帖子
List<DiscussPost> selectAllDiscussPosts();
```

sql实现

```xml
<sql id="selectFields">
    id, user_id, title, content, type, status, create_time, comment_count, score, read_count
</sql>

<sql id="insertFields">
    user_id, title, content, type, status, create_time, comment_count, score <!--默认阅读量为0-->
</sql>

<select id="selectAllDiscussPosts" resultType="DiscussPost">
select <include refid="selectFields"></include>
from discuss_post
where status != 2
</select>

<update id="updateReadCount">
update discuss_post set read_count = #{readCount}
where id = #{id}
</update>
```

4. 更新redis逻辑：一开始从sql取初值，然后定时将redis数据写入sql，删除redisKey

a. DiscussPostService新增：

```java
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
```

b. 新建软件包task，新建方法PostReadCountSyncTask

```java
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
```

c. 更改上次从redis获取浏览量的方法：

DiscussPostController:

```java
String redisKey = RedisKeyUtil.getPostReadKey(discussPostId);
if(redisKey != null){
model.addAttribute("postReadCount", redisTemplate.opsForValue().get(redisKey));
}else {
model.addAttribute("postReadCount", discussPost.getReadCount());
}
```

其他Controller：

```java
String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
Object readCountObj = redisTemplate.opsForValue().get(redisKey);
Integer readCount = null;
if (readCountObj != null) {
    readCount = (Integer) readCountObj;
} else {
    DiscussPost dbPost = discussPostMapper.selectDiscussPostById(post.getId());
    if (dbPost != null) {
        readCount = dbPost.getReadCount();
        redisTemplate.opsForValue().set(redisKey, readCount);
    } else {
        readCount = 0;
    }
}
map.put("postReadCount", readCount);
```

