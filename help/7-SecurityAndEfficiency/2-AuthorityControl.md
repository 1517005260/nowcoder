# 权限控制

- 登录检查
  - 之前的写过的简单的拦截器登录检查，现在将其废弃
- 授权配置
  - 对当前系统内包含的所有请求，为其分配访问权限（普通用户、版主、管理员）
- 认证方案
  - 不用Security的方案而是采用自己的
- CSRF相关配置
  - 防止CSRF攻击的基本原理，以及表单、AJAX的相关配置

## 代码实现

1. 导包

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

2. 拦截器配置废弃，即给LoginRequiredInterceptor加上注释

在WebMvcConfig中：

```java
//    @Autowired
//    private LoginRequiredInterceptor loginRequiredInterceptor;

//        registry.addInterceptor(loginRequiredInterceptor).
//                excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg");
```

3. 授权配置——写Security配置类

a. 增加常量——权限字符串

```java
// 权限
String AUTHORITY_USER = "user";
String AUTHORITY_ADMIN = "admin";
String AUTHORITY_MODERATOR = "moderator";
```

b. 新建配置类SecurityConfig

```java
package com.nowcoder.community.config;

import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

// 新版写法
@Configuration
public class SecurityConfig implements CommunityConstant {

    // 忽略静态资源的访问
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        // Lambda 表达式， 输入 web（WebSecurity对象） 返回 web.ignoring().requestMatchers("/resources/**")
        return web -> web.ignoring().requestMatchers("/resources/**");
    }

    // 授权
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 授权请求
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
                ).anyRequest().permitAll()   // 其他任何请求都放行
        );

        // 权限不够时的处理：1）普通请求——跳转html页面 2）异步请求——返回json
        http.exceptionHandling(handle -> handle.authenticationEntryPoint(  // 没有登录时的处理
                (request, response, authException) -> {
                    String xRequestedWith = request.getHeader("x-requested-with");  // 判断异步请求——看消息头
                    if ("XMLHttpRequest".equals(xRequestedWith)) {
                        // 如果是异步请求，给浏览器弹窗提示
                        response.setContentType("application/plain;charset=utf-8");
                        response.getWriter().write(CommunityUtil.getJSONString(403, "你还没有登录哦，请登录后再尝试！"));
                    } else {
                        // 同步请求重定向到登录页即可
                        response.sendRedirect(request.getContextPath() + "/login");
                    }
                }
        ).accessDeniedHandler(
                (request, response, accessDeniedException) -> {  // 没有权限时的处理
                    String xRequestedWith = request.getHeader("x-requested-with");
                    if ("XMLHttpRequest".equals(xRequestedWith)) {
                        response.setContentType("application/plain;charset=utf-8");
                        response.getWriter().write(CommunityUtil.getJSONString(403, "权限不足！"));
                    } else {
                        response.sendRedirect(request.getContextPath() + "/denied");
                    }
                }
        ));

        // Security会自动拦截/logout请求，进行退出处理
        // 由于底层是Filter，执行在我们写的Controller之前，所以如果不做处理，我们的/logout处理就无效了
        // 覆盖它默认的逻辑，才能执行我们自己的退出代码
        http.logout(logout -> logout.logoutUrl("/securitylogout")); // 随便让他拦截一个项目中没有的路径，这样我们的/logout就逃过了Security的监管
        // 默认开启防止CSRF攻击，如果要部分关闭，用下面的配置
        // http.csrf(httpSecurityCsrfConfigurer -> httpSecurityCsrfConfigurer.ignoringRequestMatchers("/actuator/**"));
        return http.build();
    }

    // 下面两个函数指定了：
    // 把用户的安全信息（用户是谁，权限）存储在用Session中
    // 每次用户请求到来时，Security会从会话中取出这些信息，进行安全验证
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // 用户登出时，确保清除所有的安全信息，使得用户完全退出登录状态
    // 清空用户的会话，删除会话中的安全信息
    @Bean
    public SecurityContextLogoutHandler securityContextLogoutHandler() {
        return new SecurityContextLogoutHandler();
    }
}
```

