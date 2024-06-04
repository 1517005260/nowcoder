package com.nowcoder.community.dao;

import com.nowcoder.community.entity.DiscussPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface DiscussPostMapper {

    //"我的帖子"功能预备，但是首页查询不需要提供uerId，因为是查全体帖子
    //因此需要动态sql，有时需要userId，有时不需要
    //mysql的分页功能很方便，只要改limit参数即可，传入每页的起始行行号offset和最多显示多少条数据limit
    List<DiscussPost> selectDiscussPosts(int userId, int offset, int limit, int orderMode); //orderMode0,最新，1，最热

    // 关注者的帖子
    List<DiscussPost> selectFolloweePosts(int offset, int limit, List<Integer> ids);

    // 全体帖子
    List<DiscussPost> selectAllDiscussPosts();

    // 已删除帖子（status == 2）
    List<DiscussPost> selectDeletedDiscussPosts();

    //页数 = 帖子数 / 每页贴子数
    int selectDiscussPostRows(@Param("userId") int userId);  //@Param可以给参数起别名，使用名字过长的变量
    //另外 **若我想动态拼接sql，并且这个函数有且只有一个参数，就一定需要别名**

    // 发布帖子
    int insertDiscussPost(DiscussPost discussPost);

    //根据id查帖子详情
    DiscussPost selectDiscussPostById(int id);

    //更新评论数
    int updateCommentCount(int id, int commentCount);

    // 帖子操作
    int updateType(int id, int type);
    int updateStatus(int id, int status);
    int updateScore(int id, double score);
    int updateReadCount(int id, int readCount);

    // 更新帖子
    void updatePost(int id, String title, String content, Date time);
}
