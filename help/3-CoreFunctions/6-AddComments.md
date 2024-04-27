# 添加评论

- 数据层
  - 增加评论数据
  - 修改帖子的评论数量
- 业务层
  - 处理添加评论的业务
  - 先增加评论、再更新帖子的评论数量   `事务管理`
- 表现层
  - 处理添加评论数据的请求
  - 设置添加评论的表单

## 代码实现

1. dao

a. 新增增加评论的方法：

```java
//增加评论，返回行数
int insertComment(Comment comment);
```

b. 实现sql

首先再comment-mapper中新增插入语句

```xml
<sql id="insertFields">
  user_id, entity_type, entity_id, target_id, content, status, create_time
</sql>

<insert id="insertComment" parameterType="Comment">
        insert into comment (<include refid="insertFields"></include>)
        values (#{userId}, #{entityType}, #{entityId}, #{targetId}, #{content}, #{status}, #{createTime})
</insert>
```

在discusspost-mapper.xml和DiscussPostMapper中同步评论数增加：

```xml
<update id="updateCommentCount">
        update discuss_post set comment_count = #{commentCount}
        where id = #{id}
</update>
```

```java
//更新评论数
int updateCommentCount(int id, int commentCount);
```

2. service

a. 在DiscussPostService实现增加评论数的方法

```java
public int updateCommentCount(int id, int CommentCount){
        return discussPostMapper.updateCommentCount(id, CommentCount);
}
```

b. 在CommentService实现增加评论的方法，由于涉及到多个表的数据操作，需要事务管理

```java
public class CommentService implements CommunityConstant {
  @Autowired
  private SensitiveFilter sensitiveFilter;

  @Autowired
  private DiscussPostService discussPostService;

  @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
  public int addComment(Comment comment){
    if(comment == null){
      throw new IllegalArgumentException("参数不能为空！");
    }
    //增加评论
    comment.setContent(HtmlUtils.htmlEscape(comment.getContent()));
    comment.setContent(sensitiveFilter.filter(comment.getContent()));
    int rows = commentMapper.insertComment(comment);

    //增加帖子的评论数
    if(comment.getEntityType() == ENTITY_TYPE_POST){
      int count = commentMapper.selectCountByEntity(ENTITY_TYPE_POST, comment.getEntityId());
      discussPostService.updateCommentCount(comment.getEntityId(),count);
    }

    return rows;
  }
}
```

3. controller

新建CommentController

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.Comment;
import com.nowcoder.community.service.CommentService;
import com.nowcoder.community.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@Controller
@RequestMapping(path = "/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Autowired
    private HostHolder hostHolder;

    @RequestMapping(path = "/add/{discussPostId}", method = RequestMethod.POST)
    public String addComment(@PathVariable("discussPostId") int id, Comment comment){
        comment.setUserId(hostHolder.getUser().getId());
        comment.setStatus(0);  //默认有效
        comment.setCreateTime(new Date());
        commentService.addComment(comment);

        return "redirect:/discuss/detail/" + id;
    }
}
```

4. 前端

```html
<div th:id="|huifu-${rvoStat.count}|" class="mt-4 collapse">
  <form method="post" th:action="@{|/comment/add/${post.id}|}">
    <div>
      <input type="text" class="input-size" name="content" th:placeholder="|回复 ${rvo.user.username}|">
      <input type="hidden" name="entityType" value="2"> <!--评论-->
      <input type="hidden" name="entityId" th:value="${cvo.comment.id}">
      <input type="hidden" name="targetId" th:value="${rvo.user.id}">
    </div>
    <div class="text-right mt-2">
      <button type="submit" class="btn btn-primary btn-sm" onclick="#">&nbsp;&nbsp;回&nbsp;&nbsp;复&nbsp;&nbsp;</button>
    </div>
  </form>
</div>

<!-- 回复输入框 -->
<li class="pb-3 pt-3">
  <form method="post" th:action="@{|/comment/add/${post.id}|}">
    <div>
      <input type="text" class="input-size"  name="content" placeholder="请输入你的观点">
      <input type="hidden" name="entityType" value="2"> <!--评论-->
      <input type="hidden" name="entityId" th:value="${cvo.comment.id}">
    </div>
    <div class="text-right mt-2">
      <button type="submit" class="btn btn-primary btn-sm" onclick="#">&nbsp;&nbsp;回&nbsp;&nbsp;复&nbsp;&nbsp;</button>
    </div>
  </form>
</li>

<div class="container mt-3">
  <form class="replyform" method="post" th:action="@{|/comment/add/${post.id}|}">
    <p class="mt-3">
      <a name="replyform"></a>
      <textarea placeholder="在这里畅所欲言你的看法吧!" name="content"></textarea>
      <input type="hidden" name="entityType" value="1"> <!--帖子-->
      <input type="hidden" name="entityId" th:value="${post.id}">
    </p>
    <p class="text-right">
      <button type="submit" class="btn btn-primary btn-sm">&nbsp;&nbsp;回&nbsp;&nbsp;帖&nbsp;&nbsp;</button>
    </p>
  </form>
</div>
```