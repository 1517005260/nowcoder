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