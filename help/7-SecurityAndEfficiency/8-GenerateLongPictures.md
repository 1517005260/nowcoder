# 生成长图

本功能常见于app的分享，相当于给内容截图

- 实现
  - 客户端直接截图
  - 服务端有模板，根据客户端内容生成对应长图
- 服务端利用html模板生成长图
  - [wkhtmltopdf](https://wkhtmltopdf.org/)
    - `wkhtmltopdf [url] [file]` 模板生成pdf
    - `wkhtmltoimage [url] [file]` 网页生成本地图片
  - java
    - Runtime.getRuntime.exec()

## 安装与使用

1. 环境变量：`D:\wkhtmltopdf\bin`

2. 新建好data文件夹存放pdf和图片

存为pdf：

```bash
C:\Users\15170>wkhtmltopdf https://www.nowcoder.com D:\wkhtmltopdf\data\pdfs\1.pdf
Loading pages (1/6)
Counting pages (2/6)
Resolving links (4/6)
Loading headers and footers (5/6)
Printing pages (6/6)
Done
```

存为图片：

```bash
C:\Users\15170>wkhtmltoimage https://www.nowcoder.com D:\wkhtmltopdf\data\imgs\1.png
Loading page (1/2)
Rendering (2/2)
Done

// 图片压缩
C:\Users\15170>wkhtmltoimage --quality 75 https://www.nowcoder.com D:\wkhtmltopdf\data\imgs\1.png
Loading page (1/2)
Rendering (2/2)
Done
```

用Java语言而非命令行操控：

- 新建测试类WkTests

```java
package com.nowcoder.community;

import java.io.IOException;

public class WkTests {
    public static void main(String[] args) throws IOException {
        String cmd = "D:\\wkhtmltopdf\\bin\\wkhtmltoimage --quality 75 https://www.nowcoder.com D:\\wkhtmltopdf\\data\\imgs\\2.png";

        Runtime.getRuntime().exec(cmd);
        System.out.println("ok");
    }
}
```

可以发现ok输出远早于图片生成。因为java和cmd是并发的，java把命令提交给cmd后，会继续执行自己的程序，不等cmd执行

## 代码实现

1. 配置cmd命令为常量字符串，并用程序自动创建存储目录：

在application.properties新增：

```
# wkhtmltoimage
wk.image.command = D:/wkhtmltopdf/bin/wkhtmltoimage
wk.image.storage = D:/wkhtmltopdf/data/imgs
```

新建WkConfig:

```java
package com.nowcoder.community.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class  WkConfig {

    private static final Logger logger = LoggerFactory.getLogger(WkConfig.class);

    @Value("${wk.image.storage}")
    private String WkImageStorage;

    @PostConstruct // 主服务启动前先检查有无目录
    public void init(){
        File file = new File(WkImageStorage);
        if(!file.exists()){
            file.mkdir();
            logger.info("创建wk图片目录：" + WkImageStorage);
        }
    }
}
```

2. 新建ShareController

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.Event;
import com.nowcoder.community.event.EventProducer;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class ShareController implements CommunityConstant {
    private static final Logger logger = LoggerFactory.getLogger(ShareController.class);

    @Autowired
    private EventProducer eventProducer;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Value("${wk.image.storage}")
    private String wkImageStorage;

    // 生成长图
    @RequestMapping(path = "/share", method = RequestMethod.GET)
    @ResponseBody
    public String share(String htmlUrl){
        String fileName = CommunityUtil.genUUID();

        // 模仿java与cmd的异步模式
        Event event = new Event()
                .setTopic(TOPIC_SHARE)
                .setData("htmlUrl", htmlUrl)
                .setData("fileName", fileName)
                .setData("suffix", ".png");
        eventProducer.fireEvent(event);

        // 返回长图的访问路径
        Map<String, Object> map = new HashMap<>();
        map.put("shareUrl", domain + contextPath + "/share/image/" + fileName);

        return CommunityUtil.getJSONString(0, null, map);
    }
}
```

同步更新常量接口：

```java
// 分享
String TOPIC_SHARE = "share";
```

消费掉异步请求：

```java
@Value("${wk.image.storage}")
private String wkImageStorage;

@Value("${wk.image.command}")
private String wkImageCommand;

// 消费分享事件
@KafkaListener(topics = {TOPIC_SHARE})
public void handleShareMessage(ConsumerRecord record){
  if(record == null || record.value() == null){
    logger.error("消息的内容为空！");
  }
  Event event = JSONObject.parseObject(record.value().toString(), Event.class);
  if(event == null){
    logger.error("消息格式错误！");
  }

  String htmlUrl = (String) event.getData().get("htmlUrl");
  String fileName = (String) event.getData().get("fileName");
  String suffix = (String) event.getData().get("suffix");

  String cmd = wkImageCommand + " --quality 75 " +
          htmlUrl + " " + wkImageStorage + "/" + fileName + suffix;
  try {
    Runtime.getRuntime().exec(cmd);
    logger.info("生成长图成功！" + cmd);
  } catch (IOException e) {
    logger.error("生成长图失败！" + e.getMessage());
  }
}
```

3. 在ShareController中获取长图

```java
// 访问长图
// 不是返回模板而是返回图片，参考头像上传处代码
@RequestMapping(path = "/share/image/{fileName}", method = RequestMethod.GET)
public void getShareImage(@PathVariable("fileName")String fileName, HttpServletResponse response){
    if(StringUtils.isBlank(fileName)){
        throw new IllegalArgumentException("文件名不能为空！");
    }

    // 输出格式
    response.setContentType("/image/png");

    // 读取本地文件
    File file = new File(wkImageStorage + "/" + fileName + ".png");
    // 输出本地文件
    try {
        OutputStream os = response.getOutputStream();  // 获取响应输出流
        FileInputStream fis = new FileInputStream(file);  // 使用fis读取文件
        byte[] buffer = new byte[1024]; // 缓冲区
        int b;  //游标
        while ((b = fis.read(buffer)) != -1){  // 通过while循环读取文件内容，并将其写入输出流中，直到文件读取完毕。
            os.write(buffer, 0, b);
        }
    } catch (IOException e) {
        logger.error("获取长图失败！" + e.getMessage());
    }
}
```

现在我们已经模拟好了后端的生成长图开发，由于该技术常用于app而web端少用，接下了自己开发简易的分享功能

## 自己补充——完善分享功能

1. 配置Security，只有登录用户才能分享

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
                        "/unfollow",      // 取消关注
                        "/share/**"      // 分享
                ).hasAnyAuthority(         // 这些功能只要登录就行
                        AUTHORITY_USER,
                        AUTHORITY_ADMIN,
                        AUTHORITY_MODERATOR
                )
```

2. 分享——在帖子详情页面点击分享，获得本页面的图片

帖子详情的分享按钮：

```html
<style>
  .custom-alert {
    display: none;
    position: fixed;
    top: 10%;
    left: 50%;
    transform: translateX(-50%);
    background-color: #f8d7da;
    color: #721c24;
    padding: 10px 20px;
    border-radius: 5px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
    z-index: 1000;
    font-size: 16px;
    line-height: 1.5;
  }
</style>

<button type="button" class="btn btn-danger btn-sm" id="shareBtn"
        sec:authorize="hasAnyAuthority('moderator', 'admin', 'user')">分享</button>
<div id="customAlert" class="custom-alert">链接复制成功！</div>


<!--并且给标题和作者id，方便js查找-->
<span th:utext="${post.title}" id="title">备战春招，面试刷题跟他复习，一个月全搞定！</span>
<div class="mt-0 text-warning" th:utext="${user.username}" id="author">寒江雪</div>
```

在controller增加用户下载：

```java
response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + ".png\"");
```

增加分享的js逻辑：

```javascript
$("#shareBtn").click(share);

var titleText="";
var authorText = "";
document.addEventListener("DOMContentLoaded", function() {
  // 获取包含ID为"title"的元素
  var title = document.getElementById("title");

  // 获取元素的文本值
  titleText = title.innerText; // 或者使用 textContent

  var author = document.getElementById("author")
  authorText = author.innerText;
});
function share(){
  let currentUrl = window.location.href;
  // 组织要复制的内容
  const formattedText = `${titleText} - ${authorText}的帖子 - 校园论坛\n${currentUrl}`;

  // 使用Clipboard API复制格式化后的内容到剪切板
  navigator.clipboard.writeText(formattedText).then(() => {
    // 显示自定义提示框
    const customAlert = document.getElementById('customAlert');
    customAlert.style.display = 'block';

    // 1秒后自动隐藏提示框
    setTimeout(() => {
      customAlert.style.display = 'none';
    }, 1000);
  }).catch(err => {
    // 复制失败时提示用户
    alert('复制失败: ' + err);
  });
}
```