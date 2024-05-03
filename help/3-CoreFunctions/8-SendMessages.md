# 发送私信

- 发送私信
  - 采用异步的方式发送私信
  - 发送成功后刷新列表
- 设置已读
  - 点击私信详情后，将该私信设置为已读状态


## 代码实现

1. dao

在MessageMapper中新增插入message的方法

```java
// 新增消息
int insertMessage(Message message);

// 对于多个消息一起设置 已读/删除
int updateStatus(List<Integer> ids, int status);
```

sql实现：

```xml
<sql id="insertFields">
        from_id, to_id, conversation_id, content, status, create_time
</sql>
<!--keyProperty是主键，插入会自动++-->
<insert id="insertMessage" parameterType="Message" keyProperty="id">   
insert into message (<include refid="insertFields"></include>)
values (#{fromId}, #{toId}, #{conversationId}, #{content}, #{status}, #{createTime})
</insert>

<update id="updateStatus">
update message set status = #{status}
where id in
<foreach collection="ids" item="id" open="(" separator="," close=")">   <!--MyBatis循环语法，即for id in ids,以"(,)"作为分隔符-->
  #{id}
</foreach>
</update>
```

2. service

在原有MessageService新增

```java
@Autowired
private SensitiveFilter sensitiveFilter;

public int addMessage(Message message){
  message.setContent(HtmlUtils.htmlEscape(message.getContent()));
  message.setContent(sensitiveFilter.filter(message.getContent()));
  return messageMapper.insertMessage(message);
}

public int readMessage(List<Integer> ids){
  return messageMapper.updateStatus(ids, 1);
}
```

3. controller

MessageController新增

```java
    ...
    // 更新已读
    List<Integer> ids = getUnreadLetterIds(letterList);
        if(!ids.isEmpty()){
        messageService.readMessage(ids);
        }

    private List<Integer> getUnreadLetterIds(List<Message> letterList){
      List<Integer> ids = new ArrayList<>();
      if(letterList != null){
        for(Message message : letterList){
          if(message.getToId() == hostHolder.getUser().getId() && message.getStatus() == 0){
            ids.add(message.getId());
          }
        }
      }
      return ids;
    }    

    // 发送私信
    @RequestMapping(path = "/letter/send", method = RequestMethod.POST)
    @ResponseBody
    public String sendLetter(String toName, String content){
        User target = userService.findUserByName(toName);
        if(target == null){
            return CommunityUtil.getJSONString(400, "目标用户不存在！");
        }
        if(content == null || content.trim().isEmpty()){
            return CommunityUtil.getJSONString(400,"发送内容不能为空！");
        }

        Message message = new Message();
        message.setFromId(hostHolder.getUser().getId());
        message.setToId(target.getId());
        if(message.getFromId() < message.getToId()){
            message.setConversationId(message.getFromId() + "_" + message.getToId());
        }else{
            message.setConversationId(message.getToId() + "_" + message.getFromId());
        }
        message.setContent(content);
        message.setStatus(0);
        message.setCreateTime(new Date());

        messageService.addMessage(message);
        return CommunityUtil.getJSONString(0);  //success
    }
```

补充一下findUserByUsername业务：

```java
public User findUserByName(String username){
        return userMapper.selectByName(username);
}
```

4. 前端

在letter.js中

```js
$(function(){
	$("#sendBtn").click(send_letter);
	$(".close").click(delete_msg);
});

function send_letter() {
	$("#sendModal").modal("hide");

	let toName = $("#recipient-name").val();
	let content = $("#message-text").val();

	$.post(
		CONTEXT_PATH + "/letter/send",
		{"toName":toName, "content":content},
		function (data){
			data = $.parseJSON(data);
			if(data.code == 0){
				$("#hintBody").text("发送成功！");
			}else{
				$("#hintBody").text(data.message);
			}
		}
	)

	$("#hintModal").modal("show");
	setTimeout(function(){
		$("#hintModal").modal("hide");
		$("#recipient-name").val("");
		$("#message-text").val("");
		location.reload();
	}, 2000);
}

function delete_msg() {
	// TODO 删除数据
	$(this).parents(".media").remove();
}
```

改动letter-detail.js:

```html
<input type="text" class="form-control" id="recipient-name" th:value="${target.username}">
```