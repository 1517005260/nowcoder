package com.nowcoder.community.event;

import com.alibaba.fastjson.JSONObject;
import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.Event;
import com.nowcoder.community.entity.Message;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.service.ElasticsearchService;
import com.nowcoder.community.service.MessageService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.qiniu.common.QiniuException;
import com.qiniu.common.Zone;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

@Component
public class EventConsumer implements CommunityConstant {
    // 由于消费者要负责处理，所以需要记日志
    private static final Logger logger = LoggerFactory.getLogger(EventConsumer.class);

    @Autowired
    private MessageService messageService;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Value("${wk.image.storage}")
    private String wkImageStorage;

    @Value("${wk.image.command}")
    private String wkImageCommand;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private UserService userService;

    // 由于三个消息通知格式类似，所以写一个方法就行
    // 新增系统通知：@用户
    @KafkaListener(topics = {TOPIC_COMMENT, TOPIC_LIKE, TOPIC_FOLLOW, TOPIC_MENTION})
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

    // 消费发帖事件
    @KafkaListener(topics = {TOPIC_PUBLISH})
    public void handlePublishMessage(ConsumerRecord record){
        if(record == null || record.value() == null){
            logger.error("消息的内容为空！");
        }
        Event event = JSONObject.parseObject(record.value().toString(), Event.class);
        if(event == null){
            logger.error("消息格式错误！");
        }

        DiscussPost post = discussPostService.findDiscussPostById(event.getEntityId());
        elasticsearchService.saveDiscussPost(post);
    }

    // 消费删帖事件
    @KafkaListener(topics = {TOPIC_DELETE})
    public void handleDeleteMessage(ConsumerRecord record){
        if(record == null || record.value() == null){
            logger.error("消息的内容为空！");
        }
        Event event = JSONObject.parseObject(record.value().toString(), Event.class);
        if(event == null){
            logger.error("消息格式错误！");
        }

        elasticsearchService.deleteDiscussPost(event.getEntityId());
    }

    // 消费用户事件
    @KafkaListener(topics = {TOPIC_REGISTER, TOPIC_UPDATE})
    public void handleUserMessage(ConsumerRecord record){
        if(record == null || record.value() == null){
            logger.error("消息的内容为空！");
        }
        Event event = JSONObject.parseObject(record.value().toString(), Event.class);
        if(event == null){
            logger.error("消息格式错误！");
        }
        User user = userService.findUserById(event.getUserId());
        elasticsearchService.saveUser(user);
    }
}
