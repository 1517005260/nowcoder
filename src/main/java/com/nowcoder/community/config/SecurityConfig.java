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
                        "/user/updateUsername", // 修改名字
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
                .requestMatchers(
                        "/discuss/top",
                        "/discuss/wonderful"
                ).hasAnyAuthority(
                        AUTHORITY_MODERATOR
                )
                .requestMatchers(
                        "/discuss/delete",
                        "/data/**",
                        "/user/updatetype"
                ).hasAnyAuthority(
                        AUTHORITY_ADMIN
                )
                .anyRequest().permitAll()   // 其他任何请求都放行
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