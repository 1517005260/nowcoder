# 点赞

性能：同一时间可能会有很多人点赞（redis）

- 点赞
  - 支持对帖子、评论的点赞
  - 第一次点赞，第二次取消点赞
- 首页点赞数量
  - 统计帖子的点赞数量
- 详情页点赞数量
  - 统计点赞数量
  - 显示点赞状态（已赞）

# 代码实现

1. dao  不同于之前，我们数据存在redis里，而由于redis本质就是键值对，非常简单，dao可以不用写

2. service

写一个专门生成redis的key的工具：

```java
package com.nowcoder.community.util;

// 简单小工具，不需要容器托管
public class RedisKeyUtil {
    private static final String SPLIT = ":"; //分隔符

    // 把帖子和评论统称为实体
    private static final String PREFIX_ENTITY_LIKE = "like:entity";

    // 某个实体的赞
    // like:entity:entityType:entityId -> set(userId) （方便统计谁赞了、赞的数量等）
    public static String getEntityLikeKey(int entityType, int entityId){
        return PREFIX_ENTITY_LIKE + SPLIT + entityType + SPLIT +entityId;
    }
}
```

新建LikeService

```java
package com.nowcoder.community.service;

import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LikeService {
    @Autowired
    private RedisTemplate redisTemplate;

    // 点赞
    public void like(int userId, int entityType, int entityId){
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);

        // 如果点过赞就取消，即把userId移出集合
        boolean isMember = redisTemplate.opsForSet().isMember(entityLikeKey, userId);
        if(isMember){
            redisTemplate.opsForSet().remove(entityLikeKey, userId);
        }else {
            redisTemplate.opsForSet().add(entityLikeKey, userId);
        }
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
}
```

3. controller

异步请求，只要刷新点赞数即可，不用刷新整个页面

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.LikeService;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class LikeController {
    @Autowired
    private LikeService likeService;
    
    @Autowired
    private HostHolder hostHolder;
    
    @RequestMapping(path = "/like", method = RequestMethod.POST)
    @ResponseBody
    public String like(int entityType, int entityId){
        User user = hostHolder.getUser();
        if(user == null){
            return CommunityUtil.getJSONString(1, "您还未登录！");
        }
        // 后续会用SpringSecurity统一判断有无登录
        
        likeService.like(user.getId(), entityType, entityId);
        long likeCount = likeService.findEntityLikeCount(entityType, entityId);
        int likeStatus = likeService.findEntityLikeStatus(user.getId(), entityType, entityId);

        Map<String, Object> map = new HashMap<>();
        map.put("likeCount", likeCount);
        map.put("likeStatus", likeStatus);
        
        return CommunityUtil.getJSONString(0, null, map);
    }
}
```

4. 帖子详情页面discuss-detail：

```html
<!--帖子-->
<!--this指的是本个<a>-->
<li class="d-inline ml-2">
  <a href="javascript:;" th:onclick="|like(this, 1, ${post.id});|" class="text-primary">
    <b>赞</b> <i>11</i>
  </a>
</li>

<!--评论-->
<li class="d-inline ml-2">
  <a href="javascript:;" th:onclick="|like(this, 2 , ${cvo.comment.id});|" class="text-primary">
    <b>赞</b> (<i>1</i>)
  </a>
</li>

<li class="d-inline ml-2">
  <a href="javascript:;" th:onclick="|like(this, 2, ${rvo.reply.id});|" class="text-primary">
    <b>赞</b> (<i>1</i>)</a>
</li>


<!--引入专门用于like的js-->
<script th:src="@{/js/discuss.js}"></script>
```

discuss.js:

```javascript
function like(btn, entityType, entityId){
    $.post(
        CONTEXT_PATH + "/like",
        {"entityType":entityType, "entityId":entityId},
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

5. 补充：修改我们看到的赞数

a. 首页

在HomeController中修改：

```java
public class HomeController implements CommunityConstant {

    @Autowired
    private LikeService likeService;

    @RequestMapping(path = "/index", method = RequestMethod.GET)
    public String getIndexPage(Model model, Page page){

    //方法调用前，SpringMVC会自动实例化Model和Page，并将Page注入Model
    //所以不用model.addAttribute(Page),直接在thymeleaf可以访问Page的数据

    page.setRows(discussPostService.findDiscussPostRows(0));
    page.setPath("/index");
    // 默认是第一页，前10个帖子
    List<DiscussPost> list = discussPostService.findDiscussPosts(0, page.getOffset(), page.getLimit());

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
    return "index";
  }
}
```

index.html:

```html
<li class="d-inline ml-2">赞 <span th:text="${map.likeCount}">11</span></li>
```

b. 帖子详情页

在DiscussPostController中新增：

```java
@Autowired
private LikeService likeService;

//帖子详情
@RequestMapping(path = "/detail/{discussPostId}", method = RequestMethod.GET)
public String getDiscussPost(@PathVariable("discussPostId") int discussPostId, Model model, Page page){
    //帖子
  DiscussPost discussPost = discussPostService.findDiscussPostById(discussPostId);
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

  return "/site/discuss-detail";
}
```

前端discuss-detail中：

```html
<b th:text="${likeStatus==1?'已赞':'赞'}">赞</b> <i th:text="${likeCount}">11</i>

<b th:text="${cvo.likeStatus==1?'已赞':'赞'}">赞</b> (<i th:text="${cvo.likeCount}">1</i>)

<b th:text="${rvo.likeStatus==1?'已赞':'赞'}">赞</b> (<i th:text="${rvo.likeCount}">1</i>)</a>
```