# 自己实现——根据用户名搜索用户

1. es的建表：

在User实体中：

```java
@Document(indexName = "username")
public class User {
    @Id
    private int id;
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String username;
    private String password;
    private String salt;
    private String email;
    @Field(type = FieldType.Integer)
    private int type;
    private int status;
    private String activationCode;
    @Field(type = FieldType.Text)
    private String headerUrl;
    @Field(type = FieldType.Date)
    private Date createTime;
}
```

2. dao

新建接口UserRepository:

```java
package com.nowcoder.community.dao.elasticsearch;

import com.nowcoder.community.entity.User;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends ElasticsearchRepository<User,Integer> {
}
```

3. service

在ElasticsearchService中新增：

```java
@Autowired
private UserRepository userRepository;

public void saveUser(User user){userRepository.save(user);}

public void deleteUser(int id){userRepository.deleteById(id);}

public Page<User> searchUser(String keyword, int current, int limit){
    String wildcardKeyword = "*" + keyword + "*";
    Criteria criteria = new Criteria("username").expression(wildcardKeyword);

    List<HighlightField> highlightFieldList = new ArrayList<>();
    HighlightField highlightField = new HighlightField("username", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
    highlightFieldList.add(highlightField);
    Highlight highlight = new Highlight(highlightFieldList);
    HighlightQuery highlightQuery = new HighlightQuery(highlight, User.class);

    CriteriaQueryBuilder builder = new CriteriaQueryBuilder(criteria)
            .withSort(Sort.by(Sort.Direction.DESC, "type"))
            .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
            .withHighlightQuery(highlightQuery)
            .withPageable(PageRequest.of(current, limit));
    CriteriaQuery query = new CriteriaQuery(builder);

    SearchHits<User> result = elasticTemplate.search(query, User.class);
    if(result.isEmpty()){
        return null;
    }

    List<SearchHit<User>> searchHitList = result.getSearchHits();
    List<User> UserList = new ArrayList<>();
    for (SearchHit<User> hit : searchHitList) {
        User user = hit.getContent();
        var usernameHighlight = hit.getHighlightField("username");
        if (usernameHighlight.size() != 0) {
            // 手动高亮匹配部分
            String originalUsername = user.getUsername();
            String highlightedUsername = highlightMatch(originalUsername, keyword);
            user.setUsername(highlightedUsername);
        }
        UserList.add(user);
    }

    return new PageImpl<>(UserList, PageRequest.of(current, limit), result.getTotalHits());
}

private String highlightMatch(String text, String keyword) {
    // 使用正则表达式手动高亮匹配部分
    String patternString = "(?i)(" + Pattern.quote(keyword) + ")";
    Pattern pattern = Pattern.compile(patternString);
    Matcher matcher = pattern.matcher(text);
    StringBuffer highlightedText = new StringBuffer();

    while (matcher.find()) {
        matcher.appendReplacement(highlightedText, "<em>" + matcher.group(1) + "</em>");
    }
    matcher.appendTail(highlightedText);

    return highlightedText.toString();
}
```

并更新之前的搜索帖子：

