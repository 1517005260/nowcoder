package com.nowcoder.community.dao;

import com.nowcoder.community.entity.Message;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MessageMapper {
    // 查询当前用户的私信列表，每个对话显示界面仅返回一条最新的消息
    List<Message> selectConversations(int userId, int offset, int limit);

    // 查询当前用户的会话数量
    int selectConversationCount(int userId);

    // 私信详情：查询某个会话所包含的所有消息
    List<Message> selectLetters(String conversationId, int offset, int limit);

    // 查询某个会话所包含的消息数量
    int selectLetterCount(String conversationId);

    // 查询用户未读消息数量（列表页和详情页共用一个查询，需要动态拼接）
    int selectLetterUnreadCount(int userId, String conversationId);
}