package com.nowcoder.community.service;

import com.nowcoder.community.dao.MessageMapper;
import com.nowcoder.community.entity.Message;
import com.nowcoder.community.util.SensitiveFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.Arrays;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private SensitiveFilter sensitiveFilter;

    public List<Message> findConversations(int userId, int offset, int limit){
        return  messageMapper.selectConversations(userId, offset, limit);
    }

    public int findConversationCount(int userId){
        return messageMapper.selectConversationCount(userId);
    }

    public List<Message> findLetters(int userId, String conversationId, int offset, int limit){
        return messageMapper.selectLetters(userId, conversationId, offset, limit);
    }

    public int findLetterCount(int userId, String conversationId){
        return messageMapper.selectLetterCount(userId,conversationId);
    }

    public int findLetterUnreadCount(int userId, String conversationId){
        return messageMapper.selectLetterUnreadCount(userId, conversationId);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public void addMessage(Message message) {
        // 处理消息内容
        message.setContent(HtmlUtils.htmlEscape(message.getContent()));
        message.setContent(sensitiveFilter.filter(message.getContent()));

        // 插入消息
        messageMapper.insertMessage(message);

        // 插入发送者的消息状态
        messageMapper.insertUserMessageStatus(message.getFromId(), message.getId(), message.getStatus());
        // 插入接收者的消息状态
        messageMapper.insertUserMessageStatus(message.getToId(), message.getId(), message.getStatus());
    }

    public int readMessage(List<Integer> ids){
        return messageMapper.updateStatus(ids, 1);
    }

    // 删除通知
    // 由于mappper接收的是数组，而删除消息点x删除的只是一条，所以需要转换
    public int deleteNotice(int id){
        return  messageMapper.updateStatus(Arrays.asList(new Integer[]{id}), 2);
    }

    @Transactional
    public int deleteMessage(int userId, int messageId) {
        return messageMapper.deleteMessage(userId, Arrays.asList(new Integer[]{messageId}), 2);
    }

    public Message findLatestNotice(int userId, String topic){
        return messageMapper.selectLatestNotice(userId, topic);
    }

    public int findNoticeCount(int userId, String topic){
        return messageMapper.selectNoticeCount(userId, topic);
    }

    public int findNoticeUnreadCount(int userId, String topic){
        return messageMapper.selectNoticeUnreadCount(userId, topic);
    }

    public List<Message> findNotices(int userId, String topic, int offset, int limit){
        return messageMapper.selectNotice(userId, topic, offset, limit);
    }
}
