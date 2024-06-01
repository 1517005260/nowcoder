# 热帖排行

热帖排行涉及帖子的分数计算，不同的网站分数计算的方式都是不一样的

但是，一般而言，score随time递减，随用户喜好（点赞关注评论）程度递增

例如：`score = log(精华分 + 10*评论数 + 2*点赞数) + (帖子发布时间 - 网站成立时间)`

<b>有了计算分数的公式之后，我们要在什么时候给帖子算分数呢？</b>

1. 当某个操作会影响帖子分数的时候（比如评论了一下），立即去计算分数 ==> 效率低 万一是高频的的点赞评论，短时间内负载不够

2. **定时任务计算**：一个小时/半个小时算一次，稳定时再计算，计算结果也比较稳定（用户不会每次刷新，排在前面的帖子都不一样）。
- 注意：定时算并不是对所有帖子定时算，那样效率太低了，因为一些远古帖子热度基本都不会变化了
- 所以，我们采用这样的方式：每当触发加分时间时，把帖子送入缓存（redis），等定时到时统一算分
- redis用什么数据结构存储？
  - 不能用队列，虽然它代表了先后顺序，但是若有这种情况：`A被点赞,B被点赞，A又被点赞`，此时队列中为`ABA`，我们对帖子A有重复计算，且队列失去了顺序
  - 所以用Set最好，有去重

有了score后热度按score降序即可

## 代码实现
1. 定义redisKey

```java
private static final String PREFIX_POST = "post";

// 统计帖子分数：存产生变化的帖子，不需要传参
public static String getPostScoreKey(){
    return PREFIX_POST + SPLIT + "score";
}
```

2. 增加加分事件

DiscussPostController:

```java
@Autowired
private RedisTemplate redisTemplate;

// 1. 新帖有初始分数
public String addDiscussPost(String title, String content){
  // 其余代码不变
  // 初始分数计算
  String redisKey = RedisKeyUtil.getPostScoreKey();
  redisTemplate.opsForSet().add(redisKey, discussPost.getId());

  return CommunityUtil.getJSONString(0, "发布成功！");
}

// 2. 加精有额外分数（注意：置顶是不算分数的，因为它已经在最顶上了）
public String setWonderful(int id){
    // 其余代码不变
    // 加精分数计算
  String redisKey = RedisKeyUtil.getPostScoreKey();
  redisTemplate.opsForSet().add(redisKey, id);
}
```

CommentController:

```java
@Autowired
private RedisTemplate redisTemplate;

// 3. 评论有额外分数
public String addComment(@PathVariable("discussPostId") int id, Comment comment){
    // 其余代码不变
  if(comment.getEntityType() == ENTITY_TYPE_POST){
    String redisKey = RedisKeyUtil.getPostScoreKey();
    redisTemplate.opsForSet().add(redisKey, id);
  }

  return "redirect:/discuss/detail/" + id;
}
```

LikeController:

```java
@Autowired
private RedisTemplate redisTemplate;

// 4.点赞有额外分数
public String like(int entityType, int entityId, int entityUserId, int postId){
    // 其余代码不变
  if(entityType == ENTITY_TYPE_POST){
    String redisKey = RedisKeyUtil.getPostScoreKey();
    redisTemplate.opsForSet().add(redisKey, postId);
  }
}
```

浏览由于过于频繁，故不放入redis里

3. 处理加分事件——定时任务Quartz

a. 写一个job——PostScoreRefreshJob

```java
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
    // 浏览数
    Integer readCountInteger = (Integer) redisTemplate.opsForValue().get(RedisKeyUtil.getPostReadKey(postId));
    long readCount = 0;
    if (readCountInteger != null) {
      readCount = Long.parseLong(readCountInteger.toString());
    }

    // 计算分数
    // 1. log包含的数
    double weight = (isWonderful ? 75 : 0) + 10 * commentCount + 2 * likeCount + 0.1 * readCount;
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
```

b. 根据刚刚写的job补充更新的service方法

在DiscussPostMapper中新增：

```java
int updateScore(int id, double score);
```

sql实现：

```xml
<update id="updateScore">
    update discuss_post set score = #{score}
    where id = #{id}
</update>
```

DiscussPostService新增：

