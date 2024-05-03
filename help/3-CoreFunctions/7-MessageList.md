# 私信列表

- 私信列表
  - 查询当前用户的会话列表
  - 每个会话只显示一条最新的私信
  - 支持分页显示
- 私信详情
  - 查询某个会话所包含的私信
  - 支持分页显示

## 代码实现

1.  dao

a. 新建实体类 message

```java
package com.nowcoder.community.entity;

import java.util.Date;

public class Message {
    private int id;
    private int fromId;  //系统通知fromId = 1
    private int toId;
    private String conversationId;  // 无论 1->2 还是 2->1 的消息都为 "1_2"
    private String content;
    private int status;  //0 未读 1 已读 2 删除
    private Date createTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFromId() {
        return fromId;
    }

    public void setFromId(int fromId) {
        this.fromId = fromId;
    }

    public int getToId() {
        return toId;
    }

    public void setToId(int toId) {
        this.toId = toId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
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
        return "Message{" +
                "id=" + id +
                ", fromId=" + fromId +
                ", toId=" + toId +
                ", conversationId='" + conversationId + '\'' +
                ", content='" + content + '\'' +
                ", status=" + status +
                ", createTime=" + createTime +
                '}';
    }
}
```

b. 新建数据库访问接口 MessageMapper

```java
package com.nowcoder.community.dao;

import com.nowcoder.community.entity.Message;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MessageMapper {
    // 查询当前用户的私信列表，每个对话显示界面仅返回一条最新的消息
    List<Message> selectConversations(int userId, int offset, int limit);

    // 查询当前用户的会话数量
    int selectConversationCount(int userId);

    // 私信详情：查询某个会话所包含的所有消息
    List<Message> selectLetters(String conversationId, int offset, int limit);

    // 查询某个会话所包含的消息数量
    int selectLetterCount(String conversationId);

    // 查询用户未读消息数量（列表页和详情页共用一个查询，需要动态拼接）
    int selectLetterUnreadCount(int userId, String conversationId);
}
```

