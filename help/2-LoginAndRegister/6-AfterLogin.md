# 显示登录信息——即登录之后的页面变化

- 拦截器示例（拦截浏览器过来的请求，插入代码，从而批量处理共有业务）
  - 定义拦截器，实现HandlerInterceptor
  - 配置拦截器，为它指定拦截、排除的路径
- 拦截器应用
  - 在请求开始时查询登录用户
  - 在本次请求中持有用户数据
  - 在模板视图上显示用户数据
  - 在请求结束时清理用户数据

## 拦截器示例

1. 定义接口，实现方法即可

位于controller下，新建`包Interceptor`，新建示例类AlphaInterceptor

```java
package com.nowcoder.community.controller.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class AlphaInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(AlphaInterceptor.class);
    
    //实现接口定义的三个方法
    
    //在controller之前执行
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.debug("preHandle: " + handler.toString());
        return true;  //false则拦截该请求，true放行
    }

    
    //在controller之后执行，在模板引擎之前执行
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        logger.debug("postHandle: " + handler.toString());
    }
    
    
    //在模板引擎结束之后执行
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        logger.debug("afterCompletion: " + handler.toString());
    }
}
```

2. 配置，在config下新建配置类WebMvcConfig

```java
package com.nowcoder.community.config;


import com.nowcoder.community.controller.interceptor.AlphaInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig  implements WebMvcConfigurer {
    @Autowired
    private AlphaInterceptor alphaInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //注册拦截器以及配置它们的拦截路径和顺序
        registry.addInterceptor(alphaInterceptor).
                excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg")   //排除拦截路径，一般是静态资源
                .addPathPatterns("/register", "/login");  //明确添加拦截路径
    }
}
```

3. 运行项目，点击”注册“， 可以发现控制台输出：

```bash
2024-04-14 12:17:48,667 DEBUG [http-nio-8080-exec-5] c.n.c.c.i.AlphaInterceptor [AlphaInterceptor.java:21] preHandle: com.nowcoder.community.controller.LoginController#getRegisterPage()
2024-04-14 12:17:48,667 DEBUG [http-nio-8080-exec-5] c.n.c.c.i.AlphaInterceptor [AlphaInterceptor.java:29] postHandle: com.nowcoder.community.controller.LoginController#getRegisterPage()
2024-04-14 12:17:48,672 WARN [http-nio-8080-exec-5] o.t.s.p.AbstractStandardFragmentInsertionTagProcessor [AbstractStandardFragmentInsertionTagProcessor.java:385] [THYMELEAF][http-nio-8080-exec-5][/site/register] Deprecated unwrapped fragment expression "index::header" found in template /site/register, line 15, col 38. Please use the complete syntax of fragment expressions instead ("~{index::header}"). The old, unwrapped syntax for fragment expressions will be removed in future versions of Thymeleaf.
2024-04-14 12:17:48,691 DEBUG [http-nio-8080-exec-5] c.n.c.c.i.AlphaInterceptor [AlphaInterceptor.java:36] afterCompletion: com.nowcoder.community.controller.LoginController#getRegisterPage()
```

发现确实是 preHandle - postHandle - template - afterCompletion 的顺序

## 拦截器应用



![图解拦截器](/imgs/handler.png)


0. 为了方便获取cookie，新建工具类CookieUtil：

```java
package com.nowcoder.community.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public class CookieUtil {
    
    public static String getValue(HttpServletRequest request, String name){
        if(request == null || name == null){
            throw new IllegalArgumentException("参数为空！");
        }

        Cookie[] cookies = request.getCookies();
        if(cookies != null){
            for(Cookie cookie : cookies){
                if(cookie.getName().equals(name)){
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }
}
```

并追加用ticket查询于UserService:

```java
    public LoginTicket findLoginTicket(String ticket){
        return loginTicketMapper.selectByTicket(ticket);
    }
```

以及线程独立的小工具HostHolder：

```java
package com.nowcoder.community.util;

import com.nowcoder.community.entity.User;
import org.springframework.stereotype.Component;

// 持有用户的信息，用于代替session对象
@Component
public class HostHolder {
    private ThreadLocal<User> users = new ThreadLocal<>();

    public void setUser(User user) {
        users.set(user);
    }
    
    public User getUser(){
        return users.get();
    }
    
    public void clear(){
        users.remove();
    }
}
```

1. 新建拦截器LoginTicketInterceptor:

```java
package com.nowcoder.community.controller.interceptor;


import com.nowcoder.community.entity.LoginTicket;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CookieUtil;
import com.nowcoder.community.util.HostHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Date;

@Component
public class LoginTicketInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从cookie中获取凭证
        String ticket = CookieUtil.getValue(request, "ticket");

        if(ticket !=null){
            //已经登录，查询凭证找用户
            LoginTicket loginTicket = userService.findLoginTicket(ticket);

            if(loginTicket != null && loginTicket.getStatus() == 0 &&
                    loginTicket.getExpired().after(new Date())){    //凭证为空，且有效，且过期时间晚于当前时间
                User user = userService.findUserById(loginTicket.getUserId());

                //在本次请求（线程）中持久化用户（持有用户信息）
                //由于浏览器和服务器是多对一的多线程并发关系，所以要保证每个线程独立不受影响
                //所以存入ThreadLocal里
                hostHolder.setUser(user);
            }
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        User user = hostHolder.getUser();  //得到当前线程中持有的user

        if(user != null && modelAndView != null){
            modelAndView.addObject("loginUser", user);
        }
        //之后就会传给模板引擎，然后传给前端
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        hostHolder.clear(); //清理
    }
}
```

2. 配置，在之前的配置中追加即可

```java
@Autowired
private LoginTicketInterceptor loginTicketInterceptor;

registry.addInterceptor(loginTicketInterceptor).
                excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg");
```

3. 模板引擎代码, 由于所有页面都是复用index的，所以只要改动index即可

```html
<li class="nav-item ml-3 btn-group-vertical">
  <a class="nav-link" th:href="@{/index}">首页</a>
</li>
<li class="nav-item ml-3 btn-group-vertical" th:if="${loginUser!=null}">
  <a class="nav-link position-relative" href="site/letter.html">消息<span class="badge badge-danger">12</span></a>
</li>
<li class="nav-item ml-3 btn-group-vertical" th:if="${loginUser==null}">
  <a class="nav-link" th:href="@{/register}">注册</a>
</li>
<li class="nav-item ml-3 btn-group-vertical" th:if="${loginUser==null}">
  <a class="nav-link" th:href="@{/login}">登录</a>
</li>
<li class="nav-item ml-3 btn-group-vertical dropdown" th:if="${loginUser!=null}">
  <a class="nav-link dropdown-toggle" href="#" id="navbarDropdown" role="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
    <img th:src="${loginUser.headerUrl}" class="rounded-circle" style="width:30px;"/>
  </a>
  <div class="dropdown-menu" aria-labelledby="navbarDropdown">
    <a class="dropdown-item text-center" href="site/profile.html">个人主页</a>
    <a class="dropdown-item text-center" href="site/setting.html">账号设置</a>
    <a class="dropdown-item text-center" th:href="@{/logout}">退出登录</a>
    <div class="dropdown-divider"></div>
    <span class="dropdown-item text-center text-secondary" th:utext="${loginUser.username}">nowcoder</span>
  </div>
</li>
```

## 补充：ThreadLocal
ThreadLocal是一个Java类，用于创建线程局部变量。简单地说，它可以让你在每个线程中都有一份独立的变量副本，而这些副本互不干扰。这对于并发编程非常有用，因为它避免了线程之间共享变量时可能出现的线程安全问题。

相比传统数组，这个ThreadLocal提供的元素是”一块独立的空间“，因此需要及时清理

### 通俗理解 ThreadLocal
想象你在一个大办公室工作，而这个办公室有很多员工（线程），他们都需要一些工具（变量）来完成工作。如果办公室中只有一套工具供所有人共享，那么可能会发生混乱，比如一个人正在使用一把剪刀，另一个人也需要使用。这种情况下，要么等待，要么两人尝试同时使用导致问题（线程安全问题）。

使用 ThreadLocal 就好比每个员工（线程）都有自己的一套工具（变量副本）。他们可以独立使用，不需要等待别人，也不会影响别人。这样每个人都可以更安全、更高效地工作。

### 使用场景
1. 用户会话信息：在Web应用中，每个用户的会话信息都是独立的，使用 ThreadLocal 可以保证每个线程中保存的是当前用户的会话信息，而不会混淆。
2. 数据库连接：每个线程可能有自己独立的数据库连接，使用 ThreadLocal 可以确保连接不会被其他线程错误使用。