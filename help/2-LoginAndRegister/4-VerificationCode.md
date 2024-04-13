# 生成验证码

- [Kaptcha](https://code.google.com/archive/p/kaptcha)
  - 导入jar包
  - 编写Kaptcha配置类
  - 生成随机字符、生成图片

## 代码实现

1. 导包
```xml
<dependency>
    <groupId>com.github.penggle</groupId>
    <artifactId>kaptcha</artifactId>
    <version>2.3.2</version>
</dependency>
```

2. 新建配置类KaptchaConfig（因为Spring没有集成这个小工具）

```java
package com.nowcoder.community.config;

import com.google.code.kaptcha.Producer;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class KaptchaConfig {

    //这样就会被装配到容器里了
    @Bean
    public Producer KaptchaConfig(){
        Properties properties = new Properties();
        properties.setProperty("kaptcha.image.width", "100");  //100px
        properties.setProperty("kaptcha.image.height", "40");
        properties.setProperty("kaptcha.textproducer.font.size", "32");
        properties.setProperty("kaptcha.textproducer.font.color", "black");
        properties.setProperty("kaptcha.textproducer.char.string", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYAZ");  //随机字符的范围
        properties.setProperty("kaptcha.textproducer.char.length", "4");  // 验证码4位
        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.NoNoise");  //噪声类，即验证码样式

        DefaultKaptcha kaptcha = new DefaultKaptcha();
        Config config = new Config(properties);
        kaptcha.setConfig(config);
        return kaptcha;
    }
}
```

3. 访问流程：登录页面 -> 验证码 -> 向服务器请求图片路径

即：当我们点击login服务时，：

```java
@RequestMapping(path = "/login", method = RequestMethod.GET)
    public String getLoginPage(){
        return "/site/login";
    }
```

浏览器接收了一个html页面，此时它发现有验证码服务，于是再次向服务器请求图片

因此，需要另写一个请求响应（由于返回的是图片，既不是String也不是常规响应，于是用void手写），在loginController中：

```java
@Autowired
private Producer kaptchaProducer;

private static final Logger logger =  LoggerFactory.getLogger(LoginController.class);

//验证码图片
@RequestMapping(path = "/kaptcha", method = RequestMethod.GET)
public void getKaptcha(HttpServletResponse response, HttpSession session){  // 由于验证码涉及安全问题，故用session
  //生成验证码  -> 存入session 
  String text = kaptchaProducer.createText();
  //生成验证码图片  -> 输出给浏览器
  BufferedImage image = kaptchaProducer.createImage(text);

  session.setAttribute("kaptcha", text);
  response.setContentType("image/png");  //声明格式：png
  try {
    OutputStream os = response.getOutputStream();
    ImageIO.write(image, "png", os);
  } catch (IOException e) {
    logger.error("响应验证码失败：" + e.getMessage());
  }
}
```

4. 将验证码用于登录页面

```html
<img th:src="@{/kaptcha}" id="kaptcha" style="width:100px;height:40px;" class="mr-2"/>
<a href="javascript:refresh_kaptcha();" class="font-size-12 align-bottom">刷新验证码</a>

<script>
  function refresh_kaptcha(){
    let path = CONTEXT_PATH + "/kaptcha?p=" + Math.random();  //加上随机参数，防止浏览器认为路径一样就不刷新了
    $("#kaptcha").attr("src", path);
  }
</script>
```

并为`global.js`增加全局变量，即不要在单个页面中写死路径：`let CONTEXT_PATH = "/community";`