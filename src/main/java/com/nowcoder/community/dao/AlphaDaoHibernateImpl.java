package com.nowcoder.community.dao;

import org.springframework.stereotype.Repository;
//数据库注解
@Repository("alphaHibernate")
public class AlphaDaoHibernateImpl implements AlphaDao{
    @Override
    public String select() {
        return "Hibernate";
    }
}
