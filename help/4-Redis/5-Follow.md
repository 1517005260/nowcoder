# 关注、取消关注

- 需求
  - 开发关注、取消关注功能
  - 统计用户的关注数、粉丝数
- 要点
  - A关注了B：`A->B` A为follower，B为followee
  - 关注的目标既可以是用户，也可以是帖子 ==> 抽象为实体

==> 频繁使用的功能，为了提高数据，存在redis里

## 代码实现

1. 在RedisKeyUtil中追加:

注意redisKey命名的艺术

```java
private static final String PREFIX_FOLLOWEE = "followee";
private static final String PREFIX_FOLLOWER = "follower";

// 关注：双份数据
// userId关注了followee  user -> entity
// followee:userId:entityType -> Zset(entityId, follow_time)
public static String getFolloweeKey(int userId, int entityType){
  return PREFIX_FOLLOWEE + SPLIT + userId + SPLIT + entityType;
}

// 某个实体拥有的follower     user -> entity
// follower:entityType:entityId -> Zset(userId, follow_time)
public static String getFollowerKey(int entityType, int entityId){
  return PREFIX_FOLLOWER + SPLIT + entityType + SPLIT + entityId;
}
```

新增实体类型：人

```java
// 实体类型——帖子1 评论2 用户3
    int ENTITY_TYPE_POST = 1;
    int ENTITY_TYPE_COMMENT = 2;
    int ENTITY_TYPE_USER = 3;
```

2. 新建FollowService

```java
package com.nowcoder.community.service;

import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

@Service
public class FollowService {
    @Autowired
    private RedisTemplate redisTemplate;

    // 关注
    public void follow(int userId, int entityType, int entityId){
        // 涉及两次存储 -> 事务

        redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
                String followerKey = RedisKeyUtil.getFollowerKey(entityType, entityId);
    
                operations.multi();
    
                operations.opsForZSet().add(followeeKey, entityId, System.currentTimeMillis());
                operations.opsForZSet().add(followerKey, userId, System.currentTimeMillis());
    
                return operations.exec();
            }
        });
    }
    
    // 取关
    public void unfollow(int userId, int entityType, int entityId){
        redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
                String followerKey = RedisKeyUtil.getFollowerKey(entityType, entityId);

                operations.multi();

                operations.opsForZSet().remove(followeeKey, entityId);
                operations.opsForZSet().remove(followerKey, userId);

                return operations.exec();
            }
        });
    }
}
```

3. 新建FollowController

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.FollowService;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class FollowController {
    @Autowired
    private FollowService followService;
    
    @Autowired
    private HostHolder hostHolder;
    
    // 同点赞一样都是异步请求
    @RequestMapping(path = "/follow", method = RequestMethod.POST)
    @ResponseBody
    public String follow(int entityType, int entityId){
        User user = hostHolder.getUser();
        if(user == null){
            return CommunityUtil.getJSONString(1, "您还未登录！");
        }
        
        followService.follow(user.getId(), entityType, entityId);
        
        return CommunityUtil.getJSONString(0, "已关注！");
    }

    @RequestMapping(path = "/unfollow", method = RequestMethod.POST)
    @ResponseBody
    public String unfollow(int entityType, int entityId){
        User user = hostHolder.getUser();
        if(user == null){
            return CommunityUtil.getJSONString(1, "您还未登录！");
        }

        followService.unfollow(user.getId(), entityType, entityId);

        return CommunityUtil.getJSONString(0, "已取关！");
    }
}
```

4. 处理前端关注逻辑

profile.html增加id索引：

```html
<input type="hidden" id="entityId" th:value="${user.id}">
<button type="button" class="btn btn-info btn-sm float-right mr-5 follow-btn">关注TA</button>
```

profile.js

```javascript
$(function(){
	$(".follow-btn").click(follow);
});

function follow() {
	let btn = this;
	if($(btn).hasClass("btn-info")) {
		// 关注TA
		$.post(
			CONTEXT_PATH + "/follow",
			{"entityType":3, "entityId":$(btn).prev().val()},
			function (data){
				data = $.parseJSON(data);
				if(data.code == 0){
					window.location.reload();
				}else{
					alert(data.msg);
				}
			}
		);
	} else {
		// 取消关注
		$.post(
			CONTEXT_PATH + "/unfollow",
			{"entityType":3, "entityId":$(btn).prev().val()},
			function (data){
				data = $.parseJSON(data);
				if(data.code == 0){
					window.location.reload();
				}else{
					alert(data.msg);
				}
			}
		);
	}
}
```

5. 处理关注状态

在FollowService增加查询方法：

```java
//查询关注了多少人
public long findFolloweeCount(int userId, int entityType){
    String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
    return redisTemplate.opsForZSet().zCard(followeeKey);
}
    
// 查询实体粉丝数量
public long findFollowerCount(int entityType, int entityId){
    String followerKey = RedisKeyUtil.getFollowerKey(entityType, entityId);
    return redisTemplate.opsForZSet().zCard(followerKey);
}

// 查询user是否关注了entity
public boolean hasFollowed(int userId, int entityType, int entityId){
  String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
  return redisTemplate.opsForZSet().score(followeeKey, entityId) != null ;
}
```

处理UserController：

```java
public class UserController implements CommunityConstant {
    @Autowired
    private FollowService followService;

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

    // 关注了
    long followeeCount = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
    model.addAttribute("followeeCount",followeeCount);

    // 粉丝
    long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, userId);
    model.addAttribute("followerCount",followerCount);

    // 是否关注
    boolean hasFollowed = false;
    if(hostHolder.getUser() != null){
      hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
    }
    model.addAttribute("hasFollowed", hasFollowed);

    return "/site/profile";
  }
}
```

profile.html:

```html
<button type="button"
        th:class="|btn ${hasFollowed?'btn-secondary':'btn-info'} btn-sm float-right mr-5 follow-btn|"
        th:text="${hasFollowed?'已关注':'关注TA'}"
        th:if="${loginUser!=null && loginUser.id!=user.id}"
>关注TA</button>

<span>关注了 <a class="text-primary" href="followee.html" th:text="${followeeCount}">5</a> 人</span>
<span class="ml-4">关注者 <a class="text-primary" href="follower.html" th:text="${followerCount}">123</a> 人</span>
```