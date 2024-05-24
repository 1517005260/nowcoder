# 文件上传至[云服务器](https://www.qiniu.com/)

- 客户端上传
  - 客户端将数据提交给云服务器，并等待其响应
  - 用户上传头像时，将表单数据提交给云服务器
- 服务器直传
  - 应用服务器将数据直接提交给云服务器，并等待其响应
  - 分享时，服务端将自动生成的图片直接提交给云服务器


## 七牛云创建
1. 使用`对象存储`功能进行图片存储

2. 创建对象存储应为公有，因为要让其他人访问。
- 注：分配的域名30days有效，过期后需要自己更换
- 正常情况下应该让 header 和 share 分开存储，但是这里为了方便我只用了一个存储空间（网站访问量也不会太大）

## 重构功能

1. 导包

```xml
<dependency>
    <groupId>com.qiniu</groupId>
    <artifactId>qiniu-java-sdk</artifactId>
    <version>7.2.23</version>
</dependency>
```

配置，在application.properties中：

```
# qiniuKey
qiniu.key.access = [识别用户身份]
qiniu.key.secret = [文件内容加密]
# key记得定期更换
# 配置空间名字和域名
qiniu.bucket.header.name = glkcommunity
qiniu.bucket.header.url = http://sdz6flgi0.hd-bkt.clouddn.com
qiniu.bucket.share.name = glkcommunity
qiniu.bucket.share.url = http://sdz6flgi0.hd-bkt.clouddn.com
```

2. 传头像——客户端上传

在UserController：

```java
@Value("${qiniu.key.access}")
private String accessKey;

@Value("${qiniu.key.secret}")
private String secretKey;

@Value("${qiniu.bucket.header.name}")
private String headerBucketName;

@Value("${qiniu.bucket.header.url}")
private String headerBucketUrl;

// 原上传头像方法和查看头像方法废弃

// 重构如下：

@LoginRequired
@RequestMapping(path = "/setting", method = RequestMethod.GET)
public String getSettingPage(Model model){
  // 上传头像
  String fileName = CommunityUtil.genUUID();
  // 设置响应信息
  StringMap policy = new StringMap();
  policy.put("returnBody", CommunityUtil.getJSONString(0));
  // 生成上传七牛云的凭证
  Auth auth = Auth.create(accessKey, secretKey);
  String token = auth.uploadToken(headerBucketName, fileName, 3600, policy);

  model.addAttribute("uploadToken", token);
  model.addAttribute("fileName", fileName);
  return "/site/setting";
}

// 更新头像路径
@RequestMapping(path = "/header/url", method = RequestMethod.POST)
@ResponseBody
public String updateHeaderUrl(String fileName){
  if(fileName == null){
    return CommunityUtil.getJSONString(1, "文件名为空！");
  }
  String url = headerBucketUrl + "/" + fileName;
  userService.updateHeader(hostHolder.getUser().getId(), url);
  return CommunityUtil.getJSONString(0);
}
```

Setting.html处理提交表单：

```html
<!-- 上传头像 -->
<h6 class="text-left text-info border-bottom pb-2">上传头像</h6>
<form class="mt-5" id="uploadForm">
    <div class="form-group row mt-4">
        <label for="head-image" class="col-sm-2 col-form-label text-right">选择头像:</label>
        <div class="col-sm-10">
            <div class="custom-file">
                <input type="hidden" name="token" th:value="${uploadToken}">
                <input type="hidden" name="key" th:value="${fileName}">
                <input type="file" class="custom-file-input" id="head-image" name="file" lang="es" required="">
                <label class="custom-file-label" for="head-image" data-browse="文件">选择一张图片</label>
                <div class="invalid-feedback">
                    该账号不存在!
                </div>
            </div>
        </div>
    </div>
    <div class="form-group row mt-4">
        <div class="col-sm-2"></div>
        <div class="col-sm-10 text-center">
            <button type="submit" class="btn btn-info text-white form-control">立即上传</button>
        </div>
    </div>
</form>

<script>
  // 上传头像异步表单提交逻辑
  $(function (){  // 页面加载完就调用本函数
    $("#uploadForm").submit(upload);
  });

  function upload(){
    let token = $("meta[name= '_csrf']").attr("content");
    let header = $("meta[name= '_csrf_header']").attr("content");
    $(document).ajaxSend(function (e, xhr, options){
      xhr.setRequestHeader(header, token);
    });

    $.ajax({
      url:"http://up-z0.qiniup.com",
      method:"post",
      processData: false, // 不要把表单转换成字符串
      contentType:false, // 不让jquery设置上传类型
      data:new FormData($("#uploadForm")[0]),
      success:function (data){
        if(data && data.code == 0){
          let token = $("meta[name= '_csrf']").attr("content");
          let header = $("meta[name= '_csrf_header']").attr("content");
          $(document).ajaxSend(function (e, xhr, options){
            xhr.setRequestHeader(header, token);
          });
          // 更新头像url
          $.post(
                  CONTEXT_PATH + "/user/header/url",
                  {
                    "fileName": $("input[name='key']").val()
                  },
                  function (data) {
                    data = $.parseJSON(data);
                    if (data.code == 0) {
                      window.location.reload();
                    }
                    else {
                      alert(data.msg)
                    }
                  }
          )
        }else {
          alert("上传头像失败！");
        }
      }
    })

    return false;  // 不提交表单（因为无action了，逻辑全交由七牛云），事件到此结束
  }
</script>
```

