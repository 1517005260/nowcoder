package com.nowcoder.community.util;

public interface CommunityConstant {

    //激活成功
    int ACTIVATION_SUCCESS=0;

    //重复激活
    int ACTIVATION_REPEAT=1;

    //激活失败
    int ACTIVATION_FAILURE=2;

    // 默认登录凭证超时时间
    int DEFAULT_EXPIRED_SECONDS = 3600*12;

    //记住我
    int REMEMBER_EXPIRED_SECONDS = 3600*24*30;

    // 实体类型——帖子1 评论2 用户3
    int ENTITY_TYPE_POST = 1;
    int ENTITY_TYPE_COMMENT = 2;
    int ENTITY_TYPE_USER = 3;

    // topic：评论、点赞、关注
    String TOPIC_COMMENT = "comment";
    String TOPIC_LIKE = "like";
    String TOPIC_FOLLOW = "follow";

    // 发帖
    String TOPIC_PUBLISH = "publish";
    // 删帖
    String TOPIC_DELETE = "delete";
    // 分享
    String TOPIC_SHARE = "share";
    // 新增用户
    String TOPIC_REGISTER = "register";
    // 用户信息更新
    String TOPIC_UPDATE = "update";
    // @用户
    String TOPIC_MENTION = "mention";

    // 系统用户id
    int SYSTEM_USER_ID = 1;

    // 权限
    String AUTHORITY_USER = "user";
    String AUTHORITY_ADMIN = "admin";
    String AUTHORITY_MODERATOR = "moderator";
}
