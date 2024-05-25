# 个人主页完善

## 功能：我的回复、我的帖子、我赞过的帖子

## 我的回复、我的帖子

1. dao

只要增加CommentMapper:

```java
// 根据用户查询评论
List<Comment> selectCommentsByUser(int userId, int offset, int limit);

// 查询用户评论数量
int selectCountByUser(int userId);
```

sql实现：

```xml
<select id="selectCommentsByUser" resultType="Comment">
    select <include refid="selectFields"></include>
    from comment
    where status != 1
    and user_id = #{userId}
    and entity_type = 1
    and exists (
    select id from discuss_post where id = comment.entity_id and status != 2
    )
    order by create_time desc
    limit #{offset}, #{limit}
</select>


<select id="selectCountByUser" resultType="int">
    select count(id)
    from comment
    where status != 1
      and user_id = #{userId}
      and entity_type = 1    <!-- 对帖子评论才查 -->
      and exists (select id
                  from discuss_post
                  where id = comment.entity_id and status != 2)  <!--并且帖子没被删除-->
</select>
```

2. service

只要增加CommentService:

```java
// 查询某个用户的所有评论
public List<Comment> findUserComments(int userId, int offset, int limit) {
    return commentMapper.selectCommentsByUser(userId, offset, limit);
}

// 查询某个用户的评论数量
public int findUserCount(int userId) {
    return commentMapper.selectCountByUser(userId);
}
```

3. controller:

UserController新增：

```java
 @Autowired
private CommentService commentService;

@Autowired
private DiscussPostService discussPostService;

// 我的帖子、我的回复
@RequestMapping(path = "/mypost/{userId}", method = RequestMethod.GET)
public String getMyPost(@PathVariable("userId") int userId, Page page, Model model) {
    User user = userService.findUserById(userId);
    if (user == null) {
        throw new RuntimeException("该用户不存在！");
    }
    model.addAttribute("user", user);

    // 分页信息
    page.setLimit(10);
    page.setPath("/user/mypost/" + userId);
    page.setRows(discussPostService.findDiscussPostRows(userId));

    // 帖子列表
    List<DiscussPost> discussList = discussPostService
            .findDiscussPosts(userId, page.getOffset(), page.getLimit(), 1);
    List<Map<String, Object>> discussVOList = new ArrayList<>();
    if (discussList != null) {
        for (DiscussPost post : discussList) {
            Map<String, Object> map = new HashMap<>();
            map.put("discussPost", post);
            map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
            discussVOList.add(map);
        }
    }
    model.addAttribute("discussPosts", discussVOList);

    return "/site/my-post";
}

@RequestMapping(path = "/myreply/{userId}", method = RequestMethod.GET)
public String getMyReply(@PathVariable("userId") int userId, Page page, Model model) {
    User user = userService.findUserById(userId);
    if (user == null) {
        throw new RuntimeException("该用户不存在！");
    }
    model.addAttribute("user", user);

    // 分页信息
    page.setLimit(10);
    page.setPath("/user/myreply/" + userId);
    page.setRows(commentService.findUserCount(userId));

    // 回复列表
    List<Comment> commentList = commentService.findUserComments(userId, page.getOffset(), page.getLimit());
    List<Map<String, Object>> commentVOList = new ArrayList<>();
    if (commentList != null) {
        for (Comment comment : commentList) {
            Map<String, Object> map = new HashMap<>();
            map.put("comment", comment);
            DiscussPost post = discussPostService.findDiscussPostById(comment.getEntityId());
            map.put("discussPost", post);
            commentVOList.add(map);
        }
    }
    model.addAttribute("comments", commentVOList);

    return "/site/my-reply";
}
```

4. 前端：

profile增加链接：

```html
<div class="position-relative">
    <ul class="nav nav-tabs">
        <li class="nav-item">
            <a class="nav-link active" th:href="@{|/user/profile/${user.id}|}">个人信息</a>
        </li>
        <li class="nav-item">
            <a class="nav-link" th:href="@{|/user/mypost/${user.id}|}"
               th:text="${loginUser==null||loginUser.id!=user.id?'TA的帖子':'我的帖子'}">我的帖子</a>
        </li>
        <li class="nav-item">
            <a class="nav-link" th:href="@{|/user/myreply/${user.id}|}"
               th:text="${loginUser==null||loginUser.id!=user.id?'TA的回复':'我的回复'}">我的回复</a>
        </li>
    </ul>
</div>
```

