# 账号设置

- 上传文件（本次课——用户上传的头像就存在本机服务器）
  - 必须是POST请求
  - 表单：`enctype = "multipart/form-data"`（传文件必须）
  - Spring MVC: 通过MultipartFile处理上传文件
- 开发步骤
  - 访问账号设置页面
  - 上传头像
  - 获取头像

## 代码实现

1. 新建用户控制类UserController

```java
package com.nowcoder.community.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(path = "/user")
public class UserController {
    
    @RequestMapping(path = "/setting", method = RequestMethod.GET)
    public String getSettingPage(){
        return "/site/setting";
    }
}
```

2. 配置模板site/setting.html:

```html
<html lang="en" xmlns:th="https://www.thymeleaf.org">
<link rel="stylesheet" th:href="@{/css/global.css}" />
<link rel="stylesheet" th:href="@{/css/login.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

<script th:src="@{/js/global.js}"></script>
```

在index.html中创建超链接：

```html
<a class="dropdown-item text-center" th:href="@{/user/setting}">账号设置</a>
```

3. 上传文件（头像） + 获取文件

a. 配置 文件存放路径 :`community.path.upload=c:/Users/15170/Desktop/community/data`

b. dao——无需变动

c. service——更新头像url路径：

在UserService中追加：

```java
public int updateHeader(int userId, String headerUrl){
        return userMapper.updateHeader(userId, headerUrl);  //返回修改的行数
    }
```

d. UserController更新为：

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.HostHolder;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

@Controller
@RequestMapping(path = "/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Value("${community.path.upload}")
    private String uploadPath;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @RequestMapping(path = "/setting", method = RequestMethod.GET)
    public String getSettingPage(){
        return "/site/setting";
    }

    @RequestMapping(path = "/upload", method = RequestMethod.POST)
    public String uploadHeader(MultipartFile headerImage, Model model){
        if(headerImage == null){
            model.addAttribute("error", "上传的头像图片为空！");
            return "/site/setting";
        }

        //给上传的文件重命名
        String fileName = headerImage.getOriginalFilename();
        String suffix = fileName.substring(fileName.lastIndexOf("."));  //从“.”之后开始截取，即截取格式
        if(StringUtils.isBlank(suffix)){
            model.addAttribute("error", "文件格式错误！");
            return "/site/setting";
        }
        fileName = CommunityUtil.genUUID() + suffix;

        //存储文件
        File dist = new File(uploadPath + "/" + fileName); //存放路径
        try {
            headerImage.transferTo(dist);
        } catch (IOException e) {
            logger.error("上传文件失败：" + e.getMessage());
            throw new RuntimeException("上传文件失败，服务器发生异常！", e);
        }

        //更新用户头像（非服务器，而是web路径）
        //http://...../community/user/header/xxx.png
        User user = hostHolder.getUser();
        String headerUrl = domain + contextPath + "/user/header/" + fileName;
        userService.updateHeader(user.getId(), headerUrl);

        return "redirect:/index";
    }

    @RequestMapping(path = "/header/{fileName}", method = RequestMethod.GET)
    public void getHeader(@PathVariable("fileName") String fileName, HttpServletResponse response){
        // 服务器存放路径
        fileName = uploadPath + "/" + fileName;
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        
        //响应文件
        response.setContentType("image/" + suffix);
        try (   // 使用括号语法就不用finally删除了，用完后会自动删除对象
                OutputStream os = response.getOutputStream();
                FileInputStream fis = new FileInputStream(fileName);
                ){
            byte[] buffer = new byte[1024];
            int b = 0;
            while( (b = fis.read(buffer)) != -1){
                os.write(buffer, 0, b);
            }
        } catch (IOException e) {
            logger.error("读取头像失败：" + e.getMessage());
        }
    }
}
```

e. 处理前端setting.html:

```html
<form class="mt-5" method="post" enctype="multipart/form-data" th:action="@{/user/upload}">
  <input type="file"
         th:class="|custom-file-input ${error!=null}?'is-invaild':''|"
         id="head-image" name="headerImage" lang="es" required="">
  <label class="custom-file-label" for="head-image" data-browse="文件">选择一张图片</label>
  <div class="invalid-feedback" th:text="${error}">
    该账号不存在!
  </div>
```