```java
public int updateScore(int id, double score){
    return discussPostMapper.updateScore(id, score);
}
```

c. 为写完的Job做配置：

在QuartzConfig新增：

```java
// 刷新帖子分数Job
@Bean
public JobDetailFactoryBean postScoreRefreshJobDetail(){
    JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
    factoryBean.setJobClass(PostScoreRefreshJob.class);
    factoryBean.setName("postScoreRefreshJob");
    factoryBean.setGroup("communityJobGroup");
    factoryBean.setDurability(true);
    factoryBean.setRequestsRecovery(true);
    return factoryBean;
}

@Bean
public SimpleTriggerFactoryBean postScoreRefreshTrigger(JobDetail postScoreRefreshJobDetail){
    SimpleTriggerFactoryBean factoryBean = new SimpleTriggerFactoryBean();
    factoryBean.setJobDetail(postScoreRefreshJobDetail);
    factoryBean.setName("postScoreRefreshTrigger");
    factoryBean.setGroup("CommunityTriggerGroup");
    factoryBean.setRepeatInterval(1000 * 60 * 5); // 定时任务：5分钟，这里为了测试方便写的比较短
    factoryBean.setJobDataMap(new JobDataMap());
    return factoryBean;
}
```

数据库新增触发器：双重保险使得score非负数

```sql
DELIMITER //

CREATE TRIGGER BeforeUpdateScore
BEFORE UPDATE ON discuss_post
FOR EACH ROW
BEGIN
    IF NEW.score < 0 THEN
        SET NEW.score = 0;
    END IF;
END; //

DELIMITER ;
```

4. 展现热帖——重构老方法

a. dao

DiscussPostMapper重构：

```java
List<DiscussPost> selectDiscussPosts(int userId, int offset, int limit, int orderMode); //orderMode0,最新，1，最热
```

sql更改：

```xml
<select id="selectDiscussPosts" resultType="DiscussPost">
    select <include refid="selectFields"></include>
    from discuss_post
    where status != 2
    <if test="userId!=0">
        and user_id = #{userId}
    </if>
    <if test="orderMode==0">
        order by type desc, create_time desc
    </if>
    <if test="orderMode==1">
        order by type desc, score desc, create_time desc
    </if>
    limit #{offset}, #{limit}
</select>
```

b. 更改因重构变化的代码：

ElasticSearchTests：

```java
@Test
public void insertList() {  // 多条数据插入
    discussRepository.saveAll(discussMapper.selectDiscussPosts(101, 0, 100, 0));  // 肯定条数不足limit，这里是配合mapper方法进行分页
    discussRepository.saveAll(discussMapper.selectDiscussPosts(102, 0, 100, 0));
    // 结果查看：postman GET  localhost:9200/discusspost/_search
}
```

MapperTests:

```java
@Test
public void testSelectPosts(){
    List<DiscussPost> lists = discussPostMapper.selectDiscussPosts(149,0,10, 0);
    for (DiscussPost post:lists){
        System.out.println(post);
    }
    int rows = discussPostMapper.selectDiscussPostRows(149);
    System.out.println(rows);
}
```

DiscussPostService:

```java
public List<DiscussPost> findDiscussPosts(int userId, int offset, int limit, int orderMode){
    return discussPostMapper.selectDiscussPosts(userId, offset,limit, orderMode);
}
```

HomeController:

```java
@RequestMapping(path = "/index", method = RequestMethod.GET)
public String getIndexPage(Model model, Page page,
                           @RequestParam(name = "orderMode", defaultValue = "1")int orderMode){ // 默认热帖排序

    //方法调用前，SpringMVC会自动实例化Model和Page，并将Page注入Model
    //所以不用model.addAttribute(Page),直接在thymeleaf可以访问Page的数据

    page.setRows(discussPostService.findDiscussPostRows(0));
    page.setPath("/index?orderMode=" + orderMode);
    // 默认是第一页，前10个帖子
    List<DiscussPost> list = discussPostService.findDiscussPosts(0, page.getOffset(), page.getLimit(), orderMode);

    // 将前10个帖子和对应的user对象封装
    List<Map<String, Object>> discussPosts = new ArrayList<>();
    if(list !=null){
        for(DiscussPost post:list){
            Map<String,Object> map = new HashMap<>();
            map.put("post" , post);
            User user = userService.findUserById(post.getUserId());
            map.put("user", user);

            long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
            map.put("likeCount",likeCount);

            discussPosts.add(map);
        }
    }
    // 处理完的数据填充给前端页面
    model.addAttribute("discussPosts", discussPosts);
    model.addAttribute("orderMode", orderMode);
    return "/index";
}
```

