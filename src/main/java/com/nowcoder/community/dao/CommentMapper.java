package com.nowcoder.community.dao;

import com.nowcoder.community.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommentMapper {

    //分页查询评论
    List<Comment> selectCommentsByEntity(int entityType, int entityId, int offset, int limit);

    //查询评论条目数
    int selectCountByEntity(int entityType, int entityId);
}
