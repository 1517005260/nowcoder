# 帖子操作——置顶、加精、删除

- 功能实现
  - 点击`置顶`，修改帖子的类型
  - 点击`加精、删除`，修改帖子的状态
- 权限管理
  - 版主可以置顶、加精
  - 管理员可以删除
- 按钮显示（thymeleaf和SpringSecurity有结合的[第三方包](https://github.com/thymeleaf/thymeleaf-extras-springsecurity)）
  - 根据权限显示按钮

## 代码实现

1. 导包

```xml
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```

2. 功能开发

a. dao

在DiscussPostMapper追加：

```java
// 帖子操作
int updateType(int id, int type);
int updateStatus(int id, int status);
```

sql实现：

```xml
<update id="updateType">
    update discuss_post set type = #{type}
    where id = #{id}
</update>

<update id="updateStatus">
    update discuss_post set status = #{status}
    where id = #{id}
</update>
```

b. service

在DiscussPostService追加：

```java
public int updateType(int id, int type){
    return discussPostMapper.updateType(id, type);
}

public int updateStatus(int id, int status){
    return discussPostMapper.updateStatus(id, status);
}
```

c. controller

在DiscussPostController新增：

```java
// 置顶
@RequestMapping(path = "/top", method = RequestMethod.POST)
@ResponseBody  // 异步请求
public String setTop(int id){
    discussPostService.updateType(id, 1);
    
    // 别忘了把最新的帖子状态同步给es
    Event event = new Event()
            .setTopic(TOPIC_PUBLISH)
            .setUserId(hostHolder.getUser().getId())
            .setEntityType(ENTITY_TYPE_POST)
            .setEntityId(id);
    eventProducer.fireEvent(event);
    
    return CommunityUtil.getJSONString(0);
}

// 加精
@RequestMapping(path = "/wonderful", method = RequestMethod.POST)
@ResponseBody
public String setWonderful(int id){
    discussPostService.updateStatus(id, 1);
    
    Event event = new Event()
            .setTopic(TOPIC_PUBLISH)
            .setUserId(hostHolder.getUser().getId())
            .setEntityType(ENTITY_TYPE_POST)
            .setEntityId(id);
    eventProducer.fireEvent(event);

    return CommunityUtil.getJSONString(0);
}

// 删除
@RequestMapping(path = "/delete", method = RequestMethod.POST)
@ResponseBody
public String setDelete(int id){
    discussPostService.updateStatus(id, 2);

    // 这时同步es应该是删除帖子
    Event event = new Event()
            .setTopic(TOPIC_DELETE)
            .setUserId(hostHolder.getUser().getId())
            .setEntityType(ENTITY_TYPE_POST)
            .setEntityId(id);
    eventProducer.fireEvent(event);

    return CommunityUtil.getJSONString(0);
}
```

同步更新常量：

```java
// 删帖
String TOPIC_DELETE = "delete";
```

更新消费者：

```java
// 消费删帖事件
@KafkaListener(topics = {TOPIC_DELETE})
public void handleDeleteMessage(ConsumerRecord record){
    if(record == null || record.value() == null){
        logger.error("消息的内容为空！");
    }
    Event event = JSONObject.parseObject(record.value().toString(), Event.class);
    if(event == null){
        logger.error("消息格式错误！");
    }

    elasticsearchService.deleteDiscussPost(event.getEntityId());
}
```

d. 前端——帖子详情页面

```html
<div class="float-right">
  <input type="hidden" id="postId" th:value="${post.id}">
  <button type="button" class="btn btn-danger btn-sm" id="topBtn"
          th:disabled="${post.type==1}">置顶</button>
  <button type="button" class="btn btn-danger btn-sm" id="wonderfulBtn"
          th:disabled="${post.status==1}">加精</button>
  <button type="button" class="btn btn-danger btn-sm" id="deleteBtn"
          th:disabled="${post.status==2}">删除</button>
</div>
```

js逻辑处理：

```javascript
// window.onload()  用于给按钮绑定事件
$(function(){
    $("#topBtn").click(setTop);
    $("#wonderfulBtn").click(setWonderful);
    $("#deleteBtn").click(setDelete);
});

function setTop(){
  let token = $("meta[name= '_csrf']").attr("content");
  let header = $("meta[name= '_csrf_header']").attr("content");
  $(document).ajaxSend(function (e, xhr, options){
    xhr.setRequestHeader(header, token);
  });

  $.post(
          CONTEXT_PATH + "/discuss/top",
          {"id":$("#postId").val()},
          function (data){
            data = $.parseJSON(data);
            if(data.code == 0){
              // 点过置顶按钮后disable
              $("#topBtn").attr("disabled", "disabled");
            }else{
              alert(data.msg);
            }
          }
  );
}

function setWonderful(){
  let token = $("meta[name= '_csrf']").attr("content");
  let header = $("meta[name= '_csrf_header']").attr("content");
  $(document).ajaxSend(function (e, xhr, options){
    xhr.setRequestHeader(header, token);
  });

  $.post(
          CONTEXT_PATH + "/discuss/wonderful",
          {"id":$("#postId").val()},
          function (data){
            data = $.parseJSON(data);
            if(data.code == 0){
              $("#wonderfulBtn").attr("disabled", "disabled");
            }else{
              alert(data.msg);
            }
          }
  );
}

function setDelete(){
  let token = $("meta[name= '_csrf']").attr("content");
  let header = $("meta[name= '_csrf_header']").attr("content");
  $(document).ajaxSend(function (e, xhr, options){
    xhr.setRequestHeader(header, token);
  });

  $.post(
          CONTEXT_PATH + "/discuss/delete",
          {"id":$("#postId").val()},
          function (data){
            data = $.parseJSON(data);
            if(data.code == 0){
              // 删除成功后帖子就没了
              location.href = CONTEXT_PATH + "/index";
            }else{
              alert(data.msg);
            }
          }
  );
}
```

3. 权限管理

在SecurityConfig新增：

```java
http.authorizeHttpRequests(authorize -> authorize.requestMatchers(
                        "/user/setting",  // 用户设置
                        "/user/upload",   // 上传头像
                        "/user/updatePassword",  // 修改密码
                        "/discuss/add",   // 上传帖子
                        "/comment/add/**", // 评论
                        "/letter/**",     // 私信
                        "/notice/**",    // 通知
                        "/like",         // 点赞
                        "/follow",       // 关注
                        "/unfollow"      // 取消关注
                ).hasAnyAuthority(         // 这些功能只要登录就行
                        AUTHORITY_USER,
                        AUTHORITY_ADMIN,
                        AUTHORITY_MODERATOR
                )
                .requestMatchers(
                        "/discuss/top",
                        "/discuss/wonderful"
                ).hasAnyAuthority(
                        AUTHORITY_MODERATOR
                )
                .requestMatchers(
                        "/discuss/delete"
                ).hasAnyAuthority(
                        AUTHORITY_ADMIN
                )
                .anyRequest().permitAll()   // 其他任何请求都放行
        );
```

4. 按钮按权限显示

在discuss-detail修改：

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/extras/spring-security">

<div class="float-right">
  <input type="hidden" id="postId" th:value="${post.id}">
  <button type="button" class="btn btn-danger btn-sm" id="topBtn"
          th:disabled="${post.type==1}" sec:authorize="hasAnyAuthority('moderator')">置顶</button>
  <button type="button" class="btn btn-danger btn-sm" id="wonderfulBtn"
          th:disabled="${post.status==1}" sec:authorize="hasAnyAuthority('moderator')">加精</button>
  <button type="button" class="btn btn-danger btn-sm" id="deleteBtn"
          th:disabled="${post.status==2}" sec:authorize="hasAnyAuthority('admin')">删除</button>
</div>
```