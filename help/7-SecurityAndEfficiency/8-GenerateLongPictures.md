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

