# 关注列表

- 业务层
  - 查询某个用户关注的人，支持分页
  - 查询某个用户的粉丝，支持分页
- 表现层
  - 处理`查询关注的人`、`查询粉丝`请求
  - 编写`查询关注的人`、`查询粉丝`模板。

## 代码实现

1. service

在FollowService中新增：

```java
public class FollowService implements CommunityConstant{
  @Autowired
  private UserService userService;

  // 查询用户的关注 followee
  public List<Map<String, Object>>  findFollowees(int userId, int offset, int limit){
    String followeeKey = RedisKeyUtil.getFolloweeKey(userId, ENTITY_TYPE_USER);
    // 按关注时间倒序，最新的在最上面
    // 从每个分页的起始开始查，查到本分页结束
    Set<Integer> targetIds = redisTemplate.opsForZSet().reverseRange(followeeKey, offset, offset + limit - 1);
    if(targetIds == null){
      return null;
    }

    List<Map<String, Object>> list = new ArrayList<>();
    for(Integer id : targetIds){
      Map<String, Object> map = new HashMap<>();
      User user = userService.findUserById(id);
      map.put("user", user);
      // 得到的是秒数 System.currentTimeMillis() , 转成标准日期
      Double score = redisTemplate.opsForZSet().score(followeeKey, id);
      map.put("followTime", new Date(score.longValue()));
      list.add(map);
    }
    return list;
  }

  // 查询用户的粉丝  follower
  public List<Map<String, Object>>  findFollowers(int userId, int offset, int limit){
    String followerKey = RedisKeyUtil.getFollowerKey(ENTITY_TYPE_USER, userId);
    Set<Integer> targetIds = redisTemplate.opsForZSet().reverseRange(followerKey, offset, offset + limit - 1);
    if(targetIds == null){
      return null;
    }

    List<Map<String, Object>> list = new ArrayList<>();
    for(Integer id : targetIds){
      Map<String, Object> map = new HashMap<>();
      User user = userService.findUserById(id);
      map.put("user", user);
      Double score = redisTemplate.opsForZSet().score(followerKey, id);
      map.put("followTime", new Date(score.longValue()));
      list.add(map);
    }
    return list;
  }
}
```

2. controller

在FollowController新增：

```java
public class FollowController implements CommunityConstant{
  @Autowired
  private UserService userService;

  @RequestMapping(path = "/followees/{userId}", method = RequestMethod.GET)
  public String getFollowees(@PathVariable("userId")int userId, Page page, Model model){
    User user = userService.findUserById(userId);
    if(user == null){
      throw new RuntimeException("该用户不存在！");
    }
    model.addAttribute("user", user);

    page.setLimit(5);
    page.setPath("/followees/" + userId);
    page.setRows((int)followService.findFolloweeCount(userId,CommunityConstant.ENTITY_TYPE_USER));

    List<Map<String, Object>> userList = followService.findFollowees(userId, page.getOffset(), page.getLimit());

    // 补充业务：A->B, 此时C在看A的关注列表，如果C没有关注B，C可以摁关注按钮
    if(userList != null){
      for(Map<String, Object> map : userList){
        User u = (User) map.get("user");
        map.put("hasFollowed", hasFollowed(u.getId()));
      }
    }

    model.addAttribute("users", userList);

    return "/site/followee";
  }

  // 单独封装是否关注
  private boolean hasFollowed(int userId){
    if(hostHolder.getUser() == null){
      return  false;  // 没登录就一定没关注
    }
    return followService.hasFollowed(hostHolder.getUser().getId(),ENTITY_TYPE_USER, userId);
  }

  @RequestMapping(path = "/followers/{userId}", method = RequestMethod.GET)
  public String getFollowers(@PathVariable("userId")int userId, Page page, Model model){
    User user = userService.findUserById(userId);
    if(user == null){
      throw new RuntimeException("该用户不存在！");
    }
    model.addAttribute("user", user);

    page.setLimit(5);
    page.setPath("/followers/" + userId);
    page.setRows((int)followService.findFollowerCount(ENTITY_TYPE_USER, userId));

    List<Map<String, Object>> userList = followService.findFollowers(userId, page.getOffset(), page.getLimit());
    if(userList != null){
      for(Map<String, Object> map : userList){
        User u = (User) map.get("user");
        map.put("hasFollowed", hasFollowed(u.getId()));
      }
    }

    model.addAttribute("users", userList);

    return "/site/follower";
  }
}
```

3. 前端

a. profile.html中增加超链接

