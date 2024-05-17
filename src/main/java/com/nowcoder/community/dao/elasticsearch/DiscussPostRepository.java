package com.nowcoder.community.dao.elasticsearch;

import com.nowcoder.community.entity.DiscussPost;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

// @Mapper 是MyBatis专用的注解，@Repository 是Spring 自带的数据库层注解
@Repository
public interface DiscussPostRepository  extends ElasticsearchRepository<DiscussPost,Integer> {  // 泛型声明查询类型和主键类型

}
