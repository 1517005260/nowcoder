# 发送系统通知

行为非常频繁，需要考虑性能问题

消息队列的异步：当一个topic产生一条消息后，会交由专门的发送消息线程，原线程可以做其他事情

![notice](/imgs/notice.png)

<b>事件驱动</b>

- 触发事件
  - 每个topic发生后（评论、点赞、关注等），发布通知
- 处理事件
  - 封装事件对象
  - 开发事件的生产者与消费者

## 代码实现

1. 定义事件封装对象

新建实体Event

```java
package com.nowcoder.community.entity;

import java.util.HashMap;
import java.util.Map;

public class Event {
    
    private String topic; // 事件类型
    private int userId;   // 触发事件的人
    
    // 事件目标的实体
    private int entityType;
    private int entityId;
    private int entityUserId;  //  实体作者
    
    private Map<String, Object> data = new HashMap<>();  // 预防以后新增业务的可扩展字段

    public String getTopic() {
        return topic;
    }

    public Event setTopic(String topic) {  // 稍作修改，其他set同理  ==> 为了方便处理多个事件
        this.topic = topic;
        return this;
    }

    public int getUserId() {
        return userId;
    }

    public Event setUserId(int userId) {
        this.userId = userId;
        return this;
    }

    public int getEntityType() {
        return entityType;
    }

    public Event setEntityType(int entityType) {
        this.entityType = entityType;
        return this;
    }

    public int getEntityId() {
        return entityId;
    }

    public Event setEntityId(int entityId) {
        this.entityId = entityId;
        return this;
    }

    public int getEntityUserId() {
        return entityUserId;
    }

    public Event setEntityUserId(int entityUserId) {
        this.entityUserId = entityUserId;
        return this;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Event setData(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}
```

2. 开发事件的生产者和消费者

a. 新建包 event

b. 生产者EventProducer

```java
package com.nowcoder.community.event;

import com.alibaba.fastjson.JSONObject;
import com.nowcoder.community.entity.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventProducer {
    @Autowired
    private KafkaTemplate kafkaTemplate;

    // 处理事件，即发消息
    public void fireEvent(Event event){
        // 将事件发送到指定的主题
        kafkaTemplate.send(event.getTopic(), JSONObject.toJSONString(event));  // 把整个事件转换成json，让消费者处理
    }
}
```

c. 在常量接口中新增：

```java
// topic：评论、点赞、关注
String TOPIC_COMMENT = "comment";
String TOPIC_LIKE = "like";
String TOPIC_FOLLOW = "follow";

// 系统用户id
int SYSTEM_USER_ID = 1;
```

d. 消费者EventConsumer

```java
package com.nowcoder.community.event;

import com.alibaba.fastjson.JSONObject;
import com.nowcoder.community.entity.Event;
import com.nowcoder.community.entity.Message;
import com.nowcoder.community.service.MessageService;
import com.nowcoder.community.util.CommunityConstant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EventConsumer implements CommunityConstant {
  // 由于消费者要负责处理，所以需要记日志
  private static final Logger logger = LoggerFactory.getLogger(EventConsumer.class);

  @Autowired
  private MessageService messageService;

  // 由于三个消息通知格式类似，所以写一个方法就行
  @KafkaListener(topics = {TOPIC_COMMENT, TOPIC_LIKE, TOPIC_FOLLOW})
  public void handleMessage(ConsumerRecord record){
    if(record == null || record.value() == null){
      logger.error("消息的内容为空！");
    }

    // 把生产者传进来的json还原成Event
    Event event = JSONObject.parseObject(record.value().toString(), Event.class);
    if(event == null){
      logger.error("消息格式错误！");
    }

    // 发送消息，即构造一个message，存在Message表里
    // 与之前用户间的私信不一样，这次的是系统通知，from_id = 1，此时conversation_id换成存topic,content存json字符串，包含了页面上拼通知的条件
    Message message = new Message();
    message.setFromId(SYSTEM_USER_ID);
    message.setToId(event.getEntityUserId());
    message.setConversationId(event.getTopic());
    message.setStatus(0);  // 默认有效

    Map<String, Object> content = new HashMap<>();
    content.put("userId", event.getUserId());   // 触发者
    content.put("entityType", event.getEntityType());
    content.put("entityId", event.getEntityId());
    // 依据这些消息可以拼成： 用户 xxx 评论了你的 帖子 ！

    if(!event.getData().isEmpty()){  // 如果该事件中有附加的其他内容
      for(Map.Entry<String, Object> entry : event.getData().entrySet()){
        content.put(entry.getKey(), entry.getValue());
      }
    }

    message.setContent(JSONObject.toJSONString(content));

    messageService.addMessage(message);
  }
}
```

3. 调用生产者（触发事件）。由于消费者只要有消息就会自动接收，所以我们不用主动调它

a. CommentController