3. 传分享图片——服务端直传

在ShareController:

```java
@Value("${qiniu.bucket.share.url}")
private String shareBucketUrl;

// 修改上传路径即可：
map.put("shareUrl", shareBucketUrl + "/" + fileName);

// 原访问长图功能废弃，因为不是看本地的图片了，一切给七牛云处理
// 原逻辑交由消费者处理
```

在EventConsumer:

```java
@Value("${qiniu.key.access}")
private String accessKey;

@Value("${qiniu.key.secret}")
private String secretKey;

@Value("${qiniu.bucket.share.name}")
private String shareBucketName;

@Autowired
private ThreadPoolTaskScheduler taskScheduler;

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
  // 生成长图
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

  // 上传长图
  // 注意：这里cmd命令和java主线程是并发执行且速度不同步
  // 所以必须等cmd生成完了才能上传
  // 但是程序不能阻塞干等，我们启动一个定时器，每隔0.5s轮询一次，但若30s后cmd还未好，则认为生成长图失败
  // 而且consumer具有竞争机制，本方法只会在一个server上执行，不涉及quartz
  UploadTask task = new UploadTask(fileName, suffix);
  Future future = taskScheduler.scheduleAtFixedRate(task, 500);
  task.setFuture(future);
}

class UploadTask implements Runnable{
  // 文件名
  private String fileName;
  // 文件后缀
  private String suffix;
  // future 启动任务的返回值，可以用来在Runnable内停止任务
  private Future future;
  // 任务开始时间
  private long startTime;
  // 上传次数
  private int uploadTimes;

  public UploadTask(String fileName, String suffix) {
    this.fileName = fileName;
    this.suffix = suffix;
    this.startTime = System.currentTimeMillis();
  }

  public void setFuture(Future future) {
    this.future = future;
  }

  @Override
  public void run() {
    // 1. 生成图片失败
    if(System.currentTimeMillis() - startTime > 30000){
      logger.error("生成长图时间过长，中止任务！" + fileName);
      future.cancel(true);
      return;
    }
    // 2. 上传失败
    if(uploadTimes >= 3){
      logger.error("上传图片次数过多，中止任务！" + fileName);
      future.cancel(true);
      return;
    }

    String path = wkImageStorage + "/" + fileName + suffix; // 本地路径
    File file = new File(path);
    if (file.exists()) {
      logger.info(String.format("开始第%d次上传[%s].", ++uploadTimes, fileName));
      // 设置响应信息
      StringMap policy = new StringMap();
      policy.put("returnBody", CommunityUtil.getJSONString(0));
      // 生成上传凭证
      Auth auth = Auth.create(accessKey, secretKey);
      String uploadToken = auth.uploadToken(shareBucketName, fileName, 3600, policy);
      // 指定上传机房
      UploadManager manager = new UploadManager(new Configuration(Zone.zone0())); // 华东-浙江
      try {
        // 开始上传图片
        Response response = manager.put(
                path, fileName, uploadToken, null, "image/" + suffix, false);
        // 处理响应结果
        JSONObject json = JSONObject.parseObject(response.bodyString());
        if (json == null || json.get("code") == null || !json.get("code").toString().equals("0")) {
          logger.info(String.format("第%d次上传失败[%s].", uploadTimes, fileName));
        } else {
          logger.info(String.format("第%d次上传成功[%s].", uploadTimes, fileName));
          future.cancel(true);
        }
      } catch (QiniuException e) {
        logger.info(String.format("第%d次上传失败[%s].", uploadTimes, fileName));
      }
    } else {
      logger.info("等待图片生成[" + fileName + "].");
    }
  }
}
```