my-post:

```html
<!doctype html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
	<meta name="_csrf" th:content="${_csrf.token}">
	<meta name="_csrf_header" th:content="${_csrf.headerName}">
	<link rel="icon" th:href="@{/img/icon.png}"/>
	<link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css" crossorigin="anonymous">
	<link rel="stylesheet" th:href="@{/css/global.css}" />
	<title>个人主页</title>
</head>
<body>
	<div class="nk-container">
		<!-- 头部 -->
		<header class="bg-dark sticky-top" th:replace="index::header">
		</header>

		<!-- 内容 -->
		<div class="main">
			<div class="container">
				<!-- 选项 -->
				<div class="position-relative">
					<ul class="nav nav-tabs">
						<li class="nav-item">
							<a class="nav-link" th:href="@{|/user/profile/${user.id}|}">个人信息</a>
						</li>
						<li class="nav-item">
							<a class="nav-link active" th:href="@{|/user/mypost/${user.id}|}" th:text="${loginUser==null||loginUser.id!=user.id?'TA的帖子':'我的帖子'}">我的帖子</a>
						</li>
						<li class="nav-item">
							<a class="nav-link" th:href="@{|/user/myreply/${user.id}|}" th:text="${loginUser==null||loginUser.id!=user.id?'TA的回复':'我的回复'}">我的回复</a>
						</li>
					</ul>
					<a th:href="@{|/user/profile/${user.id}|}" class="text-muted position-absolute rt-0">返回个人主页&gt;</a>
				</div>
				<!-- 判空 -->
				<div th:if="${#lists.isEmpty(discussPosts)}">
					<img th:src="@{/img/noResult.png}" alt="无私信" class="img-fluid mx-auto d-block mt-4">
					<p class="text-center mt-3">还没有发布过帖子哦~</p>
				</div>
				<!-- 我的帖子 -->
				<div class="mt-4">
					<h6><b class="square"></b> 发布的帖子(<i th:text="${page.rows}">93</i>)</h6>
					<ul class="list-unstyled mt-4 pl-3 pr-3">
						<li class="border-bottom pb-3 mt-4" th:each="map:${discussPosts}">
							<div class="font-size-16 text-info">
								<a th:href="@{|/discuss/detail/${map.discussPost.id}|}" th:utext="${map.discussPost.title}" class="text-info">备战春招，面试刷题跟他复习，一个月全搞定！</a>
							</div>
							<div class="mt-1 font-size-14" th:utext="${map.discussPost.content}">
								金三银四的金三已经到了，你还沉浸在过年的喜悦中吗？
								如果是，那我要让你清醒一下了：目前大部分公司已经开启了内推，正式网申也将在3月份陆续开始，金三银四，春招的求职黄金时期已经来啦！！！
								再不准备，作为19应届生的你可能就找不到工作了。。。作为20届实习生的你可能就找不到实习了。。。
								现阶段时间紧，任务重，能做到短时间内快速提升的也就只有算法了，
								那么算法要怎么复习？重点在哪里？常见笔试面试算法题型和解题思路以及最优代码是怎样的？
								跟左程云老师学算法，不仅能解决以上所有问题，还能在短时间内得到最大程度的提升！！！
							</div>
							<div class="text-right font-size-12 text-muted">
								赞 <i class="mr-3" th:text="${map.likeCount}">11</i> 发布于 <b th:text="${#dates.format(map.discussPost.createTime,'yyyy-MM-dd HH:mm:ss')}">2019-04-15 10:10:10</b>
							</div>
						</li>
					</ul>
					<!-- 分页 -->
                    <nav class="mt-5" th:replace="index::pagination">
					</nav>					
				</div>				
			</div>
		</div>

		<!-- 尾部 -->
		<footer class="bg-dark" th:replace="index::footer">
		</footer>
	</div>

	<script src="https://code.jquery.com/jquery-3.3.1.min.js" crossorigin="anonymous"></script>
	<script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js" crossorigin="anonymous"></script>
	<script src="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/js/bootstrap.min.js" crossorigin="anonymous"></script>
	<script th:src="@{/js/global.js}"></script>
</body>
</html>
```

my-reply:

