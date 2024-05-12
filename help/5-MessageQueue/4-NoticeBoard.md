# 显示系统通知1

- 通知列表
  - 显示评论、点赞、关注三种类型的通知
- 通知详情
  - 分页显示某个主题包含的所有通知
- 未读消息
  - 在页面头部显示所有未读消息的数量

## 通知列表

1. dao

对于MessageMapper新增：

```java
// 某个主题下最新的通知
Message selectLatestNotice(int userId, String topic);

// 某个主题所包含的通知的数量
int selectNoticeCount(int userId, String topic);

// 某个主题下未读的通知数量
int selectNoticeUnreadCount(int userId, String topic);
```

在message-mapper新增sql实现：

```xml
    <select id="selectLatestNotice" resultType="Message">
        select <include refid="selectFields"></include>
        from message
        where id in (
            select max(id) from message
            where status != 2 and from_id = 1
            and to_id = #{userId}
            and conversation_id = #{topic}
        )
    </select>

    <select id="selectNoticeCount" resultType="int">
        select count(id) from message
        where status != 2 and from_id = 1
        and to_id = #{userId}
        and conversation_id = #{topic}
    </select>

    <select id="selectNoticeUnreadCount" resultType="int">
        select count(id) from message
        where status = 0 and from_id = 1
          and to_id = #{userId}
          <if test="topic!=null">
              and conversation_id = #{topic}  <!--如果不传topic,那么就是收集所有系统通知的未读数量-->
        </if>
    </select>
```

2. service

在MessageService新增：

```java
public Message findLatestNotice(int userId, String topic){
    return messageMapper.selectLatestNotice(userId, topic);
}

public int findNoticeCount(int userId, String topic){
    return messageMapper.selectNoticeCount(userId, topic);
}

public int findNoticeUnreadCount(int userId, String topic){
    return messageMapper.selectNoticeUnreadCount(userId, topic);
}
```

3. controller

MessageController新增

```java
public class MessageController implements CommunityConstant{
  @RequestMapping(path = "/letter/list", method = RequestMethod.GET)
  public String getLetterList(Model model, Page page){
      /* 前面代码不变 */

    // 未读消息总数
    int letterUnreadCount = messageService.findLetterUnreadCount(user.getId(), null);
    model.addAttribute("letterUnreadCount", letterUnreadCount);
    int noticeUnreadCount = messageService.findNoticeUnreadCount(user.getId(), null);
    model.addAttribute("noticeUnreadCount", noticeUnreadCount);  
  }

  @RequestMapping(path = "/notice/list", method = RequestMethod.GET)
  public String getNoticeList(Model model){
    User user = hostHolder.getUser();

    // comment
    Message message = messageService.findLatestNotice(user.getId(), TOPIC_COMMENT);
    Map<String, Object> messageVO = new HashMap<>();
    if(message != null){
      messageVO.put("message", message);

      // 其他数据都是简洁明了的，只有content需要特殊处理
      String content = HtmlUtils.htmlUnescape(message.getContent()) ; // 反转义
      Map<String, Object> data = JSONObject.parseObject(content, HashMap.class);
      messageVO.put("user", userService.findUserById((Integer) data.get("userId")));
      messageVO.put("entityType", data.get("entityType"));
      messageVO.put("entityId", data.get("entityId"));
      messageVO.put("postId", data.get("postId"));

      int count = messageService.findNoticeCount(user.getId(), TOPIC_COMMENT);
      messageVO.put("count", count);
      count = messageService.findNoticeUnreadCount(user.getId(), TOPIC_COMMENT);
      messageVO.put("unread", count);

      model.addAttribute("commentNotice", messageVO);  // 在if里操作防止空异常
    }

    // like
    message = messageService.findLatestNotice(user.getId(), TOPIC_LIKE);
    messageVO = new HashMap<>();
    if(message != null){
      messageVO.put("message", message);

      String content = HtmlUtils.htmlUnescape(message.getContent());
      Map<String, Object> data = JSONObject.parseObject(content, HashMap.class);
      messageVO.put("user", userService.findUserById((Integer) data.get("userId")));
      messageVO.put("entityType", data.get("entityType"));
      messageVO.put("entityId", data.get("entityId"));
      messageVO.put("postId", data.get("postId"));

      int count = messageService.findNoticeCount(user.getId(), TOPIC_LIKE);
      messageVO.put("count", count);
      count = messageService.findNoticeUnreadCount(user.getId(), TOPIC_LIKE);
      messageVO.put("unread", count);

      model.addAttribute("likeNotice", messageVO);
    }

    // follow
    message = messageService.findLatestNotice(user.getId(), TOPIC_FOLLOW);
    messageVO = new HashMap<>();
    if(message != null){
      messageVO.put("message", message);

      String content = HtmlUtils.htmlUnescape(message.getContent());
      Map<String, Object> data = JSONObject.parseObject(content, HashMap.class);
      messageVO.put("user", userService.findUserById((Integer) data.get("userId")));
      messageVO.put("entityType", data.get("entityType"));
      messageVO.put("entityId", data.get("entityId"));

      int count = messageService.findNoticeCount(user.getId(), TOPIC_FOLLOW);
      messageVO.put("count", count);
      count = messageService.findNoticeUnreadCount(user.getId(), TOPIC_FOLLOW);
      messageVO.put("unread", count);

      model.addAttribute("followNotice", messageVO);
    }

    // 未读消息总数
    int letterUnreadCount = messageService.findLetterUnreadCount(user.getId(), null);
    model.addAttribute("letterUnreadCount", letterUnreadCount);
    int noticeUnreadCount = messageService.findNoticeUnreadCount(user.getId(), null);
    model.addAttribute("noticeUnreadCount", noticeUnreadCount);

    return "/site/notice";
  }
}
```