c. sql实现，新建message-mapper.xml配置文件

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.nowcoder.community.dao.MessageMapper">
    <sql id="selectFields">
        id, from_id, to_id, conversation_id, content, status, create_time
    </sql>

    <select id="selectConversations" resultType="Message">  <!--max id 是为了找到最新的消息-->
        select <include refid="selectFields"></include>
        from message
        where id in (
            select max(id)
            from message
            where status != 2
            and from_id !=1
            and (from_id = #{userId} or to_id = #{userId})
            group by conversation_id
        )
        order by id desc
        limit #{offset}, #{limit}
    </select>

    <select id="selectConversationCount" resultType="int">
        select count(m.maxid)
        from (
            select max(id) as maxid
            from message
            where status != 2
            and from_id !=1
            and (from_id = #{userId} or to_id = #{userId})
            group by conversation_id
        ) as m
    </select>

    <select id="selectLetters" resultType="Message">
        select <include refid="selectFields"></include>
        from message
        where status != 2
        and from_id  != 1
        and conversation_id = #{conversationId}
        order by id desc
        limit #{offset}, #{limit}
    </select>

    <select id="selectLetterCount" resultType="int">
        select count(id)
        from message
        where status != 2
        and from_id  != 1
        and conversation_id = #{conversationId}
    </select>

    <select id="selectLetterUnreadCount" resultType="int">
        select count(id)
        from message
        where status = 0
          and from_id  != 1
          and to_id = #{userId}
          <if test="conversationId != null">
          and conversation_id = #{conversationId}
          </if>
    </select>

</mapper>
```

d. 测试刚刚写完的代码

在MapperTests.java中：

```java
@Autowired
private MessageMapper messageMapper;

@Test
public void testSelectLetters(){
  List<Message> list = messageMapper.selectConversations(111, 0, 20);
  for(Message message : list){
    System.out.println(message);
  }

  int cnt = messageMapper.selectConversationCount(111);
  System.out.println(cnt);

  list = messageMapper.selectLetters("111_112", 0, 10);
  for(Message message : list){
    System.out.println(message);
  }

  cnt = messageMapper.selectLetterCount("111_112");
  System.out.println(cnt);

  cnt = messageMapper.selectLetterUnreadCount(131, "111_131");
  System.out.println(cnt);
}
```

2. service

新建MessageService

```java
package com.nowcoder.community.service;

import com.nowcoder.community.dao.MessageMapper;
import com.nowcoder.community.entity.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageMapper messageMapper;

    public List<Message> findConversations(int userId, int offset, int limit){
        return  messageMapper.selectConversations(userId, offset, limit);
    }

    public int findConversationCount(int userId){
        return messageMapper.selectConversationCount(userId);
    }

    public List<Message> findLetters(String conversationId, int offset, int limit){
        return messageMapper.selectLetters(conversationId, offset, limit);
    }

    public int findLetterCount(String conversationId){
        return messageMapper.selectLetterCount(conversationId);
    }

    public int findLetterUnreadCount(int userId, String conversationId){
        return messageMapper.selectLetterUnreadCount(userId, conversationId);
    }
}
```

3. controller

新建MessageController

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.Message;
import com.nowcoder.community.entity.Page;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.MessageService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class MessageController {

  @Autowired
  private MessageService messageService;

  @Autowired
  private HostHolder hostHolder;

  @Autowired
  private UserService userService;

  // 私信列表
  @RequestMapping(path = "/letter/list", method = RequestMethod.GET)
  public String getLetterList(Model model, Page page){
    User user = hostHolder.getUser();

    // 设置分页信息
    page.setLimit(5);
    page.setPath("/letter/list");
    page.setRows(messageService.findConversationCount(user.getId()));

    // 会话列表
    List<Message> conversationlist
            = messageService.findConversations(user.getId(), page.getOffset(), page.getLimit());

    // 封装 未读消息、消息总数等信息
    List<Map<String, Object>> conversations = new ArrayList<>();
    if(conversationlist != null){
      for(Message message : conversationlist){
        Map<String, Object> map = new HashMap<>();
        map.put("conversation", message);  // 和某个用户所有的对话
        map.put("letterCount", messageService.  // 私信总数量
                findLetterCount(message.getConversationId()));
        map.put("unreadCount", messageService.  // 该对话的未读消息
                findLetterUnreadCount(user.getId(), message.getConversationId()));
        int targetId = user.getId() == message.getFromId() ?
                message.getToId() : message.getFromId();  // 和本用户相对的用户，用于显示头像
        map.put("target", userService.findUserById(targetId));

        conversations.add(map);
      }
    }

    model.addAttribute("conversations",conversations);

    // 未读消息总数
    int letterUnreadCount = messageService.findLetterUnreadCount(user.getId(), null);
    model.addAttribute("letterUnreadCount", letterUnreadCount);

    return "/site/letter";
  }

  // 私信详情
  @RequestMapping(path = "/letter/detail/{conversationId}", method = RequestMethod.GET)
  public String getLetterDetail(@PathVariable("conversationId")String conversationId,
                                Page page, Model model){
    // 分页信息设置
    page.setLimit(5);
    page.setPath("/letter/detail/" + conversationId);
    page.setRows(messageService.findLetterCount(conversationId));

    // 和某个用户的所有对话记录
    List<Message> letterList =  messageService.findLetters(conversationId, page.getOffset(), page.getLimit());
    List<Map<String, Object>> letters = new ArrayList<>();
    if(letterList != null){
      for(Message message : letterList){
        Map<String, Object> map = new HashMap<>();
        map.put("letter", message);
        map.put("fromUser", userService.findUserById(message.getFromId()));

        letters.add(map);
      }
    }

    model.addAttribute("letters", letters);

    // 私信的目标
    model.addAttribute("target", getLetterTarget(conversationId));

    return "/site/letter-detail";
  }

  private User getLetterTarget(String conversationId){
    String[] ids = conversationId.split("_");
    int id1 = Integer.parseInt(ids[0]);
    int id2 = Integer.parseInt(ids[1]);

    if(hostHolder.getUser().getId() == id1){
      return userService.findUserById(id2);
    }else{
      return userService.findUserById(id1);
    }
  }
}
```

4. 前端

a. index页新增超链接

```html
<a class="nav-link position-relative" th:href="@{/letter/list}">消息<span class="badge badge-danger">12</span></a>
```

b. letter-html

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />
<link rel="stylesheet" th:href="@{/css/letter.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

<a class="nav-link position-relative active" th:href="@{/letter/list}">朋友私信<span class="badge badge-danger" th:text="${letterUnreadCount}" th:if="${letterUnreadCount!=0}">3</span></a>

  <!-- 私信列表 -->
  <ul class="list-unstyled">
    <li class="media pb-3 pt-3 mb-3 border-bottom position-relative" th:each="map:${conversations}">
      <span class="badge badge-danger" th:text="${map.unreadCount}" th:if="${map.unreadCount!=0}">3</span>
      <a href="profile.html">
        <img th:src="${map.target.headerUrl}" class="mr-4 rounded-circle user-header" alt="用户头像" >
      </a>
      <div class="media-body">
        <h6 class="mt-0 mb-3">
          <span class="text-success" th:utext="${map.target.username}">落基山脉下的闲人</span>
          <span class="float-right text-muted font-size-12" th:text="${#dates.format(map.conversation.createTime,'yyyy-MM-dd HH:mm:ss')}">2019-04-28 14:13:25</span>
        </h6>
        <div>
          <a  th:href="@{|/letter/detail/${map.conversation.conversationId}|}" th:utext="${map.conversation.content}">米粉车, 你来吧!</a>
          <ul class="d-inline font-size-12 float-right">
            <li class="d-inline ml-2"><a href="#" class="text-primary">共
              <i th:text="${map.letterCount}">5</i>条消息</a></li>
          </ul>
        </div>
      </div>
    </li>
  </ul>  

<nav class="mt-5" th:replace="index::pagination">  

<script th:src="@{/js/global.js}"></script>
<script th:src="@{/js/letter.js}"></script>
```

c. letter-detail.html

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />
<link rel="stylesheet" th:href="@{/css/letter.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

<h6><b class="square"></b> 来自 <i class="text-success" th:utext="${target.username}">落基山脉下的闲人</i> 的私信</h6>

  <button type="button" class="btn btn-secondary btn-sm" onclick="back();">返回</button>

  <!-- 私信列表 -->
  <ul class="list-unstyled mt-4">
    <li class="media pb-3 pt-3 mb-2" th:each="map:${letters}">
      <a href="profile.html">
        <img th:src="${map.fromUser.headerUrl}" class="mr-4 rounded-circle user-header" alt="用户头像" >
      </a>
      <div class="toast show d-lg-block" role="alert" aria-live="assertive" aria-atomic="true">
        <div class="toast-header">
          <strong class="mr-auto" th:utext="${map.fromUser.username}">落基山脉下的闲人</strong>
          <small th:text="${#dates.format(map.letter.createTime,'yyyy-MM-dd HH:mm:ss')}">2019-04-25 15:49:32</small>
          <button type="button" class="ml-2 mb-1 close" data-dismiss="toast" aria-label="Close">
            <span aria-hidden="true">&times;</span>
          </button>
        </div>
        <div class="toast-body" th:utext="${map.letter.content}">
          君不见, 黄河之水天上来, 奔流到海不复回!
        </div>
      </div>
    </li>
  </ul>

  <nav class="mt-5" th:replace="index::pagination">

<script th:src="@{/js/global.js}"></script>
<script th:src="@{/js/letter.js}"></script>
    <script>
      function back() {
        location.href = CONTEXT_PATH + "/letter/list";
      }
    </script>
```