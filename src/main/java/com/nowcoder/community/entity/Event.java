package com.nowcoder.community.entity;

import java.util.HashMap;
import java.util.Map;

public class Event {

    private String topic; // 事件类型
    private int userId;   // 触发事件的人

    // 事件目标的实体
    private int entityType;
    private int entityId;
    private int entityUserId;  //  实体作者

    private Map<String, Object> data = new HashMap<>();  // 预防以后新增业务的可扩展字段

    public String getTopic() {
        return topic;
    }

    public Event setTopic(String topic) {  // 稍作修改，其他set同理  ==> 为了方便处理多个事件
        this.topic = topic;
        return this;
    }

    public int getUserId() {
        return userId;
    }

    public Event setUserId(int userId) {
        this.userId = userId;
        return this;
    }

    public int getEntityType() {
        return entityType;
    }

    public Event setEntityType(int entityType) {
        this.entityType = entityType;
        return this;
    }

    public int getEntityId() {
        return entityId;
    }

    public Event setEntityId(int entityId) {
        this.entityId = entityId;
        return this;
    }

    public int getEntityUserId() {
        return entityUserId;
    }

    public Event setEntityUserId(int entityUserId) {
        this.entityUserId = entityUserId;
        return this;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Event setData(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}