```html
<!doctype html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
	<meta name="_csrf" th:content="${_csrf.token}">
	<meta name="_csrf_header" th:content="${_csrf.headerName}">
	<link rel="icon" th:href="@{/img/icon.png}"/>
	<link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css" crossorigin="anonymous">
	<link rel="stylesheet" th:href="@{/css/global.css}" />
	<title>个人主页</title>
</head>
<body>
<div class="nk-container">
	<!-- 头部 -->
	<header class="bg-dark sticky-top" th:replace="index::header">
	</header>

	<!-- 内容 -->
	<div class="main">
		<div class="container">
			<!-- 选项 -->
			<div class="position-relative">
				<ul class="nav nav-tabs">
					<li class="nav-item">
						<a class="nav-link" th:href="@{|/user/profile/${user.id}|}">个人信息</a>
					</li>
					<li class="nav-item">
						<a class="nav-link" th:href="@{|/user/mypost/${user.id}|}" th:text="${loginUser==null||loginUser.id!=user.id?'TA的帖子':'我的帖子'}">我的帖子</a>
					</li>
					<li class="nav-item">
						<a class="nav-link active" th:href="@{|/user/myreply/${user.id}|}" th:text="${loginUser==null||loginUser.id!=user.id?'TA的回复':'我的回复'}">我的回复</a>
					</li>
				</ul>
				<a th:href="@{|/user/profile/${user.id}|}" class="text-muted position-absolute rt-0">返回个人主页&gt;</a>
			</div>
			<!-- 判空 -->
			<div th:if="${#lists.isEmpty(comments)}">
				<img th:src="@{/img/noResult.png}" alt="无私信" class="img-fluid mx-auto d-block mt-4">
				<p class="text-center mt-3">还没有评论过帖子哦~</p>
			</div>
			<!-- 我的帖子 -->
			<div class="mt-4">
				<h6><b class="square"></b> 回复的帖子(<i th:text="${page.rows}">379</i>)</h6>
				<ul class="list-unstyled mt-4 pl-3 pr-3">
					<li class="border-bottom pb-3 mt-4" th:each="map:${comments}">
						<div class="font-size-16 text-info">
							<a th:href="@{|/discuss/detail/${map.discussPost.id}|}" th:utext="${map.discussPost.title}" class="text-info">备战春招，面试刷题跟他复习，一个月全搞定！</a>
						</div>
						<div class="mt-1 font-size-14" th:utext="${map.comment.content}">
							顶顶顶!
						</div>
						<div class="text-right font-size-12 text-muted">
							回复于 <b th:text="${#dates.format(map.comment.createTime,'yyyy-MM-dd HH:mm:ss')}">2019-04-15 10:10:10</b>
						</div>
					</li>
				</ul>
				<!-- 分页 -->
                <nav class="mt-5" th:replace="index::pagination">
				</nav>
			</div>
		</div>
	</div>

	<!-- 尾部 -->
	<footer class="bg-dark" th:replace="index::footer">
	</footer>
</div>

<script src="https://code.jquery.com/jquery-3.3.1.min.js" crossorigin="anonymous"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js" crossorigin="anonymous"></script>
<script src="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/js/bootstrap.min.js" crossorigin="anonymous"></script>
<script th:src="@{/js/global.js}"></script>
</body>
</html>
```

## 我赞过的帖子

1. 增加redisKey:

```java
private static final String PREFIX_LIKE_POST = "like:post";

// 某个用户赞了的帖子
// like:post:userid -> Zset(post, likeTime)
public static String getUserPostKey(int userId){
    return PREFIX_LIKE_POST + SPLIT + userId;
}
```

使得DicussPost可序列化：

```java
public class DiscussPost  implements Serializable {}
```

更改RedisConfig：

```java
// 设置value序列化方式为GenericJackson2JsonRedisSerializer，以便处理复杂对象
template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

// 设置hash-value序列化方式为GenericJackson2JsonRedisSerializer
template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
```

2. 改动likeService:

