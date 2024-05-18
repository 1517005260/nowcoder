# SpringSecurity

- 简介
  - [SpringSecurity](https://spring.io/projects/spring-security)是一个专注于为Java应用程序提供身份认证和授权的框架，它可以轻松扩展以及满足自定义的需求
- 特征
  - 对身份的`认证（登录）`和`授权（是否管理员）`提供全面的、可扩展的支持
  - 防止各种攻击，如会话固定攻击、点击劫持、csrf攻击等
  - 支持与Servlet API、Spring MVC等Web技术的集成

原理如下：

springMVC底层为DispatcherServlet，它和controller的关系是一对多，DispatcherServlet分发给controller的请求可以被interceptor拦截

![springmvc](/imgs/springmvc.png)

以上的内容构成了springMVC的核心

而controller和interceptor是SpringMVC独有的；DispatcherServlet是JAVAEE一个接口的实现，相同的还有Filter过滤器，它与DispatcherServlet的关系就是interceptor和controller的关系

Filter和SpringMVC无关，但是可以对DispatcherServlet拦截

![filter](/imgs/filter.png)

SpringSecurity底层利用了11个Filter进行安全防护，每个Filter只做一件事。security判断的时机非常早，你没有权限，压根访问不了DispatcherServlet，更别说controller了

## 演示（另起一个小项目，否则本项目会受演示的影响）

详见[这里](https://github.com/1517005260/SpringSecurityDemo)