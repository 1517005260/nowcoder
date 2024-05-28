# 发布帖子

本节课会用到异步请求（部分刷新）

- [AJAX](https://developer.mozilla.org/zh-CN/docs/Glossary/AJAX)
  - 全称：`Asychronous JavaScript and XML`
  - 异步的JS和XML，不是一门新技术，而是一个新的术语
  - 使用ajax，网页能够将增量更新呈现在页面上，而不需要刷新整个页面
  - 虽然X代表XML，但是现在JSON使用的比XML更加普遍
- 示例
  - 使用jQuery发送AJAX请求
- 实现
  - 采用AJAX请求，实现发布帖子的功能


## 示例

1. 导包`fastjson`以方便处理json

```xml
<dependency>
  <groupId>com.alibaba</groupId>
  <artifactId>fastjson</artifactId>
  <version>2.0.25</version>
</dependency>
```

2. 利用包的api建一些快捷方法：

在`CommunityUtil`中新增

```java
    // 取得json串，转化成字符串
    public static String getJSONString(int code, String message, Map<String, Object> map){
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("message", message);
        if(map != null){
            for(String key : map.keySet()){
                json.put(key,map.get(key));
            }
        }
        return json.toJSONString();
    }
    
    // 有时可能以上三个数据只有两个甚至一个，为了方便起见，我们进行重载
    public static String getJSONString(int code, String message){
        return getJSONString(code, message, null);
    }
    public static String getJSONString(int code){
        return getJSONString(code, null, null);
    }
```

之后，可在这个类中直接测试：

```java
    // test-json
    public static void main(String[] args) {
        Map<String, Object> map =new HashMap<>();
        map.put("name", "peter");
        map.put("age", 25);
        System.out.println(getJSONString(0, "ok", map));
    }
```

输出： `{"code":0,"name":"peter","message":"ok","age":25}`

3. ajax示例

在AlphaController新增方法处理请求：

```java
    @RequestMapping(path = "/ajax", method = RequestMethod.POST)
    @ResponseBody // 返回非html
    public String testAJAX(String name, int age){
        System.out.println(name);
        System.out.println(age);
        return CommunityUtil.getJSONString(0, "success");
    }
```

新建`static/html/ajax_demo.html`的简易前端：

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Title</title>
</head>
<body>
<p>
    <input type="button" value="发送" onclick="send();">
</p>
<script src="https://code.jquery.com/jquery-3.3.1.min.js" crossorigin="anonymous"></script>
<script>
    function send(){
        $.post(
            "/community/alpha/ajax",  //url
            {"name":"peter", "age":18}, // post_data
            function (data){           //response （无名字，匿名函数）
                console.log(typeof (data));
                console.log(data);
                
                data = $.parseJSON(data);
                console.log(typeof (data));
                console.log(data.code);
                console.log(data.message);
            }
        );
    }
</script>
</body>
</html>
```

访问：`http://localhost:8080/community/html/ajax_demo.html` 后f12查看后台即可

## 发布帖子

1. dao层

在DiscussPostMapper中新增：

```java
// 发布帖子
int insertDiscussPost(DiscussPost discussPost);
```

2. 在 discusspost-mapper.xml中新增sql语句：

```xml
<sql id="insertFields">
  user_id, title, content, type, status, create_time, comment_count, score
</sql>

<insert id="insertDiscussPost" parameterType="DiscussPost">
insert into discuss_post(<include refid="insertFields"></include>)
values(#{userId},#{title},#{content},#{type},#{status},#{createTime},#{commentCount},#{score})
</insert>
```

3. service层

需要过滤敏感词

在DiscussPostService追加：

```java
@Autowired
private SensitiveFilter sensitiveFilter;

public int addDiscussPost(DiscussPost discussPost){
        if(discussPost == null){
            throw new IllegalArgumentException("参数不能为空！");
        }
        
        //敏感词过滤：标题+内容
        //并且处理用户上传的标签，比如 <script>abcd</script> 即转义html
        discussPost.setTitle(HtmlUtils.htmlEscape(discussPost.getTitle()));
        discussPost.setContent(HtmlUtils.htmlEscape(discussPost.getContent()));
        discussPost.setTitle(sensitiveFilter.filter(discussPost.getTitle()));
        discussPost.setContent(sensitiveFilter.filter(discussPost.getContent()));
        
        return discussPostMapper.insertDiscussPost(discussPost);
    }
```

4. controller层

新建DiscussPostController:

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;

@Controller
@RequestMapping("/discuss")
public class DiscussPostController {
  @Autowired
  private DiscussPostService discussPostService;

  @Autowired
  private HostHolder hostHolder;

  //处理增加帖子异步请求
  @RequestMapping(path = "/add", method = RequestMethod.POST)
  @ResponseBody
  public String addDiscussPost(String title, String content){
    User user = hostHolder.getUser();
    if(user == null){
      return CommunityUtil.getJSONString(403, "你还没有登录哦!");  // 403表示没有权限
    }
    if (title == null || title.trim().isEmpty()) {
      return CommunityUtil.getJSONString(400, "标题不能为空");  // 400表示请求错误
    }
    if (content == null || content.trim().isEmpty()) {
      return CommunityUtil.getJSONString(400, "内容不能为空！");
    }
    DiscussPost discussPost = new DiscussPost();
    discussPost.setUserId(user.getId());   //一定要记得插入id，否则前端接收到的user_id就是0，会报空指针异常
    discussPost.setTitle(title);
    discussPost.setContent(content);
    discussPost.setCreateTime(new Date());
    discussPostService.addDiscussPost(discussPost);

    return CommunityUtil.getJSONString(0, "发布成功！");
    //报错将来统一处理
  }
}
```

5. 前端 —— 点击 “我要发布” 发布帖子

在index.js中新增发布逻辑：

```javascript
$(function(){
  $("#publishBtn").click(publish);  //点击发布按钮时，调用发布方法
});

function publish() {
  $("#publishModal").modal("hide");  //发布的页面隐藏

  //获取标题和内容
  let title = $("#recipient-name").val();
  let content = $("#message-text").val();

  //ajax
  $.post(
          CONTEXT_PATH + "/discuss/add",
          {"title":title, "content":content},
          function (data){
            data = $.parseJSON(data);
            // 在提示框中显示返回消息
            $("#hintBody").text(data.message);
            // 显示提示框
            $("#hintModal").modal("show");
            // 2s后自动隐藏
            setTimeout(function(){
              $("#hintModal").modal("hide");
              //刷新页面
              if(data.code == 0){
                window.location.reload();
              }
              //清空输入框内容
              $("#recipient-name").val("");
              $("#message-text").val("");
            }, 2000);
          }
  )
}
```


index.html中“我要发布”增加登录判定
```html
<button type="button" class="btn btn-primary btn-sm position-absolute rt-0" data-toggle="modal" data-target="#publishModal" th:if="${loginUser!=null}">我要发布</button>
```

## 修改帖子

需求：在discusspost-detail中，增加修改帖子的按钮，使得用户能够编辑已经发布的帖子。需要更新内容和时间

1. 在DiscussPostMapper中新增：

```java
// 更新帖子
void updatePost(int id, String title, String content, Date time);
```

sql实现：

```xml
<update id="updatePost">
  update discuss_post set content = #{content}, create_time = #{time}, title = #{title}
  where id = #{id}
</update>
```

2. DiscussPostService追加:

```java
// 更新帖子
public void updatePost(DiscussPost discussPost){
  if(discussPost == null){
    throw new IllegalArgumentException("参数不能为空！");
  }

  discussPost.setTitle(HtmlUtils.htmlEscape(discussPost.getTitle()));
  discussPost.setContent(HtmlUtils.htmlEscape(discussPost.getContent()));
  discussPost.setTitle(sensitiveFilter.filter(discussPost.getTitle()));
  discussPost.setContent(sensitiveFilter.filter(discussPost.getContent()));

  discussPostMapper.updatePost(discussPost.getId(), discussPost.getTitle(), discussPost.getContent(), discussPost.getCreateTime());
}
```

3. DiscussPostController更新：

```java
// 进入更改帖子的页面
@RequestMapping(path = "/updatePost/{postId}", method = RequestMethod.GET)
public String getUpdatePage(@PathVariable("postId") int postId, Model model){
  DiscussPost post = discussPostService.findDiscussPostById(postId);
  if (post == null || post.getUserId() != hostHolder.getUser().getId()) {
    return "/error/404";  // 只能作者访问
  }
  model.addAttribute("title", post.getTitle());
  model.addAttribute("content", post.getContent());
  model.addAttribute("id", postId);

  return "/site/update-posts";
}

// 更改帖子请求
@RequestMapping(path = "/update/{postId}", method = RequestMethod.POST)
@ResponseBody
public String UpdateDiscussPost(@PathVariable("postId") int postId,String title, String content){
  User user = hostHolder.getUser();
  if(user == null){
    return CommunityUtil.getJSONString(403, "你还没有登录哦!");
  }
  DiscussPost post = discussPostService.findDiscussPostById(postId);
  if (post == null || post.getUserId() != user.getId()) {
    return CommunityUtil.getJSONString(403, "你没有权限修改此帖子!");
  }
  post.setTitle(title);
  post.setContent(content);
  post.setCreateTime(new Date());
  discussPostService.updatePost(post);

  // 改帖事件，存进es服务器
  Event event = new Event()
          .setTopic(TOPIC_PUBLISH)
          .setUserId(user.getId())
          .setEntityType(ENTITY_TYPE_POST)
          .setEntityId(post.getId());
  eventProducer.fireEvent(event);

  // 初始分数计算
  String redisKey = RedisKeyUtil.getPostScoreKey();
  redisTemplate.opsForSet().add(redisKey, post.getId());

  return CommunityUtil.getJSONString(0, "修改成功！");
}
```

Security配置：

```java
http.authorizeHttpRequests(authorize -> authorize.requestMatchers(
                        "/user/setting",  // 用户设置
                        "/user/upload",   // 上传头像
                        "/user/updatePassword",  // 修改密码
                        "/user/updateUsername", // 修改名字
                        "/discuss/add",   // 上传帖子
                        "/discuss/publish", // 发布帖子页
                        "/discuss/update/**", // 帖子修改
                        "/discuss/updatePost/**",
                        "/comment/add/**", // 评论
                        "/letter/**",     // 私信
                        "/notice/**",    // 通知
                        "/like",         // 点赞
                        "/follow",       // 关注
                        "/unfollow",      // 取消关注
                        "/share/**"      // 分享
                ).hasAnyAuthority(         // 这些功能只要登录就行
                        AUTHORITY_USER,
                        AUTHORITY_ADMIN,
                        AUTHORITY_MODERATOR
                )
```

4. discusspost-detail按钮更新：

```html
<button type="button" class="btn btn-danger btn-sm" id="updateBtn"
    sec:authorize="hasAnyAuthority('moderator', 'admin', 'user')"
    th:if="${post.userId == loginUser.id}" th:onclick="|update(${post.id})|">修改</button>
```

js代码：

```javascript
function update(id){
    window.location.href = CONTEXT_PATH + "/discuss/updatePost/" + id;
}
```

新页面update-posts:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="https://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    <meta name="_csrf" th:content="${_csrf.token}">
    <meta name="_csrf_header" th:content="${_csrf.headerName}">
    <link rel="icon" th:href="@{/img/icon.png}"/>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css" crossorigin="anonymous">
    <link rel="stylesheet" th:href="@{/css/global.css}" />
    <link rel="stylesheet" type="text/css" th:href="@{/editor-md/css/editormd.css}" />
    <title>修改帖子</title>
</head>
<body class="bg-white">
<div class="nk-container">
    <!-- 头部 -->
    <header class="bg-dark sticky-top" th:replace="index::header">
    </header>

    <!-- 内容 -->
    <div class="main" style="background-color: rgb(238,238,238)">
        <div class="container mt-5">
            <div class="form-group">
                <input type="text" class="form-control" style="font-size: 24px; font-weight: 500;"
                       id="recipient-name" placeholder="输入文章标题..." required th:value="${title}">
            </div>

            <div id="test-editormd" style="width:2000px;">
                <textarea class="form-control" id="message-text" style="display:none;" th:text="${content}"></textarea>
            </div>

            <div style="text-align: center">
                <button type="button" class="btn btn-outline-secondary" id="backIndexBtn">返回首页</button>
                <button type="button" class="btn btn-outline-primary" id="publishBtn"
                        style="color: rgb(51, 133, 255)"
                        th:onclick="|publish(${id})|">修改文章</button>
            </div>

            <!-- 提示框 -->
            <div class="modal fade" id="hintModal" tabindex="-1" role="dialog" aria-labelledby="hintModalLabel" aria-hidden="true">
                <div class="modal-dialog modal-lg" role="document">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title" id="hintModalLabel">提示</h5>
                        </div>
                        <div class="modal-body" id="hintBody"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- 尾部 -->
    <footer class="bg-dark" th:replace="index::footer">
    </footer>

</div>
<script src="https://code.jquery.com/jquery-3.3.1.min.js" crossorigin="anonymous"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js" crossorigin="anonymous"></script>
<script src="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/js/bootstrap.min.js" crossorigin="anonymous"></script>
<script th:src="@{/js/global.js}"></script>
<script th:src="@{/editor-md/editormd.min.js}"></script>
<script type="text/javascript">
    var testEditor;

    $(function() {
        testEditor = editormd("test-editormd", {
            width: "90%",
            height: 640,
            syncScrolling: "single",
            path: "../../editor-md/lib/",
            saveHTMLToTextarea: true, // 方便post提交表单
            imageUpload: false,
            placeholder: "欢迎来到帖子编辑界面~ 本论坛支持 Markdown/非Markdown 格式的帖子~"
        });
    });

    $(function() {
        $("#backIndexBtn").click(backIndex);
    });

    function publish(id) {
        var title = $('#recipient-name').val().trim();
        var content = testEditor.getMarkdown().trim();

        if (title === "") {
            showHintModal("标题不能为空");
            return;
        }

        if (content === "") {
            showHintModal("内容不能为空");
            return;
        }

        $('#publishModal').modal('hide');

        let token = $("meta[name='_csrf']").attr("content");
        let header = $("meta[name='_csrf_header']").attr("content");
        $(document).ajaxSend(function(e, xhr, options) {
            xhr.setRequestHeader(header, token);
        });

        $.post(
            CONTEXT_PATH + "/discuss/update/" + id,
            {"title": title, "content": content},
            function(data) {
                data = $.parseJSON(data);
                $("#hintBody").text("修改成功！");
                $("#hintModal").modal("show");
                setTimeout(function() {
                    $("#hintModal").modal("hide");
                    if (data.code == 0) {
                        location.href = CONTEXT_PATH + "/index";
                    }
                }, 2000);
            }
        );
    }

    function showHintModal(message) {
        $("#hintBody").text(message);
        $("#hintModal").modal("show");
        setTimeout(function() {
            $("#hintModal").modal("hide");
        }, 2000);
    }

    function backIndex() {
        location.href = CONTEXT_PATH + "/index";
    }
</script>
</body>
</html>
```