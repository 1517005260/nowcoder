# 检查登录状态——防止未登录用户直接通过url敲路径访问一些功能

- 使用拦截器
  - 在方法前标注自定义注解
  - 拦截所有请求，只处理带有该注解的方法（加了注解就处理，不加就放行）
- 自定义注解
  - 常用元注解：
  ```
  *@Target  定义写在哪个位置，可以作用在哪些类/方法上
  *@Retention  保留时间/有效时间
  @Document    自定义注解在生成文档时是否带上这个注解
  @Inherited   子类继承用
  ```
  - 如何读取注解（反射）：
    - `Method.getDeclaredAnnotations()  获取方法上的所有注解`
    - `Method.getAnnotation(Class<T> annotationClass)  获取某类型注解`

## 代码实现

1. 新建注解包annotation

2. 新建annotation类型LoginRequired

```java
package com.nowcoder.community.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)  //写在方法上的注解，因为我们拦截的就是方法
@Retention(RetentionPolicy.RUNTIME)   // 程序运行的时候才有效
public @interface LoginRequired {
    //里面什么都不用写，只是起到一个标识的作用，即需要登录
}

```

3. 更新我们的程序，为一些功能加上这个标识——访问设置页面、上传头像、修改密码

```java
@LoginRequired
@RequestMapping(path = "/setting", method = RequestMethod.GET)

@LoginRequired
@RequestMapping(path = "/upload", method = RequestMethod.POST)

@LoginRequired
@RequestMapping(path = "/updatePassword", method = RequestMethod.POST)
// 加了登录拦截器就不需要用户判空操作了
```

获取头像不用，因为未登录也可以访问其他人的头像，其他功能比如首页等也不要登录

4. 拦截器设置

```java
package com.nowcoder.community.controller.interceptor;

import com.nowcoder.community.annotation.LoginRequired;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.util.HostHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;


@Component
public class LoginRequiredInterceptor implements HandlerInterceptor {

    @Autowired
    private HostHolder hostHolder;

    //判断是否拦截
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //能获取到用户就是已登录
        User user = hostHolder.getUser();

        if(handler instanceof HandlerMethod){  //如果handler是HandlerMethod类型，即拦截到的是个方法
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();
            LoginRequired loginRequired = method.getAnnotation(LoginRequired.class);  //取注解

            if(loginRequired != null && user ==null){  //如果被标记（需要登录） 并且 用户为空（未登录）
                response.sendRedirect(request.getContextPath() + "/login");  //强制重定向到登录页
                return false;
            }
        }

        return true;
    }
}
```

<b>层次结构</b>

```
HandlerMethod 包含对特定请求处理方法的引用。  [表]  
  它属于 Spring MVC 的一部分，用于封装处理 HTTP 请求的方法及其相关信息。
Method  属于 Java 反射机制，表示类中的一个方法。  [select * from 表]   但是反射与sql不同的是，反射更动态，而表是预定义的、静态的
  可以通过它获取方法的详细信息，包括注解。
注解 (LoginRequired 等)       [select A from 表]
  定义在方法上，通过 Method 对象获取，用于提供关于方法如何被调用和处理的元信息。
```

配置

```java
@Autowired
private LoginRequiredInterceptor loginRequiredInterceptor;

registry.addInterceptor(loginRequiredInterceptor).
excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg");
```
