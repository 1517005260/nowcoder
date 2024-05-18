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