package com.nowcoder.community.service;

import com.nowcoder.community.dao.AlphaDao;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AlphaService {

    //依赖注入
    @Autowired
    private AlphaDao alphaDao;

    //构造
    public AlphaService(){
        System.out.println("实例化AlphaService");
    }

    //初始化——在构造之后调用
    @PostConstruct
    public void init(){
        System.out.println("初始化AlphaService");
    }

    //销毁之前调用 on_destroy
    @PreDestroy
    public void destroy(){
        System.out.println("销毁AlphaService");
    }

    public String find(){
        return alphaDao.select();
    }
}
