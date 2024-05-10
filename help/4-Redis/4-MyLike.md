# 我收到的赞

- 重构点赞功能
  - 以用户为key，记录点赞数量
  - increment(key), decrement(key)
- 开发个人主页
  - 以用户为key，查询点赞数量

## 重构点赞

1. 在RedisKeyUtil新增：

```java
private static final String PREFIX_USER_LIKE = "like:user";

// 某个用户收到的赞
// like:user:userId -> int
public static String getUserLikeKey(int userId){
    return PREFIX_USER_LIKE + SPLIT + userId;
}
```

2. 改动LikeService:

```java
// 重构like()
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

      boolean isMember = operations.opsForSet().isMember(entityLikeKey, userId);  // 查询放在事务之外

      operations.multi();
      // 未赞过，实体赞和用户收到的赞同步增加，否则同步减少
      if(isMember){
        operations.opsForSet().remove(entityLikeKey, userId);
        operations.opsForValue().decrement(userLikeKey);
      }else{
        operations.opsForSet().add(entityLikeKey, userId);
        operations.opsForValue().increment(userLikeKey);
      }

      operations.exec();

      return null;
    }
  });
}

// 查询一个用户收到的赞
public int findUserLikeCount(int userId){
  String userLikeKey = RedisKeyUtil.getUserLikeKey(userId);
  Integer count = (Integer)redisTemplate.opsForValue().get(userLikeKey);
  return count == null ? 0 : count.intValue();
}
```

3. 改动LikeController:

```java
public String like(int entityType, int entityId, int entityUserId) {
    likeService.like(user.getId(), entityType, entityId, entityUserId);
}        
```

4. discuss-detail前端改动

```html
<a href="javascript:;" th:onclick="|like(this, 1, ${post.id}, ${post.userId});|" class="text-primary">

<a href="javascript:;" th:onclick="|like(this, 2 , ${cvo.comment.id}, ${cvo.comment.userId});|" class="text-primary">

<a href="javascript:;" th:onclick="|like(this, 2, ${rvo.reply.id}, ${rvo.reply.userId});|" class="text-primary">
```

修改like():

```javascript
function like(btn, entityType, entityId, entityUserId){
    $.post(
        CONTEXT_PATH + "/like",
        {"entityType":entityType, "entityId":entityId, "entityUserId":entityUserId},
        function (data){
            data = $.parseJSON(data);
            if(data.code == 0){
                $(btn).children("i").text(data.likeCount);
                $(btn).children("b").text(data.likeStatus == 1 ? "已赞" : "赞");
            }else{
                alert(data.msg);
            }
        }
    )
}
```

## 个人主页初步开发

1. 合并为UserController

```java
@Autowired
private LikeService likeService;

// 个人主页
@RequestMapping(path = "/profile/{userId}", method = RequestMethod.GET)
public String getProfilePage(@PathVariable("userId") int userId, Model model){
    User user = userService.findUserById(userId);
    if(user == null){
        throw new RuntimeException("该用户不存在！");
    }
    // 用户
    model.addAttribute("user", user);
    // 获赞
    int likeCount = likeService.findUserLikeCount(userId);
    model.addAttribute("likeCount", likeCount);
        
    return "/site/profile";
}
```

2. profile.html:

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

  <img th:src="${user.headerUrl}" class="align-self-start mr-4 rounded-circle" alt="用户头像" style="width:50px;">

  <span th:utext="${user.username}">nowcoder</span>

  <span>注册于 <i class="text-muted" th:text="${#dates.format(user.createTime, 'yyyy-MM-dd HH:mm:ss')}">2015-06-12 15:20:12</i></span>

  <span class="ml-4">获得了 <i class="text-danger" th:text="${likeCount}">87</i> 个赞</span>

<script th:src="@{/js/global.js}"></script>
<script th:src="@{/js/profile.js}"></script>
```

index 超链接：

```html
<a class="dropdown-item text-center" th:href="@{|/user/profile/${loginUser.id}|}">个人主页</a>

<a th:href="@{|/user/profile/${map.user.id}|}">
  <img th:src="${map.user.headerUrl}" class="mr-4 rounded-circle" alt="用户头像" style="width:50px;height:50px;">
</a>
```

discuss-detail 超链接：

```html
<a th:href="@{|/user/profile/${user.id}|}">
  <img th:src="${user.headerUrl}" class="align-self-start mr-4 rounded-circle user-header" alt="用户头像" >
</a>

<a th:href="@{|/user/profile/${cvo.user.id}|}">
  <img th:src="${cvo.user.headerUrl}" class="align-self-start mr-4 rounded-circle user-header" alt="用户头像" >
</a>

<span th:if="${rvo.target == null}">
  <a th:href="@{|/user/profile/${rvo.user.id}|}">
    <b class="text-info" th:utext="${rvo.user.username}">
      寒江雪
    </b>
  </a>	:&nbsp;&nbsp;
</span>
<span th:if="${rvo.target != null}">
  <a th:href="@{|/user/profile/${rvo.user.id}|}">
    <b class="text-info" th:utext="${rvo.user.username}">
      寒江雪
    </b>
  </a>回复
  <a th:href="@{|/user/profile/${rvo.target.id}|}">
    <b class="text-info" th:utext="${rvo.target.username}">
      寒江雪
    </b>
  </a>:&nbsp;&nbsp;
</span>
```