c. 前端index.html:

```html
<!-- 筛选条件 -->
<ul class="nav nav-tabs mb-3">
  <li class="nav-item">
    <a th:class="|nav-link ${orderMode==1?'active':''}|" th:href="@{/index(orderMode=1)}">最热</a>
  </li>
  <li class="nav-item">
    <a th:class="|nav-link ${orderMode==0?'active' :''}|" th:href="@{/index(orderMode=0)}">最新</a>
  </li>
</ul>
```

# orderMode = 2 : 我关注人的帖子

1. dao

DiscussPostMapper新增：

```java
// 关注者的帖子
List<DiscussPost> selectFolloweePosts(int offset, int limit, List<Integer> ids);

// 某人所有的帖子，不分页
List<DiscussPost> selectUserPosts(int userId);
```

sql实现：

```xml
<select id="selectFolloweePosts" resultType="DiscussPost">
  select <include refid="selectFields"></include>
  from discuss_post
  where status != 2
  and user_id in
  <foreach collection="ids" item="id" open="(" separator="," close=")">
    #{id}
  </foreach>
  order by create_time desc
  limit #{offset}, #{limit}
</select>

<select id="selectUserPosts" resultType="DiscussPost">
select <include refid="selectFields"></include>
from discuss_post
where status != 2
and user_id = #{userId}
order by create_time desc
</select>
```

2. service

DiscussPostService新增：

```java
@Autowired
private RedisTemplate redisTemplate;

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
```

3. controller:

HomeController改动;

```java
@Autowired
private HostHolder hostHolder;

@RequestMapping(path = "/index", method = RequestMethod.GET)
public String getIndexPage(Model model, Page page,
                           @RequestParam(name = "orderMode", defaultValue = "1")int orderMode){ // 默认热帖排序

  //方法调用前，SpringMVC会自动实例化Model和Page，并将Page注入Model
  //所以不用model.addAttribute(Page),直接在thymeleaf可以访问Page的数据

  List<DiscussPost> list = new ArrayList<>();
  if (orderMode == 2) {
    User user = hostHolder.getUser();
    if (user == null) {
      return "/error/404";
    }
    int cnt = discussPostService.findFolloweePostCount(user.getId());
    page.setRows(cnt);
    list = discussPostService.findFolloweePosts(user.getId(), page.getOffset(), page.getLimit());
  } else {
    page.setRows(discussPostService.findDiscussPostRows(0));
    // 默认是第一页，前10个帖子
    list = discussPostService.findDiscussPosts(0, page.getOffset(), page.getLimit(), orderMode);
  }
  page.setPath("/index?orderMode=" + orderMode);

  // 将前10个帖子和对应的user对象封装
  List<Map<String, Object>> discussPosts = new ArrayList<>();
  if(list !=null){
    for(DiscussPost post:list){
      Map<String,Object> map = new HashMap<>();
      map.put("post" , post);
      User user = userService.findUserById(post.getUserId());
      map.put("user", user);

      long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
      map.put("likeCount",likeCount);

      discussPosts.add(map);
    }
  }
  // 处理完的数据填充给前端页面
  model.addAttribute("discussPosts", discussPosts);
  model.addAttribute("orderMode", orderMode);
  return "/index";
}
```

4. index前端：

```html
<li class="nav-item">
    <a th:class="|nav-link ${orderMode==2?'active' :''}|" th:href="@{/index(orderMode=2)}" th:if="${loginUser!=null}">关注</a>
</li>

<div th:if="${#lists.isEmpty(discussPosts)}">
  <img th:src="@{/img/noResult.png}" alt="无私信" class="img-fluid mx-auto d-block mt-4">
  <p class="text-center mt-3">额，什么都没有哦~</p>
</div>
<!-- 帖子列表 -->
<ul class="list-unstyled">
  <li class="media pb-3 pt-3 mb-3 border-bottom" th:each="map:${discussPosts}">
```