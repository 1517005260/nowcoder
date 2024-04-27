# 显示评论

- 数据层
  - 根据实体查询一页评论的数据
  - 根据实体查询评论的数量
- 业务层
  - 处理查询评论的业务
  - 处理查询评论数量的业务
- 表现层
  - 显示帖子详情数据时，同时显示该帖子的所有评论数据


## 代码实现

1. dao

a. 新建实体comment

```java
package com.nowcoder.community.entity;

import java.util.Date;

public class Comment {

    private int id;
    private int userId;  //谁发的
    private int entityType;  //评论对象：帖子还是评论
    private int entityId;  //评论对象的id
    private int targetId;  //对评论的回复的评论
    private String content; //内容
    private int status;  //是否被封
    private Date createTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getEntityType() {
        return entityType;
    }

    public void setEntityType(int entityType) {
        this.entityType = entityType;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public int getTargetId() {
        return targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id=" + id +
                ", userId=" + userId +
                ", entityType=" + entityType +
                ", entityId=" + entityId +
                ", targetId=" + targetId +
                ", content='" + content + '\'' +
                ", status=" + status +
                ", createTime=" + createTime +
                '}';
    }
}
```

b. 新建CommentMapper

```java
package com.nowcoder.community.dao;

import com.nowcoder.community.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommentMapper {
    
    //分页查询评论
    List<Comment> selectCommentsByEntity(int entityType, int entityId, int offset, int limit);
    
    //查询评论条目数
    int selectCountByEntity(int entityType, int entityId);
}
```

c. xml实现sql，新建comment-mapper.xml

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.nowcoder.community.dao.CommentMapper">

    <sql id="selectFields">
        id, user_id, entity_type, entity_id, target_id, content, status, create_time
    </sql>

    <select id="selectCommentsByEntity" resultType="Comment">
        select <include refid="selectFields"></include>
        from comment
        where status = 0 
        and entity_type = #{entityType}
        and entity_id = #{entityId}
        order by create_time asc 
        limit #{offset}, #{limit}
    </select>

    <select id="selectCountByEntity" resultType="int">
        select count(id)
        from comment
        where status = 0
        and entity_type = #{entityType}
        and entity_id = #{entityId}
    </select>

</mapper>
```

2. service

新建业务组件CommentService

```java
package com.nowcoder.community.service;

import com.nowcoder.community.dao.CommentMapper;
import com.nowcoder.community.entity.Comment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentService {
    
    @Autowired
    private CommentMapper commentMapper;
    
    public List<Comment> findCommentsByEntity(int entityType, int entityId, int offset, int limit){
        return commentMapper.selectCommentsByEntity(entityType, entityId, offset, limit);
    }
    
    public int findCommentCount(int entityType, int entityId){
        return commentMapper.selectCountByEntity(entityType, entityId);
    }
}
```

3. controller

追加新的常量于CommunityConstant:
```java
// 实体类型——帖子1 评论2
int ENTITY_TYPE_POST = 1;
int ENTITY_TYPE_COMMENT = 2;
```

在DiscussPostController更新

```java
public class DiscussPostController implements CommunityConstant{
  @Autowired
  private CommentService commentService;