```html
<span>关注了 <a class="text-primary" th:href="@{|/followees/${user.id}|}" th:text="${followeeCount}">5</a> 人</span>
<span class="ml-4">关注者 <a class="text-primary" th:href="@{|/followers/${user.id}|}" th:text="${followerCount}">123</a> 人</span>
```

b. followee.html

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

  <!-- 选项 -->
  <ul class="nav nav-tabs mb-3">
    <li class="nav-item">
      <a class="nav-link position-relative active" th:href="@{|/followees/${user.id}|}"><i class="text-info" th:utext="${user.username}">Nowcoder</i> 关注的人</a>
    </li>
    <li class="nav-item">
      <a class="nav-link position-relative" th:href="@{|/followers/${user.id}|}">关注 <i class="text-info" th:utext="${user.username}">Nowcoder</i> 的人</a>
    </li>
  </ul>
  <a th:href="@{|/user/profile/${user.id}|}" class="text-muted position-absolute rt-0">返回个人主页&gt;</a>

  <!-- 关注列表 -->
  <ul class="list-unstyled">
    <li class="media pb-3 pt-3 mb-3 border-bottom position-relative" th:each="map:${users}">
      <a th:href="@{|/user/profile/${map.user.id}|}">
        <img th:src="${map.user.headerUrl}" class="mr-4 rounded-circle user-header" alt="用户头像" >
      </a>
      <div class="media-body">
        <h6 class="mt-0 mb-3">
          <span class="text-success" th:utext="${map.user.username}">落基山脉下的闲人</span>
          <span class="float-right text-muted font-size-12">关注于 
									<i th:text="${#dates.format(map.followTime, 'yyyy-MM-dd HH:mm:ss')}">2019-04-28 14:13:25</i>
								</span>
        </h6>
        <div>
          <input type="hidden" id="entityId" th:value="${map.user.id}">
          <button type="button"
                  th:class="|btn ${map.hasFollowed?'btn-secondary':'btn-info'} btn-sm float-right mr-5 follow-btn|"
                  th:text="${map.hasFollowed?'已关注':'关注TA'}"
                  th:if="${loginUser!=null && loginUser.id!=map.user.id}"
          >关注TA</button>
        </div>
      </div>
    </li>
  </ul>

<nav class="mt-5" th:replace="index::pagination">
  
<script th:src="@{/js/global.js}"></script>
<script th:src="@{/js/profile.js}"></script>
```

c. 同理改follower.html

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

  <!-- 选项 -->
  <ul class="nav nav-tabs mb-3">
    <li class="nav-item">
      <a class="nav-link position-relative" th:href="@{|/followees/${user.id}|}"><i class="text-info" th:utext="${user.username}">Nowcoder</i> 关注的人</a>
    </li>
    <li class="nav-item">
      <a class="nav-link position-relative active" th:href="@{|/followers/${user.id}|}">关注 <i class="text-info" th:utext="${user.username}">Nowcoder</i> 的人</a>
    </li>
  </ul>
  <a th:href="@{|/user/profile/${user.id}|}" class="text-muted position-absolute rt-0">返回个人主页&gt;</a>
  </div>

  <!-- 粉丝列表 -->
  <ul class="list-unstyled">
    <li class="media pb-3 pt-3 mb-3 border-bottom position-relative" th:each="map:${users}">
      <a th:href="@{|/user/profile/${map.user.id}|}">
        <img th:src="${map.user.headerUrl}" class="mr-4 rounded-circle user-header" alt="用户头像" >
      </a>
      <div class="media-body">
        <h6 class="mt-0 mb-3">
          <span class="text-success" th:utext="${map.user.username}">落基山脉下的闲人</span>
          <span class="float-right text-muted font-size-12">关注于 
									<i th:text="${#dates.format(map.followTime, 'yyyy-MM-dd HH:mm:ss')}">2019-04-28 14:13:25</i>
								</span>
        </h6>
        <div>
          <input type="hidden" id="entityId" th:value="${map.user.id}">
          <button type="button"
                  th:class="|btn ${map.hasFollowed?'btn-secondary':'btn-info'} btn-sm float-right mr-5 follow-btn|"
                  th:text="${map.hasFollowed?'已关注':'关注TA'}"
                  th:if="${loginUser!=null && loginUser.id!=map.user.id}"
          >关注TA</button>
        </div>
      </div>
    </li>
  </ul>

<nav class="mt-5" th:replace="index::pagination">
  
<script th:src="@{/js/global.js}"></script>
<script th:src="@{/js/profile.js}"></script>
```