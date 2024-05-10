package com.nowcoder.community.util;

// 简单小工具，不需要容器托管
public class RedisKeyUtil {
    private static final String SPLIT = ":"; //分隔符

    // 把帖子和评论统称为实体
    private static final String PREFIX_ENTITY_LIKE = "like:entity";

    private static final String PREFIX_USER_LIKE = "like:user";

    // 某个实体的赞
    // like:entity:entityType:entityId -> set(userId) （方便统计谁赞了、赞的数量等）
    public static String getEntityLikeKey(int entityType, int entityId){
        return PREFIX_ENTITY_LIKE + SPLIT + entityType + SPLIT +entityId;
    }

    // 某个用户收到的赞
    // like:user:userId -> int
    public static String getUserLikeKey(int userId){
        return PREFIX_USER_LIKE + SPLIT + userId;
    }
}
