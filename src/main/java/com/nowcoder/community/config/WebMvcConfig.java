package com.nowcoder.community.config;


import com.nowcoder.community.controller.interceptor.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig  implements WebMvcConfigurer {
    @Autowired
    private AlphaInterceptor alphaInterceptor;

    @Autowired
    private LoginTicketInterceptor loginTicketInterceptor;

//    @Autowired
//    private LoginRequiredInterceptor loginRequiredInterceptor;

    @Autowired
    private MessageInterceptor messageInterceptor;

    @Autowired
    private DataInterceptor dataInterceptor;

    @Value("${community.path.editormdUploadPath}")
    private String editormdUploadPath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //注册拦截器以及配置它们的拦截路径和顺序
        registry.addInterceptor(alphaInterceptor).
                excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg")   //排除拦截路径，一般是静态资源
                .addPathPatterns("/register", "/login");  //明确添加拦截路径

        registry.addInterceptor(loginTicketInterceptor).
                excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg", "/editor-md/**");

        registry.addInterceptor(messageInterceptor).
                excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg", "/editor-md/**");

        registry.addInterceptor(dataInterceptor).
                excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg", "/editor-md/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将上传路径映射到虚拟路径
        registry.addResourceHandler("/upload/**").addResourceLocations("file:" + editormdUploadPath + "/");
    }
}