```java
public class LikeService implements CommunityConstant {
    @Autowired
    private DiscussPostService discussPostService;

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
                    if(entityType == ENTITY_TYPE_POST){
                        operations.opsForSet().remove(userPostKey, discussPostService.findDiscussPostById(entityId));
                    }
                }else{
                    operations.opsForSet().add(entityLikeKey, userId);
                    operations.opsForValue().increment(userLikeKey);
                    if (entityType == ENTITY_TYPE_POST) {
                        DiscussPost post = discussPostService.findDiscussPostById(entityId);
                        operations.opsForZSet().add(userPostKey, post, System.currentTimeMillis());
                    }
                }

                return operations.exec();
            }
        });
    }
    
    
}

// 查询一个用户点过赞的帖子
public List<DiscussPost> findUserLikePosts(int userId) {
    String redisKey = RedisKeyUtil.getUserPostKey(userId);

    // 获取存储在redis ZSet中的DiscussPost对象，按score(点赞时间)降序排列
    Set<DiscussPost> posts = redisTemplate.opsForZSet().reverseRange(redisKey, 0, -1);

    // 将结果转换为List
    List<DiscussPost> likePosts = new ArrayList<>();
    if (posts != null) {
        likePosts.addAll(posts);
    }

    return likePosts;
}
```

3. UserController新增：

```java
@RequestMapping(path = "/mylikes/{userId}", method = RequestMethod.GET)
public String getMyLikes(@PathVariable("userId") int userId, Page page, Model model) {
    User user = userService.findUserById(userId);
    if (user == null) {
        throw new RuntimeException("该用户不存在！");
    }
    model.addAttribute("user", user);

    List<DiscussPost> discussList = likeService.findUserLikePosts(userId);
    // 分页信息
    page.setLimit(10);
    page.setPath("/user/mylikes/" + userId);
    page.setRows(discussList.size());
    
    List<Map<String, Object>> discussVOList = new ArrayList<>();
    if (discussList != null) {
        for (DiscussPost post : discussList) {
            Map<String, Object> map = new HashMap<>();
            map.put("discussPost", post);
            map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
            discussVOList.add(map);
        }
    }
    model.addAttribute("discussPosts", discussVOList);

    return "/site/my-likes";
}
```

4. 新建前端页面my-likes:

```html
<!doctype html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    <meta name="_csrf" th:content="${_csrf.token}">
    <meta name="_csrf_header" th:content="${_csrf.headerName}">
    <link rel="icon" th:href="@{/img/icon.png}"/>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css" crossorigin="anonymous">
    <link rel="stylesheet" th:href="@{/css/global.css}" />
    <title>个人主页</title>
</head>
<body>
<div class="nk-container">
    <!-- 头部 -->
    <header class="bg-dark sticky-top" th:replace="index::header">
    </header>

    <!-- 内容 -->
    <div class="main">
        <div class="container">
            <!-- 选项 -->
            <div class="position-relative">
                <ul class="nav nav-tabs">
                    <li class="nav-item">
                        <a class="nav-link" th:href="@{|/user/profile/${user.id}|}">个人信息</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" th:href="@{|/user/mypost/${user.id}|}" th:text="${loginUser==null||loginUser.id!=user.id?'TA的帖子':'我的帖子'}">我的帖子</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" th:href="@{|/user/myreply/${user.id}|}" th:text="${loginUser==null||loginUser.id!=user.id?'TA的回复':'我的回复'}">我的回复</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link active" th:href="@{|/user/mylikes/${user.id}|}" th:text="${loginUser==null||loginUser.id!=user.id?'TA赞过的':'我赞过的'}"></a>
                    </li>
                </ul>
                <a th:href="@{|/user/profile/${user.id}|}" class="text-muted position-absolute rt-0">返回个人主页&gt;</a>
            </div>
            <!-- 判空 -->
            <div th:if="${#lists.isEmpty(discussPosts)}">
                <img th:src="@{/img/noResult.png}" alt="无私信" class="img-fluid mx-auto d-block mt-4">
                <p class="text-center mt-3">还没有赞过帖子哦~</p>
            </div>
            <!-- 我的帖子 -->
            <div class="mt-4">
                <h6><b class="square"></b> 赞过的帖子(<i th:text="${page.rows}">93</i>)</h6>
                <ul class="list-unstyled mt-4 pl-3 pr-3">
                    <li class="border-bottom pb-3 mt-4" th:each="map:${discussPosts}">
                        <div class="font-size-16 text-info">
                            <a th:href="@{|/discuss/detail/${map.discussPost.id}|}" th:utext="${map.discussPost.title}" class="text-info">备战春招，面试刷题跟他复习，一个月全搞定！</a>
                        </div>
                        <div class="mt-1 font-size-14" th:utext="${map.discussPost.content}">
                            金三银四的金三已经到了，你还沉浸在过年的喜悦中吗？
                            如果是，那我要让你清醒一下了：目前大部分公司已经开启了内推，正式网申也将在3月份陆续开始，金三银四，春招的求职黄金时期已经来啦！！！
                            再不准备，作为19应届生的你可能就找不到工作了。。。作为20届实习生的你可能就找不到实习了。。。
                            现阶段时间紧，任务重，能做到短时间内快速提升的也就只有算法了，
                            那么算法要怎么复习？重点在哪里？常见笔试面试算法题型和解题思路以及最优代码是怎样的？
                            跟左程云老师学算法，不仅能解决以上所有问题，还能在短时间内得到最大程度的提升！！！
                        </div>
                        <div class="text-right font-size-12 text-muted">
                            赞 <i class="mr-3" th:text="${map.likeCount}">11</i> 发布于 <b th:text="${#dates.format(map.discussPost.createTime,'yyyy-MM-dd HH:mm:ss')}">2019-04-15 10:10:10</b>
                        </div>
                    </li>
                </ul>
                <!-- 分页 -->
                <nav class="mt-5" th:replace="index::pagination">
                </nav>
            </div>
        </div>
    </div>

    <!-- 尾部 -->
    <footer class="bg-dark" th:replace="index::footer">
    </footer>
</div>

<script src="https://code.jquery.com/jquery-3.3.1.min.js" crossorigin="anonymous"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js" crossorigin="anonymous"></script>
<script src="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/js/bootstrap.min.js" crossorigin="anonymous"></script>
<script th:src="@{/js/global.js}"></script>
</body>
</html>
```

