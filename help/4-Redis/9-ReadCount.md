# Redis实现文章访问量统计

思路，存取 文章id——访问数，访问文章详情时++

1. redisKeyUtil新增：

```java
private static final String PREFIX_POST_READ = "post:read";

// 统计帖子阅读量
// post:read:postId -> int
public static String getPostReadKey(int postId){
    return PREFIX_POST_READ + SPLIT + postId;
}
```

2. DiscussPostService新增：

```java
public void updatePostReadCount(int postId){
    String redisKey = RedisKeyUtil.getPostReadKey(postId);
    redisTemplate.opsForValue().increment(redisKey);
}
```

3. DiscussPostController新增：

```java
//帖子详情
@RequestMapping(path = "/detail/{discussPostId}", method = RequestMethod.GET)
public String getDiscussPost(@PathVariable("discussPostId") int discussPostId, Model model, Page page){
    //帖子
    DiscussPost discussPost = discussPostService.findDiscussPostById(discussPostId);
    String content = HtmlUtils.htmlUnescape(discussPost.getContent()); // 内容反转义，不然 markdown 格式无法显示
    discussPost.setContent(content);
    model.addAttribute("post", discussPost);

    //作者
    User user = userService.findUserById(discussPost.getUserId());
    model.addAttribute("user", user);

    // 赞
    long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, discussPostId);
    model.addAttribute("likeCount", likeCount);
    int likeStatus = hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_POST, discussPostId);
    model.addAttribute("likeStatus", likeStatus);

    //评论分页信息
    page.setLimit(5);
    page.setPath("/discuss/detail/" + discussPostId);
    page.setRows(discussPost.getCommentCount());
    List<Comment> commentList = commentService.
            findCommentsByEntity(ENTITY_TYPE_POST, discussPost.getId(), page.getOffset(), page.getLimit());

    //找到评论的用户
    List<Map<String, Object>> commentVoList = new ArrayList<>();  // Vo = view objects 显示对象
    if(commentList != null){
        for(Comment comment : commentList){
            Map<String, Object> commentVo = new HashMap<>();
            // 评论
            commentVo.put("comment", comment);
            // 作者
            commentVo.put("user", userService.findUserById(comment.getUserId()));
            // 赞
            likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, comment.getId());
            commentVo.put("likeCount", likeCount);
            likeStatus = hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, comment.getId());
            commentVo.put("likeStatus", likeStatus);

            //评论的评论——回复
             List<Comment> replyList = commentService.
                    findCommentsByEntity(ENTITY_TYPE_COMMENT,
                            comment.getId(), 0, Integer.MAX_VALUE);  // 回复就不需要分页了，就一页显示所有评论

            //找到回复的用户
            List<Map<String, Object>> replyVoList = new ArrayList<>();
            if(replyList != null){
                for(Comment reply : replyList){
                    Map<String, Object> replyVo = new HashMap<>();
                    // 回复
                    replyVo.put("reply", reply);
                    // 作者
                    replyVo.put("user", userService.findUserById(reply.getUserId()));
                    // 赞
                    likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, reply.getId());
                    replyVo.put("likeCount", likeCount);
                    likeStatus = hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, reply.getId());
                    replyVo.put("likeStatus", likeStatus);

                    //回复的目标
                    User target = reply.getTargetId() == 0 ? null : userService.findUserById(reply.getTargetId());

                    replyVo.put("target", target);
                    replyVoList.add(replyVo);
                }
            }
            commentVo.put("replys", replyVoList);

            int replycnt = commentService.findCommentCount(ENTITY_TYPE_COMMENT, comment.getId());
            commentVo.put("replyCount", replycnt);

            commentVoList.add(commentVo);
        }
    }

    model.addAttribute("comments", commentVoList);
    
    // 增加帖子访问量
    discussPostService.updatePostReadCount(discussPostId);
    String redisKey = RedisKeyUtil.getPostReadKey(discussPostId);
    model.addAttribute("postReadCount", redisTemplate.opsForValue().get(redisKey));
    
    return "/site/discuss-detail";
}
```

HomeController新增：

```java
@Autowired
private RedisTemplate redisTemplate;

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

            String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
            map.put("postReadCount", redisTemplate.opsForValue().get(redisKey));

            discussPosts.add(map);
        }
    }
    // 处理完的数据填充给前端页面
    model.addAttribute("discussPosts", discussPosts);
    model.addAttribute("orderMode", orderMode);
    return "/index";
}
```

SearchController新增：

```java
@Autowired
private RedisTemplate redisTemplate;

// 路径格式：/search?keyword=xxx
@RequestMapping(path = "/search", method = RequestMethod.GET)
public String search(String keyword, Page page, Model model) {
    // 搜索帖子
    org.springframework.data.domain.Page<DiscussPost> searchResult =
            elasticsearchService.searchDiscussPost(keyword, page.getCurrent() - 1, page.getLimit());

    List<Map<String, Object>> discussPosts = new ArrayList<>();
    if (searchResult != null) {
        for (DiscussPost post : searchResult) {
            Map<String, Object> map = new HashMap<>();
            String contentPlainText = extractPlainText(post.getContent());

            map.put("post", post);
            map.put("user", userService.findUserById(post.getUserId()));  // 作者
            map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));  // 赞数
            map.put("contentPlainText", contentPlainText);  // Processed plain text content

            String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
            map.put("postReadCount", redisTemplate.opsForValue().get(redisKey));

            discussPosts.add(map);
        }
    }
    model.addAttribute("discussPosts", discussPosts);
    model.addAttribute("keyword", keyword);

    page.setPath("/search?keyword=" + keyword);
    page.setRows(searchResult == null ? 0 : (int) searchResult.getTotalElements());

    return "/site/search";
}
```

UserController新增;

```java
@Autowired
private RedisTemplate redisTemplate;

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
            .findDiscussPosts(userId, page.getOffset(), page.getLimit(), 0);
    List<Map<String, Object>> discussVOList = new ArrayList<>();
    if (discussList != null) {
        for (DiscussPost post : discussList) {
            Map<String, Object> map = new HashMap<>();
            map.put("post", post);
            map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
            String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
            map.put("postReadCount", redisTemplate.opsForValue().get(redisKey));
            discussVOList.add(map);
        }
    }
    model.addAttribute("discussPosts", discussVOList);

    return "/site/my-post";
}

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
            map.put("post", post);
            map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
            String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
            map.put("postReadCount", redisTemplate.opsForValue().get(redisKey));
            discussVOList.add(map);
        }
    }
    model.addAttribute("discussPosts", discussVOList);

    return "/site/my-likes";
}
```

4. 前端：

index:

```html
<li class="d-inline ml-2"><span th:text="${map.postReadCount}"></span> 浏览</li>
<li class="d-inline ml-2">|</li>
```

discuss-detail:

```html
<li class="d-inline ml-2">
    <a href="#" class="text-primary"><span th:text="${postReadCount}"></span> 浏览</a></li>
<li class="d-inline ml-2">|</li>
```

search:

```html
<li class="d-inline ml-2"><span th:text="${map.postReadCount}"></span> 浏览</li>
<li class="d-inline ml-2">|</li>
```

my-post:

```html
看过 <i class="mr-3" th:text="${map.postReadCount}"></i>
```

my-likes:

```html
看过 <i class="mr-3" th:text="${map.postReadCount}"></i>
```