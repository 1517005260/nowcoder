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

    //增加评论，返回行数
    int insertComment(Comment comment);

    // 根据id查评论
    Comment selectCommentById(int id);

    // 根据用户查询评论
    List<Comment> selectCommentsByUser(int userId, int offset, int limit);

    // 查询用户评论数量
    int selectCountByUser(int userId);
}
