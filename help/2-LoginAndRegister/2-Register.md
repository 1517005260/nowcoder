# 注册功能

- 访问注册页面`请求1`
  - 点击顶部区域内的链接，打开注册页面
- 提交注册数据`请求2`
  - 通过表单提交数据
  - 服务端验证账号是否已存在，邮箱是否已注册
  - 服务端发送激活邮件
- 激活注册账号`请求3`
  - 点击邮件中的链接，访问服务端的激活服务


==> 复杂的功能（web端）按请求拆分成小需求，每次请求开发都遵循`数据层->业务层->视图层`

## 请求1：访问注册页面

本请求没有数据层和业务层，用户点击后直接跳转即可

1. 在controller下新建`LoginController`（集成注册和登录）

```java
package com.nowcoder.community.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class LoginController {

    @RequestMapping(path = "/register", method = RequestMethod.GET)
    public String getRegisterPage(){
        return "/site/register";
    }
}
```

2. 修改对应的`register.html`为thymeleaf模板

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">
...
<link rel="stylesheet" th:href="@{/css/global.css}" />
<link rel="stylesheet" th:href="@{/css/login.css}" />
...
<script th:src="@{/js/global.js}"></script>
<script th:src="@{/js/register.js}"></script>
```

3. 修改`index.html`使得超链接正确，并且使得首页的header能够被复用

```html
<header class="bg-dark sticky-top" th:fragment="header">  <!--fragment 说白了就是取名字 -->
...
<a class="nav-link" th:href="@{/index}">首页</a>
...
<a class="nav-link" th:href="@{site/register}">注册</a>
```

这样，在`register.html`里，头部的导航栏之类的就不需要再写了：

```html
<header class="bg-dark sticky-top" th:replace="index::header">
```

## 请求2：提交注册数据

1. 导入jar包，用于判断字符串和集合等的空值
```xml
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-lang3</artifactId>
  <version>3.12.0</version>
</dependency>
```

2. 配置网站域名（现在没有，所以配本机ip），用于发邮件的激活地址: 
```
# community
community.path.domain=http://localhost:8080
```

3. 和发送邮件一样新写一个`util`类以复用

```java
package com.nowcoder.community.util;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.DigestUtils;

import java.util.SplittableRandom;
import java.util.UUID;

// 都是些简单方法，不需要容器托管，故不要注解
public class CommunityUtil {

    // 生成随机字符串，可用于：邮件激活链接、用户上传文件命名等
    public static String genUUID(){
        return UUID.randomUUID().toString().replaceAll("-","");  //去除横线
    }
    
    // 加密算法，可用于：用户密码加密等。
    // MD5加密：加密容易解密难。并且为了防止黑客用对照表解密（比如hello固定被加密为abcde），加入“salt”随机字符串
    public static String md5(String key){
        if(StringUtils.isBlank(key)){
            // 判定空值
            return null;
        }
        return DigestUtils.md5DigestAsHex(key.getBytes());
    }
}
```

4. 开发注册业务：直接在之前的`UserService`上更新即可，因为注册也是属于用户的业务

```java
@Autowired
private MailClient mailClient;
    
@Autowired
private TemplateEngine templateEngine;
    
@Value("${community.path.domain}")
private String domain;  // 注入主域名，即“https://......”

@Value("${server.servlet.context-path}")
private String contextPath;  // 注入项目名，即“/community”

public Map<String, Object> register(User user){
  Map<String, Object> map = new HashMap<>();

  // 对空值判断处理
  if(user == null){  // 程序的错误
    throw new IllegalArgumentException("参数不能为空！");
  }
  if(StringUtils.isBlank(user.getUsername())){  //业务的漏洞
    map.put("usernameMsg", "账号不能为空！");
    return map;
  }
  if(StringUtils.isBlank(user.getPassword())){
    map.put("passwordMsg", "密码不能为空！");
    return map;
  }
  if(StringUtils.isBlank(user.getEmail())){
    map.put("emailMsg", "邮箱不能为空！");
    return map;
  }

  // 验证是否已经注册
  User u = userMapper.selectByName(user.getUsername());
  if(u != null){
    map.put("usernameMsg", "该账号已存在！");
    return map;
  }
  u = userMapper.selectByEmail(user.getEmail());
  if(u != null){
    map.put("emailMsg", "该邮箱已被使用！");
    return map;
  }

  // 注册，即存入数据库
  user.setSalt(CommunityUtil.genUUID().substring(0,5));  // 5位salt
  user.setPassword(CommunityUtil.md5(user.getPassword() + user.getSalt()));
  user.setType(0);  // 普通用户
  user.setStatus(0);  // 未激活
  user.setActivationCode(CommunityUtil.genUUID());  // 给个激活码
  user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png",
          new Random().nextInt(1000)));  //默认头像，由牛客网提供，格式：http://images.nowcoder.com/head/1t.png
  user.setCreateTime(new Date());
  userMapper.insertUser(user);  // 插入并生成id

  // 发送激活邮件，流程和上节课一样
  Context context = new Context();
  context.setVariable("email",user.getEmail());
  //激活路径：https://{domain}/community/activation/{userid}/{activate_code}
  String url = domain + contextPath + "/activation/" + user.getId() + "/" +user.getActivationCode();
  context.setVariable("url", url);
  String content = templateEngine.process("/mail/activation",context);
  mailClient.sendMail(user.getEmail(), "邮箱激活账号", content);

  return map;
}
```

5. 编写发送激活邮件的格式，在`/mail/activation.html`下：

```html
<!doctype html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <link rel="icon" href="https://static.nowcoder.com/images/logo_87_87.png"/>
    <title>邮箱激活账号</title>