c. 为了适配SecurityConfig，在HomeController中新增：

```java
// 权限不足页面
@RequestMapping(path = "/denied", method = RequestMethod.GET)
public String getDeniedPage() {
    return "/error/404";
}
```

在UserService新增：

```java
// 获取用户权限
public Collection<? extends GrantedAuthority> getAuthorities(int userId){
    User user = this.findUserById(userId);

    List<GrantedAuthority> list = new ArrayList<>();
    list.add(new GrantedAuthority() {
        @Override
        public String getAuthority() {
            switch (user.getType()){
                case 1:
                    return AUTHORITY_ADMIN;
                case 2:
                    return AUTHORITY_MODERATOR;
                default:
                    return AUTHORITY_USER;
            }
        }
    });

    return list;
}
```

并重构LoginTicketInterceptor

```java
@Autowired
private SecurityContextRepository securityContextRepository;

if(loginTicket != null && loginTicket.getStatus() == 0 &&
        loginTicket.getExpired().after(new Date())){    //凭证为空，且有效，且过期时间晚于当前时间
    User user = userService.findUserById(loginTicket.getUserId());

    //在本次请求（线程）中持久化用户（持有用户信息）
    //由于浏览器和服务器是多对一的多线程并发关系，所以要保证每个线程独立不受影响
    //所以存入ThreadLocal里
    hostHolder.setUser(user);

    // 重构，配合SpringSecurity，类似hostHolder一样，把用户的权限通过ContextHolder存入SecurityContext，便于Security进行授权
    Authentication authentication = new UsernamePasswordAuthenticationToken(
            user, user.getPassword(), userService.getAuthorities(user.getId())
    );// 构造凭证
    SecurityContextHolder.setContext(new SecurityContextImpl(authentication));  // 传入Context
    // 将SecurityContext存入SecurityContextRepository中  相当于存入数据库
    securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
}

@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
  hostHolder.clear(); // 清理登录信息
  // 这里不需要清理授权信息，在logoutController中处理即可
}
```

在LoginController中处理退出：

```java
@RequestMapping(path = "/logout", method = RequestMethod.GET)
public String logout(@CookieValue("ticket") String ticket, HttpServletRequest request,
                     HttpServletResponse response, Authentication authentication) {
  userService.logout(ticket);
  // 重构，使用Spring Security
  // 使用SecurityContextLogoutHandler清理SecurityContext
  securityContextLogoutHandler.logout(request, response, authentication);
  // 退出登录，返回重定向页面到登录页面
  // 因为login有两个请求，一个是GET请求，一个是POST请求
  // 这里要重定向默认到/login的GET请求
  return "redirect:/login";
}
```

4. CSRF配置

什么是CSRF：用户浏览器中保存了cookie和ticket，像服务器提交表单form时，服务器会给你返回一个表单页面。但此时若用户停止填写，访问了另一个不安全的网站（带病毒），该网站通过病毒窃取了用户携带的cookie和ticket，此时该网站可以模仿用户向服务器提交表单，这是不安全的

![csrf](/imgs/csrf.png)

Security解决方案：在返回给用户表单的时候附带上一个随机token，不安全的网站不能猜到这个token，当它提交时服务器发现token对不上，身份验证就有问题了

![csrf_token](/imgs/csrftoken.png)

但是，这种方式无法应对异步请求，因为异步的时候可能连表单都没有，此时需要手动处理

因此，针对项目中的所有异步请求，作如下增加：

在index.html中新增：

```html
<!--访问该页面时，强制令Security在此处生产csrf令牌，之后异步请求的时候从此处取即可-->
<meta name="_csrf" th:content="${_csrf.token}">
<meta name="_csrf_header" th:content="${_csrf.headerName}">
```

在index.js中改变异步逻辑：

```javascript
// 发送AJAX前，带上csrf令牌
let token = $("meta[name= '_csrf']").attr("content");
let header = $("meta[name= '_csrf_header']").attr("content");
$(document).ajaxSend(function (e, xhr, options){
    xhr.setRequestHeader(header, token);
});
```

其他异步处理类似