```java
// 搜索，可以复用上次的test代码
public Page<DiscussPost> searchDiscussPost(String keyword, int current, int limit) {
    // 使用 wildcard 查询进行模糊匹配
    String wildcardKeyword = "*" + keyword + "*";
    Criteria criteria = new Criteria("title").expression(wildcardKeyword)
            .or(new Criteria("content").expression(wildcardKeyword));

    // 这里的高亮格式还是<em></em>，但是前端的global.css已经处理为了高亮红色
    List<HighlightField> highlightFieldList = new ArrayList<>();
    HighlightField highlightField = new HighlightField("title", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
    highlightFieldList.add(highlightField);
    highlightField = new HighlightField("content", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
    highlightFieldList.add(highlightField);
    Highlight highlight = new Highlight(highlightFieldList);
    HighlightQuery highlightQuery = new HighlightQuery(highlight, DiscussPost.class);

    CriteriaQueryBuilder builder = new CriteriaQueryBuilder(criteria)
            .withSort(Sort.by(Sort.Direction.DESC, "type"))
            .withSort(Sort.by(Sort.Direction.DESC, "score"))
            .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
            .withHighlightQuery(highlightQuery)
            .withPageable(PageRequest.of(current, limit));
    CriteriaQuery query = new CriteriaQuery(builder);

    SearchHits<DiscussPost> result = elasticTemplate.search(query, DiscussPost.class);
    if (result.isEmpty()) {
        return null;
    }

    List<SearchHit<DiscussPost>> searchHitList = result.getSearchHits();
    List<DiscussPost> discussPostList = new ArrayList<>();
    for (SearchHit<DiscussPost> hit : searchHitList) {
        DiscussPost post = hit.getContent();
        var titleHighlight = hit.getHighlightField("title");
        if (titleHighlight.size() != 0) {
            String originalTitle = post.getTitle();
            String highlightedTitle = highlightMatch(originalTitle, keyword);
            post.setTitle(highlightedTitle);
        }
        var contentHighlight = hit.getHighlightField("content");
        if (contentHighlight.size() != 0) {
            String originalContent = post.getContent();
            String highlightedContent = highlightMatch(originalContent, keyword);
            post.setContent(highlightedContent);
        }
        discussPostList.add(post);
    }

    return new PageImpl<>(discussPostList, PageRequest.of(current, limit), result.getTotalHits());
}
```

3. 常量接口新增：

```java
// 新增用户
String TOPIC_REGISTER = "register";
// 用户信息更新
String TOPIC_UPDATE = "update";
```

在激活成功处新增“新增用户”事件

```java
@Autowired
private EventProducer eventProducer;

@RequestMapping(path = "/activation/{userId}/{code}", method = RequestMethod.GET)
public String activation(Model model, @PathVariable("userId") int userId, @PathVariable("code") String code) {
    int result = userService.activation(userId, code);
    if (result == ACTIVATION_SUCCESS) {
        model.addAttribute("msg", "激活成功，您的账号已经可以正常使用了！");
        model.addAttribute("target", "/login");

        Event event = new Event()
                .setTopic(TOPIC_REGISTER)
                .setUserId(userId);
        eventProducer.fireEvent(event);
    }
    //....其余代码省略
}
```

在修改头像、修改用户名处新增“更新用户事件”

```java
@Autowired
private EventProducer eventProducer;

// 更新头像路径
@RequestMapping(path = "/header/url", method = RequestMethod.POST)
@ResponseBody
public String updateHeaderUrl(String fileName){
    if(fileName == null){
        return CommunityUtil.getJSONString(1, "文件名为空！");
    }
    String url = headerBucketUrl + "/" + fileName;
    userService.updateHeader(hostHolder.getUser().getId(), url);

    Event event = new Event()
            .setTopic(TOPIC_UPDATE)
            .setUserId(hostHolder.getUser().getId());
    eventProducer.fireEvent(event);

    return CommunityUtil.getJSONString(0);
}

@LoginRequired
@RequestMapping(path = "/updateUsername", method = RequestMethod.POST)
public String updateUsername(String username,Model model){
    User user = hostHolder.getUser();
    Map<String, Object> map = userService.updateUsername(user.getId(), username);
    if (map == null || map.isEmpty()) {
        Event event = new Event()
                .setTopic(TOPIC_UPDATE)
                .setUserId(user.getId());
        eventProducer.fireEvent(event);
        return "redirect:/logout";
    } else {
        model.addAttribute("errorMsg", map.get("errorMsg"));
        return "/site/setting";
    }
}
```

4. 消费两个新事件

```java
@Autowired
private UserService userService;

// 消费用户事件
@KafkaListener(topics = {TOPIC_REGISTER, TOPIC_UPDATE})
public void handleUserMessage(ConsumerRecord record){
    if(record == null || record.value() == null){
        logger.error("消息的内容为空！");
    }
    Event event = JSONObject.parseObject(record.value().toString(), Event.class);
    if(event == null){
        logger.error("消息格式错误！");
    }
    User user = userService.findUserById(event.getUserId());
    elasticsearchService.saveUser(user);
}
```

5. SearchController更新：

