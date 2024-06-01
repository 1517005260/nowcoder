package com.nowcoder.community.util;

// 简单小工具，不需要容器托管
public class RedisKeyUtil {
    private static final String SPLIT = ":"; //分隔符

    // 把帖子和评论统称为实体
    private static final String PREFIX_ENTITY_LIKE = "like:entity";

    private static final String PREFIX_USER_LIKE = "like:user";
    private static final String PREFIX_LIKE_POST = "like:post";

    private static final String PREFIX_FOLLOWEE = "followee";
    private static final String PREFIX_FOLLOWER = "follower";
    private static final String PREFIX_KAPTCHA = "kaptcha";
    private static final String PREFIX_TICKET = "ticket";
    private static final String PREFIX_USER = "user";
    private static final String PREFIX_UV = "uv";
    private static final String PREFIX_DAU = "dau";
    private static final String PREFIX_POST = "post";
    private static final String PREFIX_POST_READ = "post:read";
    private static final String PREFIX_POST_UNREAD = "post:unread";

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

    // 某个用户赞了的帖子
    // like:post:userid -> Zset(postId, likeTime)
    public static String getUserPostKey(int userId){
        return PREFIX_LIKE_POST + SPLIT + userId;
    }

    // 关注：双份数据
    // userId关注了followee  user -> entity
    // followee:userId:entityType -> Zset(entityId, follow_time)
    public static String getFolloweeKey(int userId, int entityType){
        return PREFIX_FOLLOWEE + SPLIT + userId + SPLIT + entityType;
    }

    // 某个实体拥有的follower     user -> entity
    // follower:entityType:entityId -> Zset(userId, follow_time)
    public static String getFollowerKey(int entityType, int entityId){
        return PREFIX_FOLLOWER + SPLIT + entityType + SPLIT + entityId;
    }

    // 验证码，由于用户未登录，我们用cookie里的随机字符串区分
    public static String getKaptchaKey(String owner){
        return PREFIX_KAPTCHA + SPLIT + owner;
    }

    // 登录凭证
    public static String getTicketKey(String ticket){
        return PREFIX_TICKET + SPLIT + ticket;
    }

    // 用户信息
    public static String getUserKey(int userId){
        return PREFIX_USER + SPLIT + userId;
    }

    // 单日uv
    public static String getUVKey(String date){
        return PREFIX_UV + SPLIT + date;
    }

    // 区间uv
    public static String getUVKey(String startDate, String endDate){
        return PREFIX_UV + SPLIT + startDate + SPLIT + endDate;
    }

    // 单日dau
    public static String getDAUKey(String date){
        return PREFIX_DAU + SPLIT + date;
    }

    // 区间dau
    public static String getDAUKey(String startDate, String endDate){
        return PREFIX_DAU + SPLIT + startDate + SPLIT + endDate;
    }

    // 统计帖子分数：存产生变化的帖子，不需要传参
    public static String getPostScoreKey(){
        return PREFIX_POST + SPLIT + "score";
    }

    // 统计帖子阅读量
    // post:read:postId -> int
    public static String getPostReadKey(int postId){
        return PREFIX_POST_READ + SPLIT + postId;
    }

    // 用户关注未读
    // post:unread:userId -> Zset(postId, create_time)
    public static String getFolloweePostUnreadKey(int userId) {
        return PREFIX_POST_UNREAD + SPLIT + userId;
    }
}
