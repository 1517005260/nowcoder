# 显示系统通知2

## 通知详情

1. dao

MessageMapper新增

```java
// 某个主题包含的所有消息
List<Message> selectNotice(int userId, String topic, int offset, int limit);
```

sql实现message-mapper

```xml
<select id="selectNotice" resultType="Message">
    select <include refid="selectFields"></include>
    from message
    where status != 2
    and from_id = 1
    and to_id = #{userId}
    and conversation_id = #{topic}
    order by create_time desc 
    limit #{offset}, #{limit}
</select>
```

2. service

MessageService新增：

```java
public List<Message> findNotices(int userId, String topic, int offset, int limit){
    return messageMapper.selectNotice(userId, topic, offset, limit);
}
```

3. controller

MessageController新增:

```java
@RequestMapping(path = "/notice/detail/{topic}", method = RequestMethod.GET)
public String getNoticeDetail(@PathVariable("topic")String topic, Page page, Model model){
    User user = hostHolder.getUser();
    
    page.setLimit(5);
    page.setPath("/notice/detail/" + topic);
    page.setRows(messageService.findNoticeCount(user.getId(), topic));
    
    List<Message> noticeList = messageService.findNotices(user.getId(), topic, page.getOffset(), page.getLimit());
    List<Map<String, Object>> noticeVoList = new ArrayList<>();
    if(noticeList != null){
        for(Message notice : noticeList){
            Map<String, Object> map = new HashMap<>();
            // 通知
            map.put("notice", notice);
            // 内容
            String content = HtmlUtils.htmlUnescape(notice.getContent());
            Map<String, Object> data = JSONObject.parseObject(content, HashMap.class);
            map.put("user", userService.findUserById((Integer) data.get("userId")));
            map.put("entityType", data.get("entityType"));
            map.put("entityId", data.get("entityId"));
            map.put("postId", data.get("postId"));  // follow notice无postId
            // 通知作者——系统用户
            map.put("fromUser", userService.findUserById(notice.getFromId()));
            
            noticeVoList.add(map);
        }
    }
    model.addAttribute("notices", noticeVoList);
    
    // 设置已读状态
    List<Integer> ids = getUnreadLetterIds(noticeList);
    if(!ids.isEmpty()){
        messageService.readMessage(ids);
    }
    
    return "/site/notice-detail";
}
```

4. 前端

notice.html

```html
<a th:href="@{/notice/detail/comment}">
    用户 <i th:utext="${commentNotice.user.username}">nowcoder</i> 评论了你的
<b th:text="${commentNotice.entityType==1?'帖子':'回复'}">帖子</b> ...</a>
<a th:href="@{/notice/detail/comment}">
    <li class="d-inline ml-2"><span class="text-primary">共 <i th:text="${commentNotice.count}">3</i> 条消息</span></li>
</a>

<a th:href="@{/notice/detail/like}">用户
    <i th:utext="${likeNotice.user.username}">nowcoder</i> 点赞了你的
<b th:text="${likeNotice.entityType==1?'帖子':'回复'}">帖子</b> ...</a>
<a th:href="@{/notice/detail/like}">
    <li class="d-inline ml-2"><span class="text-primary">共 <i th:text="${likeNotice.count}">3</i> 条消息</span></li>
</a>

<a th:href="@{/notice/detail/follow}">用户
    <i th:utext="${followNotice.user.username}">nowcoder</i> 关注了你 ...</a>
<a th:href="@{/notice/detail/follow}">
    <li class="d-inline ml-2"><span class="text-primary">共 <i th:text="${followNotice.count}">3</i> 条消息</span></li>
</a>
```

notice-detail.html

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />
<link rel="stylesheet" th:href="@{/css/letter.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

    <button type="button" class="btn btn-secondary btn-sm" onclick="back();">返回</button>

    <ul class="list-unstyled mt-4">
        <li class="media pb-3 pt-3 mb-2" th:each="map:${notices}">
            <img th:src="${map.fromUser.headerUrl}" class="mr-4 rounded-circle user-header" alt="系统图标">
            <div class="toast show d-lg-block" role="alert" aria-live="assertive" aria-atomic="true">
                <div class="toast-header">
                    <strong class="mr-auto" th:utext="${map.fromUser.username}">落基山脉下的闲人</strong>
                    <small th:text="${#dates.format(map.notice.createTime, 'yyyy-MM-dd HH:mm:ss')}">2019-04-25 15:49:32</small>
                    <button type="button" class="ml-2 mb-1 close" data-dismiss="toast" aria-label="Close">
                        <span aria-hidden="true">&times;</span>
                    </button>
                </div>
                <div class="toast-body">
								<span th:if="${topic.equals('comment')}">用户
									<i th:utext="${map.user.username}">nowcoder</i>
									评论了你的<b th:text="${map.entityType==1?'帖子':'回复'}">帖子</b>,
									<a class="text-primary" th:href="@{|/discuss/detail/${map.postId}|}">点击查看</a> !
								</span>
                    <span th:if="${topic.equals('like')}">用户
									<i th:utext="${map.user.username}">nowcoder</i>
									点赞了你的<b th:text="${map.entityType==1?'帖子':'回复'}">帖子</b>,
									<a class="text-primary" th:href="@{|/discuss/detail/${map.postId}|}">点击查看</a> !
								</span>
                    <span th:if="${topic.equals('follow')}">用户
									<i th:utext="${map.user.username}">nowcoder</i>
									关注了你,
									<a class="text-primary" th:href="@{|/user/profile/${map.user.id}|}">点击查看</a> !
								</span>
                </div>
            </div>
        </li>
    </ul>

<nav class="mt-5" th:replace="index::pagination">

<script th:src="@{/js/global.js}"></script>
<script th:src="@{/js/letter.js}"></script>
<script>
    function back(){
        location.href = CONTEXT_PATH + "/notice/list";
    }
</script>
```

5. 导航栏上的消息数

新建拦截器MessageInterceptor

```java
package com.nowcoder.community.controller.interceptor;

import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.MessageService;
import com.nowcoder.community.util.HostHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class MessageInterceptor implements HandlerInterceptor {
    
    @Autowired
    private HostHolder hostHolder;
    
    @Autowired
    private MessageService messageService;
    
    // 在模板调用前拦截即可
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        User user = hostHolder.getUser();
        
        if(user != null && modelAndView != null){
            int letterUnreadCount = messageService.findLetterUnreadCount(user.getId(), null);
            int noticeUnreadCount = messageService.findNoticeUnreadCount(user.getId(), null);
            modelAndView.addObject("allUnreadCount", letterUnreadCount + noticeUnreadCount);
        }
    }
}
```

进行拦截器的配置，在WebMvcConfig新增：

```java
@Autowired
private MessageInterceptor messageInterceptor;

registry.addInterceptor(messageInterceptor).
excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg");
```

index.html中更新导航栏

```html
<a class="nav-link position-relative" th:href="@{/letter/list}">消息<span class="badge badge-danger" th:text="${allUnreadCount!=0?allUnreadCount:''}">12</span></a>
```