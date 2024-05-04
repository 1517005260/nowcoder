package com.nowcoder.community.controller.advice;

import com.nowcoder.community.util.CommunityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;
import java.io.PrintWriter;


@ControllerAdvice(annotations = Controller.class)  // 仅针对带有Controller注解的bean
public class ExceptionAdvice {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionAdvice.class);

    @ExceptionHandler({Exception.class})   // Exception是所有异常的父类，这里即表示处理所有异常
    public void handleException(Exception e, HttpServletResponse response, HttpServletRequest request) throws IOException {
        logger.error("服务器发送异常：" + e.getMessage());  // 简略记下错误
        for(StackTraceElement element : e.getStackTrace()){
            logger.error(element.toString());   // 详细记下每一条错误信息
        }

        // 判断：普通请求返回500.html，异步请求返回json/xml等数据
        String xRequestedWith = request.getHeader("x-requested-with");
        if("XMLHttpRequest".equals(xRequestedWith)){
            // 是异步请求
            response.setContentType("application/plain;charset=utf-8");  // 返回普通字符串（但我们知道这是json），由前端 $.parseJSON手动转成json格式
            PrintWriter pw = response.getWriter();
            pw.write(CommunityUtil.getJSONString(500, "服务器异常！"));
        }else {
            response.sendRedirect(request.getContextPath() + "/error");  // 重定向到 HomeController的 /error
        }
    }
}
