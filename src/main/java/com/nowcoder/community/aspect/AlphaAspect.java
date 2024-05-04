package com.nowcoder.community.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

//@Component
//@Aspect
public class AlphaAspect {

    // 空方法即可，在注解里声明where
    @Pointcut("execution (* com.nowcoder.community.service.*.*(..))")
    // 处理：        接受所有的返回值      service下所有类 的所有方法 的所有的参数
    public void pointcut(){}

    // 注解声明when

    @Before("pointcut()")  //在连接点开始时织入
    public void before(){
        System.out.println("before");
    }

    @After("pointcut()")   //在连接点之后
    public void after(){
        System.out.println("after");
    }

    @AfterReturning("pointcut()")  //在返回值以后
    public void afterReturning(){
        System.out.println("afterReturning");
    }

    @AfterThrowing("pointcut()")  // 在抛异常之后
    public void afterThrowing(){
        System.out.println("afterThrowing");
    }

    @Around("pointcut()")   // 既在前面，也在之后织入
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{   //参数为连接点
        System.out.println("around before");
        Object obj = joinPoint.proceed();   // 调用目标组件的方法
        System.out.println("around after");
        return  obj;
    }
}