```java
public class CommentController implements CommunityConstant{ 
  @Autowired
  private EventProducer eventProducer;

  @Autowired
  private DiscussPostService discussPostService;

  @RequestMapping(path = "/add/{discussPostId}", method = RequestMethod.POST)
  public String addComment(@PathVariable("discussPostId") int id, Comment comment){
    comment.setUserId(hostHolder.getUser().getId());
    comment.setStatus(0);  //默认有效
    comment.setCreateTime(new Date());
    commentService.addComment(comment);

    // 触发评论的系统通知
    Event event = new Event().setTopic(TOPIC_COMMENT)
            .setUserId(hostHolder.getUser().getId())
            .setEntityType(comment.getEntityType())
            .setEntityId(comment.getEntityId())
            .setData("postId", id);  // 这里是为了最后显示“点击查看”能链接到帖子详情页面

    if(comment.getEntityType() == ENTITY_TYPE_POST){
      // 如果是对帖子的评论
      DiscussPost target = discussPostService.findDiscussPostById(comment.getEntityId());
      event.setEntityUserId(target.getUserId());
    }else if(comment.getEntityType() == ENTITY_TYPE_COMMENT){
      // 如果是对评论的回复
      Comment target = commentService.findCommentById(comment.getEntityId());
      event.setEntityUserId(target.getUserId());
    }
    eventProducer.fireEvent(event);

    return "redirect:/discuss/detail/" + id;
  }
}
```

同时补充CommentService:

```java
public Comment findCommentById(int id){
    return commentMapper.selectCommentById(id);
}
```

CommentMapper新增：

```java
// 根据id查评论
Comment selectCommentById(int id);
```

sql实现：

```xml
<select id="selectCommentById" resultType="Comment">
  select <include refid="selectFields"></include>
  from comment
  where id = #{id}
</select>
```

b. LikeController

```java
public class LikeController implements CommunityConstant{
  @Autowired
  private EventProducer eventProducer;
    
  @RequestMapping(path = "/like", method = RequestMethod.POST)
  @ResponseBody
  public String like(int entityType, int entityId, int entityUserId, int postId){  // 这里重构了原方法的传参，使得postId由前端传入
    User user = hostHolder.getUser();
    if(user == null){
      return CommunityUtil.getJSONString(1, "您还未登录！");
    }
    // 后续会用SpringSecurity统一判断有无登录

    likeService.like(user.getId(), entityType, entityId, entityUserId);
    long likeCount = likeService.findEntityLikeCount(entityType, entityId);
    int likeStatus = likeService.findEntityLikeStatus(user.getId(), entityType, entityId);

    Map<String, Object> map = new HashMap<>();
    map.put("likeCount", likeCount);
    map.put("likeStatus", likeStatus);

    // 触发点赞事件（仅赞的时候通知，取消赞不通知）
    if(likeStatus == 1){
      Event event = new Event().setTopic(TOPIC_LIKE)
              .setUserId(hostHolder.getUser().getId())
              .setEntityType(entityType)
              .setEntityId(entityId)
              .setEntityUserId(entityUserId)
              .setData("postId", postId);
      eventProducer.fireEvent(event);
    }

    return CommunityUtil.getJSONString(0, null, map);
  }
}
```

由于重构了传参，所以前端也要修改

discuss-detail.html:

```html
<a href="javascript:;" th:onclick="|like(this, 1, ${post.id}, ${post.userId}, ${post.id});|" class="text-primary">
  <b th:text="${likeStatus==1?'已赞':'赞'}">赞</b> <i th:text="${likeCount}">11</i>
</a>

<a href="javascript:;" th:onclick="|like(this, 2 , ${cvo.comment.id}, ${cvo.comment.userId}, ${post.id});|" class="text-primary">
  <b th:text="${cvo.likeStatus==1?'已赞':'赞'}">赞</b> (<i th:text="${cvo.likeCount}">1</i>)
</a>

<a href="javascript:;" th:onclick="|like(this, 2, ${rvo.reply.id}, ${rvo.reply.userId}, ${post.id});|" class="text-primary">
  <b th:text="${rvo.likeStatus==1?'已赞':'赞'}">赞</b> (<i th:text="${rvo.likeCount}">1</i>)</a>
```

discuss.js:

```javascript
function like(btn, entityType, entityId, entityUserId, postId){
    $.post(
        CONTEXT_PATH + "/like",
        {"entityType":entityType, "entityId":entityId, "entityUserId":entityUserId, "postId":postId},
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

c. FollowController

```java
@Autowired
private EventProducer eventProducer;

@RequestMapping(path = "/follow", method = RequestMethod.POST)
@ResponseBody
public String follow(int entityType, int entityId){
  User user = hostHolder.getUser();
  if(user == null){
    return CommunityUtil.getJSONString(1, "您还未登录！");
  }

  followService.follow(user.getId(), entityType, entityId);

  // 触发关注事件
  Event event = new Event().setTopic(TOPIC_FOLLOW)
          .setUserId(hostHolder.getUser().getId())
          .setEntityType(entityType)
          .setEntityId(entityId)
          .setEntityUserId(entityId);  // 当前只能关注人，所以entityId就是userId
  // 与点赞评论不一样，这里的链接是链接到关注你的人的主页

  eventProducer.fireEvent(event);

  return CommunityUtil.getJSONString(0, "已关注！");
}
```

## 出现了bug

在aop组件中报错：

```java
HttpServletRequest request = attributes.getRequest();
```

本句话报空异常，排查发现，之前我们访问service都是通过controller访问的，这里我们是直接由消息队列组件调用的，所以getRequest为空

修改如下：

```java
ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
if(attributes == null){
    return;
}
HttpServletRequest request = attributes.getRequest();
```