4. 前端

修改私信页面letter.html

```html
<a class="nav-link position-relative" th:href="@{/notice/list}">
  系统通知
  <span class="badge badge-danger" th:text="${noticeUnreadCount}" th:if="${noticeUnreadCount!=0}">27
  </span>
</a>
```

notice.html

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />
<link rel="stylesheet" th:href="@{/css/letter.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

  <ul class="nav nav-tabs mb-3">
    <li class="nav-item">
      <a class="nav-link position-relative" th:href="@{/letter/list}">
        朋友私信<span class="badge badge-danger" th:text="${letterUnreadCount}" th:if="${letterUnreadCount!=0}">3</span></a>
    </li>
    <li class="nav-item">
      <a class="nav-link position-relative active" th:href="@{/notice/list}">系统通知<span class="badge badge-danger" th:text="${noticeUnreadCount}" th:if="${noticeUnreadCount!=0}">27</span></a>
    </li>
  </ul>

  <!--comment-->
  <li class="media pb-3 pt-3 mb-3 border-bottom position-relative" th:if="${commentNotice!=null}"> <!--注意commentNotice.message的判断条件会报错，因为如果没有评论通知会报空异常-->
    <span class="badge badge-danger" th:text="${commentNotice.unread!=0?commentNotice.unread:''}">3</span>
    <img src="http://static.nowcoder.com/images/head/reply.png" class="mr-4 user-header" alt="通知图标">
    <div class="media-body">
      <h6 class="mt-0 mb-3">
        <span>评论</span>
        <span class="float-right text-muted font-size-12"
              th:text="${#dates.format(commentNotice.message.createTime, 'yyyy-MM-dd HH:mm:ss')}">2019-04-28 14:13:25</span>
      </h6>
      <div>
        <a href="notice-detail.html">
          用户 <i th:utext="${commentNotice.user.username}">nowcoder</i> 评论了你的
          <b th:text="${commentNotice.entityType==1?'帖子':'回复'}">帖子</b> ...</a>
        <ul class="d-inline font-size-12 float-right">
          <li class="d-inline ml-2"><span class="text-primary">共 <i th:text="${commentNotice.count}">3</i> 条消息</span></li>
        </ul>
      </div>
    </div>
  </li>
  <!--like-->
  <li class="media pb-3 pt-3 mb-3 border-bottom position-relative" th:if="${likeNotice!=null}">
    <span class="badge badge-danger" th:text="${likeNotice.unread!=0?likeNotice.unread:''}">3</span>
    <img src="http://static.nowcoder.com/images/head/like.png" class="mr-4 user-header" alt="通知图标">
    <div class="media-body">
      <h6 class="mt-0 mb-3">
        <span>赞</span>
        <span class="float-right text-muted font-size-12"
              th:text="${#dates.format(likeNotice.message.createTime, 'yyyy-MM-dd HH:mm:ss')}">2019-04-28 14:13:25</span>
      </h6>
      <div>
        <a href="notice-detail.html">用户
          <i th:utext="${likeNotice.user.username}">nowcoder</i> 点赞了你的
          <b th:text="${likeNotice.entityType==1?'帖子':'回复'}">帖子</b> ...</a>
        <ul class="d-inline font-size-12 float-right">
          <li class="d-inline ml-2"><span class="text-primary">共 <i th:text="${likeNotice.count}">3</i> 条消息</span></li>
        </ul>
      </div>
    </div>
  </li>
  <!--follow-->
  <li class="media pb-3 pt-3 mb-3 border-bottom position-relative" th:if="${followNotice!=null}">
    <span class="badge badge-danger" th:text="${followNotice.unread!=0?followNotice.unread:''}">3</span>
    <img src="http://static.nowcoder.com/images/head/follow.png" class="mr-4 user-header" alt="通知图标">
    <div class="media-body">
      <h6 class="mt-0 mb-3">
        <span>关注</span>
        <span class="float-right text-muted font-size-12"
              th:text="${#dates.format(followNotice.message.createTime, 'yyyy-MM-dd HH:mm:ss')}">2019-04-28 14:13:25</span>
      </h6>
      <div>
        <a href="notice-detail.html">用户
          <i th:utext="${followNotice.user.username}">nowcoder</i> 关注了你 ...</a>
        <ul class="d-inline font-size-12 float-right">
          <li class="d-inline ml-2"><span class="text-primary">共 <i th:text="${followNotice.count}">3</i> 条消息</span></li>
        </ul>
      </div>
    </div>
  </li>

  <script th:src="@{/js/global.js}"></script>
```