```java
@RequestMapping(path = "/searchUser", method = RequestMethod.GET)
public String searchUser(String username, Page page, Model model){
    // 搜索用户
    org.springframework.data.domain.Page<User> searchResult =
            elasticsearchService.searchUser(username, page.getCurrent() - 1, page.getLimit());

    List<Map<String, Object>> Users = new ArrayList<>();
    if(searchResult != null){
        for(User user : searchResult){
            Map<String, Object> map = new HashMap<>();

            map.put("user", user);
            map.put("uid", user.getId());
            map.put("username", user.getUsername());
            map.put("headerUrl", user.getHeaderUrl());
            map.put("createTime", user.getCreateTime());
            map.put("type", user.getType());
            
            Users.add(map);
        }
    }
    model.addAttribute("Users", Users);
    model.addAttribute("name", username);

    page.setPath("/searchUser?name=" + username);
    page.setRows(searchResult == null ? 0 : (int)searchResult.getTotalElements());

    return "/site/searchUser";
}
```

6. 新建页面searchUser

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
    <title>搜索结果</title>
</head>
<body>
<div class="nk-container">
    <!-- 头部 -->
    <header class="bg-dark sticky-top" th:replace="index::header">
    </header>

    <!-- 内容 -->
    <div class="main">
        <div class="container">
            <h6><b class="square"></b> 相关用户</h6>
            <!-- 判断搜索结果是否为空 -->
            <div th:if="${#lists.isEmpty(Users)}">
                <img th:src="@{/img/noResult.png}" alt="无搜索结果" class="img-fluid mx-auto d-block mt-4">
                <p class="text-center mt-3">没有找到相关用户呢~ 请尝试其他关键词！</p>
            </div>
            <!-- 用户列表 -->
            <ul class="list-unstyled mt-4">
                <li class="media pb-3 pt-3 mb-3 border-bottom" th:each="map:${Users}">
                    <a th:href="@{|/user/profile/${map.uid}|}">
                    <img th:src="${map.headerUrl}" class="mr-4 rounded-circle" alt="用户头像" style="width: 50px;height: 50px">
                    </a>
                    <div class="media-body">
                        <h6 class="mt-0 mb-3">
                            <a th:href="@{|/user/profile/${map.uid}|}" th:utext="${map.username}">备战<em>春招</em>，面试刷题跟他复习，一个月全搞定！</a>
                        </h6>
                        <span class="media-body">
                            uid: <span th:text="${map.uid}"></span>
                        </span>
                        <span style="margin-left: 100px;">
                            注册于 <span th:text="${#dates.format(map.createTime, 'yyyy-MM-dd HH:mm:ss')}"></span>
                        </span>
                    </div>
                    <div class="status-badge" th:if="${map.type==0}"
                         style="background-color: rgb(51, 133, 255);font-size: 14px;color: white;padding: 5px;border-radius: 10px;user-select: none">
                        普通用户
                    </div>
                    <div class="status-badge" th:if="${map.type==1}"
                         style="background-color: rgb(51, 133, 255);font-size: 14px;color: white;padding: 5px;border-radius: 10px;user-select: none">
                        管理员
                    </div>
                    <div class="status-badge" th:if="${map.type==2}"
                         style="background-color: rgb(51, 133, 255);font-size: 14px;color: white;padding: 5px;border-radius: 10px;user-select: none">
                        版主
                    </div>
                </li>
            </ul>
            <!-- 分页 -->
            <nav class="mt-5" th:replace="index::pagination">
            </nav>
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

index增加：

```html
<!-- 搜索帖子 -->
<form class="form-inline my-2 my-lg-0" th:action="@{/search}" method="get">
    <input class="form-control mr-sm-2 short-search" type="search" aria-label="Search" name="keyword" id="keyword" th:value="${keyword}" required style="width: 150px;"/>
    <button class="btn btn-outline-light my-2 my-sm-0" type="submit">搜帖子</button>
</form>
<!--搜索用户-->
<form class="form-inline my-2 my-lg-0" th:action="@{/searchUser}" method="get">
    <input class="form-control mr-sm-2 short-search" type="search" aria-label="Search" name="username" id="username" th:value="${name}" required style="width: 150px;margin-left: 10px;"/>
    <button class="btn btn-outline-light my-2 my-sm-0" type="submit">找用户</button>
</form>
```