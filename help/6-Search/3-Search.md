# 开发社区搜索功能

- 搜索服务
  - 将帖子保存至es服务器
  - 从es服务器中删除帖子
  - 从es服务器中搜索帖子
- 发布事件
  - 发布帖子时，帖子异步提交到es服务器（put）
  - 增加评论时，帖子异步提交到es服务器（update）
  - 在Consumer中增加方法，使得能够消费帖子的发布事件
- 显示结果
  - 在controller中处理搜索请求，在前端显示搜索结果

## 代码实现

1. 解决遗留小问题

在discusspost-mapper中增加插入主键，后续event.setEntityId()要用到

```xml
<insert id="insertDiscussPost" parameterType="DiscussPost" keyProperty="id">
    insert into discuss_post(<include refid="insertFields"></include>)
    values(#{userId},#{title},#{content},#{type},#{status},#{createTime},#{commentCount},#{score})
</insert>
```

2. service（dao上次已经写好了）

新建ElasticsearchService

```java
package com.nowcoder.community.service;

import com.nowcoder.community.dao.elasticsearch.DiscussPostRepository;
import com.nowcoder.community.entity.DiscussPost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.CriteriaQueryBuilder;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ElasticsearchService {

    @Autowired
    private DiscussPostRepository discussRepository;  // dao

    @Autowired
    private ElasticsearchTemplate elasticTemplate;

    // 保存与修改（新的save相当于修改）
    public void saveDiscussPost(DiscussPost post){
        discussRepository.save(post);
    }

    // 删除
    public void deleteDiscussPost(int id){
        discussRepository.deleteById(id);
    }

    // 搜索，可以复用上次的test代码
    public Page<DiscussPost> searchDiscussPost(String keyword, int current, int limit){  // 分页传参：第几页和每页条数
        Criteria criteria = new Criteria("title").matches(keyword).or(new Criteria("content").matches(keyword));

        // 这里的高亮格式还是<em></em>，但是前端的global.css已经处理为了高亮红色
        List<HighlightField> highlightFieldList = new ArrayList<>();
        HighlightField highlightField = new HighlightField("title", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
        highlightFieldList.add(highlightField);
        highlightField = new HighlightField("content", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
        highlightFieldList.add(highlightField);
        Highlight highlight = new Highlight(highlightFieldList);
        HighlightQuery highlightQuery = new HighlightQuery(highlight, DiscussPost.class);

        CriteriaQueryBuilder builder = new CriteriaQueryBuilder(criteria)
                .withSort(Sort.by(Sort.Direction.DESC, "type"))
                .withSort(Sort.by(Sort.Direction.DESC, "score"))
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
                .withHighlightQuery(highlightQuery)
                .withPageable(PageRequest.of(current, limit));
        CriteriaQuery query = new CriteriaQuery(builder);

        SearchHits<DiscussPost> result = elasticTemplate.search(query, DiscussPost.class);
        if(result.isEmpty()){
            return null;
        }
        
        List<SearchHit<DiscussPost>> searchHitList = result.getSearchHits();
        List<DiscussPost> discussPostList = new ArrayList<>();
        for (SearchHit<DiscussPost> hit : searchHitList) {
            DiscussPost post = hit.getContent();
            var titleHighlight = hit.getHighlightField("title");
            if (titleHighlight.size() != 0) {
                post.setTitle(titleHighlight.get(0));
            }
            var contentHighlight = hit.getHighlightField("content");
            if (contentHighlight.size() != 0) {
                post.setContent(contentHighlight.get(0));
            }
            discussPostList.add(post);
        }

        return new PageImpl<>(discussPostList, PageRequest.of(current, limit), result.getTotalHits());
    }

}
```

3. controller

<b>生产者</b>

a. 在DiscussPostController中新增发布帖子事件：

```java
@Autowired
private EventProducer eventProducer;

public String addDiscussPost(String title, String content){
    // 前面代码不动，新增即可

  discussPostService.addDiscussPost(discussPost);

  // 发帖事件，存进es服务器
  Event event = new Event()
          .setTopic(TOPIC_PUBLISH)
          .setUserId(user.getId())
          .setEntityType(ENTITY_TYPE_POST)
          .setEntityId(discussPost.getId());
  eventProducer.fireEvent(event);

  return CommunityUtil.getJSONString(0, "发布成功！");
}
```

事件常量接口：

```java
// 发帖
String TOPIC_PUBLISH = "publish";
```

b. 在CommentController中新增修改帖子事件（发评论就要修改帖子表的评论数，相当于修改了帖子）：

