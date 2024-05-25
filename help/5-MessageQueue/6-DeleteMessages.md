# 自己实现小功能——删除消息/通知

1. 之前已经实现了sql：更改状态：

```xml
<update id="updateStatus">
update message set status = #{status}
where id in
<foreach collection="ids" item="id" open="(" separator="," close=")">   <!--MyBatis循环语法，即for id in ids,以"(,)"作为分隔符-->
    #{id}
</foreach>
</update>
```

2. 之前也实现了dao层:

```java
// 对于多个消息一起设置 已读/删除
int updateStatus(List<Integer> ids, int status);
```

3. MessageService新增：

```java
// 删除消息/通知
// 由于mappper接收的是数组，而删除消息点x删除的只是一条，所以需要转换
public int deleteMessage(int id){
    return  messageMapper.updateStatus(Arrays.asList(new Integer[]{id}), 2);
}
```

4. MessageController新增：

```java
// 删除私信
@RequestMapping(path = "/letter/delete", method = RequestMethod.POST)
@ResponseBody
public String deleteLetter(int id) {
    messageService.deleteMessage(id);
    return CommunityUtil.getJSONString(0);
}

// 删除通知
@RequestMapping(path = "/notice/delete", method = RequestMethod.POST)
@ResponseBody
public String deleteNotice(int id) {
    messageService.deleteMessage(id);
    return CommunityUtil.getJSONString(0);
}
```

5. 前端

letter-detail.html:

```html
<input type="hidden" th:value="${map.letter.id}">
<button type="button" class="ml-2 mb-1 close close-message" data-dismiss="toast"
        aria-label="Close">
    <span aria-hidden="true">&times;</span>
</button>
```

notice-detail.html

```html
<input type="hidden" th:value="${map.notice.id}">
<button type="button" class="ml-2 mb-1 close close-notice" data-dismiss="toast"
        aria-label="Close">
    <span aria-hidden="true">&times;</span>
</button>
```

letter.js新增：

```javascript
$(function(){
	$("#sendBtn").click(send_letter);
	$(".close-message").click(delete_msg);
	$(".close-notice").click(delete_notice);
});

function delete_msg() {
    let btn = this;
    let id = $(btn).prev().val();

    $.post(
        CONTEXT_PATH + "/letter/delete",
        {"id": id},
        function (data) {
            data = $.parseJSON(data);
            if (data.code === 0) {
                $(btn).parents(".media").remove();
            } else {
                alert(data.msg);
            }
        }
    );
}

function delete_notice() {
    let btn = this;
    let id = $(btn).prev().val();

    $.post(
        CONTEXT_PATH + "/notice/delete",
        {"id": id},
        function (data) {
            data = $.parseJSON(data);
            if (data.code === 0) {
                $(btn).parents(".media").remove();
            } else {
                alert(data.msg);
            }
        }
    );
}
```

# Bug解决

由于两个人共享一个消息id，删除时，这条消息的status变成2，一个人删了两人都查不到

1. 创建新表：user_message_status

```sql
CREATE TABLE user_message_status (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    message_id INT NOT NULL,
    status INT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (message_id) REFERENCES message(id)
);
```

2. 更新updateStatus SQL语句，status用

MessageMapper:

```java
List<Message> selectLetters(int userId, String conversationId, int offset, int limit);
int selectLetterCount(int userId, String conversationId);
// 对于多个消息一起设置 已读
int updateStatus(List<Integer> ids, int status);

// 删除对话消息（通知用之前的无影响，故只要重新写delete即可）
int deleteMessage(int userId, List<Integer> messageIds, int status);
int insertUserMessageStatus(int userId, int messageId, int status);
```

sql（需要大幅度更新）:

```xml
<select id="selectConversations" resultType="Message">
    select message.id, from_id, to_id, conversation_id, content, message.status, create_time
    from message
    where message.id in (
    select max(message.id)
    from message join community.user_message_status ums on message.id = ums.message_id
    where ums.status != 2
    and from_id !=1
    and (from_id = #{userId} or to_id = #{userId})
    and ums.user_id = #{userId}
    group by conversation_id
    )
    order by id desc
    limit #{offset}, #{limit}
</select>

<select id="selectConversationCount" resultType="int">
select count(m.maxid)
from (
select max(message.id) as maxid
from message join community.user_message_status ums on message.id = ums.message_id
where ums.status != 2
and from_id !=1
and (from_id = #{userId} or to_id = #{userId})
and ums.user_id = #{userId}
group by conversation_id
) as m
</select>


<select id="selectLetters" resultType="Message">
select message.id, from_id, to_id, conversation_id, content, message.status, create_time
from message join community.user_message_status ums on message.id = ums.message_id
where ums.status != 2
and from_id  != 1
and conversation_id = #{conversationId}
and ums.user_id = #{userId}
order by id desc
limit #{offset}, #{limit}
</select>

<select id="selectLetterCount" resultType="int">
select count(message.id)
from message join community.user_message_status ums on message.id = ums.message_id
where ums.status != 2
and from_id  != 1
and conversation_id = #{conversationId}
and ums.user_id = #{userId}
</select>


<select id="selectLetterUnreadCount" resultType="int">
select count(id)
from message
where status = 0
and to_id = #{userId}
<if test="conversationId != null">
    and conversation_id = #{conversationId}
</if>
</select>


<insert id="insertMessage" parameterType="Message" keyProperty="id">
insert into message (from_id, to_id, conversation_id, content, status, create_time)
values (#{fromId}, #{toId}, #{conversationId}, #{content}, #{status}, #{createTime})
</insert>

<insert id="insertUserMessageStatus" parameterType="map">
insert into user_message_status (user_id, message_id, status)
values (#{userId}, #{messageId}, #{status})
</insert>

<update id="updateStatus">
update message set status = #{status}
where id in
<foreach collection="ids" item="id" open="(" separator="," close=")">
    #{id}
</foreach>
</update>

<update id="deleteMessage">
update user_message_status set status = #{status}
where user_id = #{userId} and message_id in
<foreach collection="messageIds" item="messageId" open="(" separator="," close=")">
    #{messageId}
</foreach>
</update>
```

