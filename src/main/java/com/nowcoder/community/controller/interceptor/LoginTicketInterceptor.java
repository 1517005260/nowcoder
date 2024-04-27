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