```java
public String addComment(@PathVariable("discussPostId") int id, Comment comment){
    // 前面代码不动
    // 修改帖子事件——只有对帖子评论才触发，对评论的回复不触发
    if(comment.getEntityType() == ENTITY_TYPE_POST){
        event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(comment.getUserId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);
    }

  return "redirect:/discuss/detail/" + id;
}
```

<b>消费者</b>

在EventConsumer新增：

```java
@Autowired
private DiscussPostService discussPostService;

@Autowired
private ElasticsearchService elasticsearchService;

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
```

c. 搜索结果展现SearchController:

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.Page;
import com.nowcoder.community.service.ElasticsearchService;
import com.nowcoder.community.service.LikeService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SearchController implements CommunityConstant {
    // 搜索
    @Autowired
    private ElasticsearchService elasticsearchService;
    
    // 搜到后展示作者和点赞数
    @Autowired
    private LikeService likeService;
    
    @Autowired
    private UserService userService;
    
    // 路径格式：/search?keyword=xxx
    @RequestMapping(path = "/search", method = RequestMethod.GET)
    public String search(String keyword, Page page, Model model){
        // 搜索帖子
        // page的current从1开始但是本方法要求从0开始
        org.springframework.data.domain.Page<DiscussPost> searchResult = 
        elasticsearchService.searchDiscussPost(keyword, page.getCurrent() - 1, page.getLimit());

        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if(searchResult != null){
            for(DiscussPost post : searchResult){
                Map<String, Object> map = new HashMap<>();
                
                map.put("post", post);
                map.put("user", userService.findUserById(post.getUserId()));  // 作者
                map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));  // 赞数
                
                discussPosts.add(map);
            }
        }
        model.addAttribute("discussPosts", discussPosts);
        model.addAttribute("keyword", keyword);
        
        page.setPath("/search?keyword=" + keyword);
        page.setRows(searchResult == null ? 0 : (int)searchResult.getTotalElements());
        
        return "/site/search";
    }
}
```

4. 前端

a. index中导航栏的搜索框

```html
<!-- 搜索 -->
<form class="form-inline my-2 my-lg-0" th:action="@{/search}" method="get">
    <input class="form-control mr-sm-2" type="search" aria-label="Search" name="keyword" th:value="${keyword}" />
    <button class="btn btn-outline-light my-2 my-sm-0" type="submit">搜索</button>
</form>
```

b. search.html:

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />

<ul class="list-unstyled mt-4">
  <li class="media pb-3 pt-3 mb-3 border-bottom" th:each="map:${discussPosts}">
    <img th:src="${map.user.headerUrl}" class="mr-4 rounded-circle" alt="用户头像" style="width: 50px;height: 50px">
    <div class="media-body">
      <h6 class="mt-0 mb-3">
        <a th:href="@{|/discuss/detail/${map.post.id}|}" th:utext="${map.post.title}">备战<em>春招</em>，面试刷题跟他复习，一个月全搞定！</a>
      </h6>
      <div class="mb-3" th:utext="${map.post.content}">
        金三银四的金三已经到了，你还沉浸在过年的喜悦中吗？ 如果是，那我要让你清醒一下了：目前大部分公司已经开启了内推，正式网申也将在3月份陆续开始，金三银四，<em>春招</em>的求职黄金时期已经来啦！！！ 再不准备，作为19应届生的你可能就找不到工作了。。。作为20届实习生的你可能就找不到实习了。。。 现阶段时间紧，任务重，能做到短时间内快速提升的也就只有算法了， 那么算法要怎么复习？重点在哪里？常见笔试面试算法题型和解题思路以及最优代码是怎样的？ 跟左程云老师学算法，不仅能解决以上所有问题，还能在短时间内得到最大程度的提升！！！
      </div>
      <div class="text-muted font-size-12">
        <u class="mr-3" th:utext="${map.user.username}">寒江雪</u> 发布于 <b th:text="${#dates.format(map.post.createTime, 'yyyy-MM-dd HH:mm:ss')}">2019-04-15 15:32:18</b>
        <ul class="d-inline float-right">
          <li class="d-inline ml-2">赞 <i th:text="${map.likeCount}">11</i></li>
          <li class="d-inline ml-2">|</li>
          <li class="d-inline ml-2">回复 <i th:text="${map.post.commentCount}">7</i></li>
        </ul>
      </div>
    </div>
  </li>
</ul>

<nav class="mt-5" th:replace="index::pagination">
  
<script th:src="@{/js/global.js}"></script>
```