package com.nowcoder.community.event;

import com.alibaba.fastjson.JSONObject;
import com.nowcoder.community.entity.Event;
import com.nowcoder.community.entity.Message;
import com.nowcoder.community.service.MessageService;
import com.nowcoder.community.util.CommunityConstant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class EventConsumer implements CommunityConstant {
    // 由于消费者要负责处理，所以需要记日志
    private static final Logger logger = LoggerFactory.getLogger(EventConsumer.class);

    @Autowired
    private MessageService messageService;

    // 由于三个消息通知格式类似，所以写一个方法就行
    @KafkaListener(topics = {TOPIC_COMMENT, TOPIC_LIKE, TOPIC_FOLLOW})
    public void handleMessage(ConsumerRecord record){
        if(record == null || record.value() == null){
            logger.error("消息的内容为空！");
        }

        // 把生产者传进来的json还原成Event
        Event event = JSONObject.parseObject(record.value().toString(), Event.class);
        if(event == null){
            logger.error("消息格式错误！");
        }

        // 发送消息，即构造一个message，存在Message表里
        // 与之前用户间的私信不一样，这次的是系统通知，from_id = 1，此时conversation_id换成存topic,content存json字符串，包含了页面上拼通知的条件
        Message message = new Message();
        message.setFromId(SYSTEM_USER_ID);
        message.setToId(event.getEntityUserId());
        message.setConversationId(event.getTopic());
        message.setStatus(0);  // 默认有效

        Map<String, Object> content = new HashMap<>();
        content.put("userId", event.getUserId());   // 触发者
        content.put("entityType", event.getEntityType());
        content.put("entityId", event.getEntityId());
        // 依据这些消息可以拼成： 用户 xxx 评论了你的 帖子 ！

        if(!event.getData().isEmpty()){  // 如果该事件中有附加的其他内容
            for(Map.Entry<String, Object> entry : event.getData().entrySet()){
                content.put(entry.getKey(), entry.getValue());
            }
        }

        message.setContent(JSONObject.toJSONString(content));
        message.setCreateTime(new Date());

        messageService.addMessage(message);
    }
}