并在其他页面增加超链接

```html
<li class="nav-item">
    <a class="nav-link" th:href="@{|/user/mylikes/${user.id}|}" th:text="${loginUser==null||loginUser.id!=user.id?'TA赞过的':'我赞过的'}"></a>
</li>
```

## BUG —— Redis和MySQl的数据不一致问题

当用户A对帖子a点赞时，a加入了redis，记redis中为a1，这时的帖子相当于锁死了，当我们对帖子进行点赞、删除、加精时，帖子的属性会有变化，记为a2，那么显然 a1≠a2，我们需要修复这个bug

### 思路——重构redisKey，改为存帖子id而非帖子，之后根据id实时更新数据

1. redisKey重构：

```java
// 某个用户赞了的帖子
    // like:post:userid -> Zset(postId, likeTime)
    public static String getUserPostKey(int userId){
        return PREFIX_LIKE_POST + SPLIT + userId;
    }
```

2. likeService改动：

```java
if(isMember){
    operations.opsForSet().remove(entityLikeKey, userId);
    operations.opsForValue().decrement(userLikeKey);
    if (entityType == ENTITY_TYPE_POST) {
        DiscussPost post = discussPostService.findDiscussPostById(entityId);
        operations.opsForZSet().remove(userPostKey, post.getId());
    }
}else{
    operations.opsForSet().add(entityLikeKey, userId);
    operations.opsForValue().increment(userLikeKey);
    if (entityType == ENTITY_TYPE_POST) {
        DiscussPost post = discussPostService.findDiscussPostById(entityId);
        operations.opsForZSet().add(userPostKey, post.getId(), System.currentTimeMillis());
    }
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
```

3. UserController改动：

```java
@RequestMapping(path = "/mylikes/{userId}", method = RequestMethod.GET)
public String getMyLikes(@PathVariable("userId") int userId, Page page, Model model) {
    User user = userService.findUserById(userId);
    if (user == null) {
        throw new RuntimeException("该用户不存在！");
    }
    model.addAttribute("user", user);

    // 获取用户点赞的帖子列表
    List<DiscussPost> discussList = likeService.findUserLikePosts(userId);

    // 分页信息
    page.setLimit(10);
    page.setPath("/user/mylikes/" + userId);
    page.setRows(discussList.size());

    List<Map<String, Object>> discussVOList = new ArrayList<>();
    if (discussList != null) {
        for (DiscussPost post : discussList) {
            Map<String, Object> map = new HashMap<>();
            map.put("discussPost", post);
            map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
            discussVOList.add(map);
        }
    }
    model.addAttribute("discussPosts", discussVOList);

    return "/site/my-likes";
}
```