</head>
<body>
	<div>
		<p>
			<b th:text="${email}">xxx@xxx.com</b>, 您好!
		</p>
		<p>
			您正在注册论坛, 这是一封激活邮件, 请点击 
			<a th:href="${url}">此链接</a>,
			激活您的账号!
		</p>
	</div>
</body>
</html>
```

6. 开发Controller，还是在`LoginController`中

```java
@Autowired
private UserService userService;

@RequestMapping(path = "/register", method = RequestMethod.POST)
public String register(Model model, User user){
    Map<String, Object> map = userService.register(user);

    if(map == null || map.isEmpty()){
        // 没有错误信息，注册成功，直接跳到首页
        model.addAttribute("msg", "注册成功，已经向您的邮箱发送了一封激活邮件，请尽快激活！");
        model.addAttribute("target", "/index");
        return "/site/operate-result";
        }else {
            model.addAttribute("usernameMsg", map.get("usernameMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            model.addAttribute("emailMsg", map.get("emailMsg"));
            return "/site/register";
        }
}
```

7. 编辑处理结果的页面：`site/operate-result.html`（成功）， `site/register.html`（失败，并且保留之前的账号密码，删除错误提示）:

```html
<html lang="en" xmlns:th="https://www.thymeleaf.org">
<link rel="stylesheet" th:href="@{/css/global.css}" />
<header class="bg-dark sticky-top" th:replace="index::header">
...
<!-- 内容 -->
<div class="main">
  <div class="container mt-5">
    <div class="jumbotron">
      <p class="lead" th:text="${msg}">您的账号已经激活成功,可以正常使用了!</p>
      <hr class="my-4">
      <p>
        系统会在 <span id="seconds" class="text-danger">8</span> 秒后自动跳转,
        您也可以点此 <a id="target" th:href="@{${target}}" class="text-primary">链接</a>, 手动跳转!
      </p>
    </div>
  </div>
</div>
```

```html
<form class="mt-5" method="post" th:action="@{/register}">
  <input type="text"
         th:class="|form-control | ${UsernameMsg!=null?'is-invaild':''}"
         th:value="${user!=null?user.username:''}"
         id="username" name="username" placeholder="请输入您的账号!" required>
  <div class="invalid-feedback" th:text="${UsernameMsg}">
    该账号已存在!
  </div>
  <input type="password"
         th:class="|form-control | ${PasswordMsg!=null?'is-invaild':''}"
         th:value="${user!=null?user.password:''}"
         id="password" name="password" placeholder="请输入您的密码!" required>
  <div class="invalid-feedback" th:text="${PasswordMsg}">
    密码长度不能小于8位!
  </div>
  <div class="col-sm-10">
    <input type="password" class="form-control"
           th:value="${user!=null?user.password:''}"
           id="confirm-password" placeholder="请再次输入密码!" required>
    <input type="email"
           th:class="|form-control | ${EmailMsg!=null?'is-invaild':''}"
           th:value="${user!=null?user.email:''}"
           id="email" name="email" placeholder="请输入您的邮箱!" required>
    <div class="invalid-feedback" th:text="${EmailMsg}">
      该邮箱已注册!
    </div>
```

## 请求3：激活邮箱

1. 新建工具接口——常量`CommunityConstant`:

```java
package com.nowcoder.community.util;

public interface CommunityConstant {
    
    //激活成功
    int ACTIVATION_SUCCESS=0;
    
    //重复激活
    int ACTIVATION_REPEAT=1;
    
    //激活失败
    int ACTIVATION_FAILURE=2;
}
```

2. 更新UserService：

```java
public class UserService implements CommunityConstant
...

//激活邮件
public int activation(int userId, String code){
  User user = userMapper.selectById(userId);
  if(user.getStatus() == 1){
    return ACTIVATION_REPEAT;
  } else if (user.getActivationCode().equals(code)) {
    return ACTIVATION_SUCCESS;
  }else{
    return ACTIVATION_FAILURE;
  }
}
```

3. 更新LoginController

```java
public class LoginController implements CommunityConstant
...
@RequestMapping(path = "/login", method = RequestMethod.GET)
public String getLoginPage(){
  return "/site/login";
}
//激活路径：https://{domain}/community/activation/{userid}/{activate_code}
@RequestMapping(path = "/activation/{userId}/{code}", method = RequestMethod.GET)
public String activation(Model model, @PathVariable("userId") int userId, @PathVariable("code") String code){
  int result = userService.activation(userId,code);
  if(result == ACTIVATION_SUCCESS){
    model.addAttribute("msg", "激活成功，您的账号已经可以正常使用了！");
    model.addAttribute("target", "/login");
  } else if (result == ACTIVATION_REPEAT) {
    model.addAttribute("msg", "重复激活！");
    model.addAttribute("target", "/index");
  }else{
    model.addAttribute("msg", "激活失败！激活码不正确！");
    model.addAttribute("target", "/index");
  }
  return "/site/operate-result";
}
```

4. 用模板更新login.html，先初步处理更新为thymeleaf模板，并更换固定验证码图片，不用管逻辑

```html
<html lang="en" xmlns:th="https://www.thymeleaf.org">
<link rel="stylesheet" th:href="@{/css/global.css}" />
<link rel="stylesheet" th:href="@{/css/login.css}" />
<header class="bg-dark sticky-top" th:replace="index::header">
...
  <img th:src="@{/img/captcha.png}" style="width:100px;height:40px;" class="mr-2"/>
  ...
<script th:src="@{/js/global.js}"></script>
```

5. 最后，更新index的链接：`<a class="nav-link" th:href="@{/login}">登录</a>`