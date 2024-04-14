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