  //帖子详情
  @RequestMapping(path = "/detail/{discussPostId}", method = RequestMethod.GET)
  public String getDiscussPost(@PathVariable("discussPostId") int discussPostId, Model model, Page page){
    //帖子
    DiscussPost discussPost = discussPostService.findDiscussPostById(discussPostId);
    model.addAttribute("post", discussPost);

    //作者
    User user = userService.findUserById(discussPost.getUserId());
    model.addAttribute("user", user);

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
        commentVo.put("comment", comment);
        commentVo.put("user", userService.findUserById(comment.getUserId()));

        //评论的评论——回复
        List<Comment> replyList = commentService.
                findCommentsByEntity(ENTITY_TYPE_COMMENT,
                        comment.getId(), 0, Integer.MAX_VALUE);  // 回复就不需要分页了，就一页显示所有评论

        //找到回复的用户
        List<Map<String, Object>> replyVoList = new ArrayList<>();
        if(replyList != null){
          for(Comment reply : replyList){
            Map<String, Object> replyVo = new HashMap<>();
            replyVo.put("reply", reply);
            replyVo.put("user", userService.findUserById(reply.getUserId()));

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

    //待补充：回复的功能

    return "/site/discuss-detail";
  }
}
```

4. 前端

首页回帖数动态显示：
```html
<li class="d-inline ml-2" >回帖 <span th:text="${map.post.commentCount}">7</span></li>
```

帖子详情页：

```html
<li class="d-inline ml-2"><a href="#replyform" class="text-primary">回帖
  <i th:text="${post.commentCount}">7</i>
</a></li>

<!-- 回帖 -->
<div class="container mt-3">
  <!-- 回帖数量 -->
  <div class="row">
    <div class="col-8">
      <h6><b class="square"></b> <i th:text="${post.commentCount}">30</i>条回帖</h6>
    </div>
    <div class="col-4 text-right">
      <a href="#replyform" class="btn btn-primary btn-sm">&nbsp;&nbsp;回&nbsp;&nbsp;帖&nbsp;&nbsp;</a>
    </div>
  </div>
  <!-- 回帖列表 -->
  <ul class="list-unstyled mt-4">
    <li class="media pb-3 pt-3 mb-3 border-bottom" th:each="cvo:${comments}">
      <a href="profile.html">
        <img th:src="${cvo.user.headerUrl}" class="align-self-start mr-4 rounded-circle user-header" alt="用户头像" >
      </a>
      <div class="media-body">
        <div class="mt-0">
          <span class="font-size-12 text-success" th:utext="${cvo.user.username}">掉脑袋切切</span>
          <span class="badge badge-secondary float-right floor">
            <i th:text="${page.offset + cvoStat.count}">1</i>  <!--当前楼层数 = 当前页起始楼 + 当前页循环数-->
            #</span>
        </div>
        <div class="mt-2" th:utext="${cvo.comment.content}">
          这开课时间是不是有点晚啊。。。
        </div>
        <div class="mt-4 text-muted font-size-12">
          <span>发布于 <b th:text="${#dates.format(cvo.comment.createTime, 'yyyy-MM-dd HH:mm:ss')}">2019-04-15 15:32:18</b></span>
          <ul class="d-inline float-right">
            <li class="d-inline ml-2"><a href="#" class="text-primary">赞(1)</a></li>
            <li class="d-inline ml-2">|</li>
            <li class="d-inline ml-2"><a href="#" class="text-primary">回复(
              <i th:text="${cvo.replyCount}">2</i>
              )</a></li>
          </ul>
        </div>
        <!-- 回复列表 -->
        <ul class="list-unstyled mt-4 bg-gray p-3 font-size-12 text-muted">
          <li class="pb-3 pt-3 mb-3 border-bottom" th:each="rvo:${cvo.replys}">
            <div>
              <span th:if="${rvo.target == null}">
                <b class="text-info" th:utext="${rvo.user.username}">寒江雪</b>:&nbsp;&nbsp;
              </span>
              <span th:if="${rvo.target != null}">
                <b class="text-info" th:utext="${rvo.user.username}">寒江雪</b> 回复 
                <b class="text-info" th:utext="${rvo.target.username}">寒江雪</b>:&nbsp;&nbsp;
              </span>
              <span th:utext="${rvo.reply.content}">这个是直播时间哈，觉得晚的话可以直接看之前的完整录播的~</span>
            </div>
            <div class="mt-3">
              <span th:text="${#dates.format(rvo.reply.createTime,'yyyy-MM-dd HH:MM:ss')}">2019-04-15 15:32:18</span>
              <ul class="d-inline float-right">
                <li class="d-inline ml-2"><a href="#" class="text-primary">赞(1)</a></li>
                <li class="d-inline ml-2">|</li>
                <li class="d-inline ml-2"><a th:href="| #huifu-${rvoStat.count}|" data-toggle="collapse" class="text-primary">回复</a></li>
              </ul>
              <div th:id="|huifu-${rvoStat.count}|" class="mt-4 collapse">
                <div>
                  <input type="text" class="input-size" placeholder="回复寒江雪"/>
                </div>
                <div class="text-right mt-2">
                  <button type="button" class="btn btn-primary btn-sm" onclick="#">&nbsp;&nbsp;回&nbsp;&nbsp;复&nbsp;&nbsp;</button>
                </div>										
              </div>
            </div>								
          </li>
          <!-- 分页 -->
          <nav class="mt-5" th:replace="index::pagination">
```