2. service层：

```java
 public List<Message> findLetters(int userId, String conversationId, int offset, int limit){
    return messageMapper.selectLetters(userId, conversationId, offset, limit);
}

public int findLetterCount(int userId, String conversationId){
    return messageMapper.selectLetterCount(userId,conversationId);
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

// 删除通知
// 由于mappper接收的是数组，而删除消息点x删除的只是一条，所以需要转换
public int deleteNotice(int id){
    return  messageMapper.updateStatus(Arrays.asList(new Integer[]{id}), 2);
}

@Transactional
public int deleteMessage(int userId, int messageId) {
    return messageMapper.deleteMessage(userId, Arrays.asList(new Integer[]{messageId}), 2);
}
```

3. controller层：

```java
 // 私信列表
@RequestMapping(path = "/letter/list", method = RequestMethod.GET)
public String getLetterList(Model model, Page page){
    User user = hostHolder.getUser();

    // 设置分页信息
    page.setLimit(5);
    page.setPath("/letter/list");
    page.setRows(messageService.findConversationCount(user.getId()));

    // 会话列表
    List<Message> conversationlist
            = messageService.findConversations(user.getId(), page.getOffset(), page.getLimit());

    // 封装 未读消息、消息总数等信息
    List<Map<String, Object>> conversations = new ArrayList<>();
    if(conversationlist != null){
        for(Message message : conversationlist){
            Map<String, Object> map = new HashMap<>();
            map.put("conversation", message);  // 和某个用户所有的对话
            map.put("letterCount", messageService.  // 私信总数量
                    findLetterCount(hostHolder.getUser().getId(), message.getConversationId()));
            map.put("unreadCount", messageService.  // 该对话的未读消息
                    findLetterUnreadCount(user.getId(), message.getConversationId()));
            int targetId = user.getId() == message.getFromId() ?
                    message.getToId() : message.getFromId();  // 和本用户相对的用户，用于显示头像
            map.put("target", userService.findUserById(targetId));

            conversations.add(map);
        }
    }

    model.addAttribute("conversations",conversations);

    // 未读消息总数
    int letterUnreadCount = messageService.findLetterUnreadCount(user.getId(), null);
    model.addAttribute("letterUnreadCount", letterUnreadCount);
    int noticeUnreadCount = messageService.findNoticeUnreadCount(user.getId(), null);
    model.addAttribute("noticeUnreadCount", noticeUnreadCount);

    return "/site/letter";
}

// 私信详情
@RequestMapping(path = "/letter/detail/{conversationId}", method = RequestMethod.GET)
public String getLetterDetail(@PathVariable("conversationId")String conversationId,
                              Page page, Model model){
    // 分页信息设置
    page.setLimit(5);
    page.setPath("/letter/detail/" + conversationId);
    page.setRows(messageService.findLetterCount(hostHolder.getUser().getId(), conversationId));

    // 和某个用户的所有对话记录
    List<Message> letterList =  messageService.findLetters(hostHolder.getUser().getId(),conversationId, page.getOffset(), page.getLimit());
    List<Map<String, Object>> letters = new ArrayList<>();
    if(letterList != null){
        for(Message message : letterList){
            Map<String, Object> map = new HashMap<>();
            map.put("letter", message);
            map.put("fromUser", userService.findUserById(message.getFromId()));

            letters.add(map);
        }
    }

    model.addAttribute("letters", letters);

    // 私信的目标
    model.addAttribute("target", getLetterTarget(conversationId));

    // 更新已读
    List<Integer> ids = getUnreadLetterIds(letterList);
    if(!ids.isEmpty()){
        messageService.readMessage(ids);
    }

    return "/site/letter-detail";
}

// 发送私信
@RequestMapping(path = "/letter/send", method = RequestMethod.POST)
@ResponseBody
public String sendLetter(String toName, String content) {
    User target = userService.findUserByName(toName);
    if (target == null) {
        return CommunityUtil.getJSONString(400, "目标用户不存在！");
    }
    if (content == null || content.trim().isEmpty()) {
        return CommunityUtil.getJSONString(400, "发送内容不能为空！");
    }

    Message message = new Message();
    message.setFromId(hostHolder.getUser().getId());
    message.setToId(target.getId());
    if (message.getFromId() < message.getToId()) {
        message.setConversationId(message.getFromId() + "_" + message.getToId());
    } else {
        message.setConversationId(message.getToId() + "_" + message.getFromId());
    }
    message.setContent(content);
    message.setStatus(0);
    message.setCreateTime(new Date());

    messageService.addMessage(message);
    return CommunityUtil.getJSONString(0); // success
}

// 删除私信
@RequestMapping(path = "/letter/delete", method = RequestMethod.POST)
@ResponseBody
public String deleteLetter(int id) {
    messageService.deleteMessage(hostHolder.getUser().getId(), id);
    return CommunityUtil.getJSONString(0);
}

// 删除通知
@RequestMapping(path = "/notice/delete", method = RequestMethod.POST)
@ResponseBody
public String deleteNotice(int id) {
    messageService.deleteNotice(id);
    return CommunityUtil.getJSONString(0);
}
```