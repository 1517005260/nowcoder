# 帖子详情

在首页的帖子列表，点击标题，可以进入帖子详情页面

## 代码实现

1. dao层

在DiscussPostMapper新增访问的方法

```java
//根据id查帖子详情
DiscussPost selectDiscussPostById(int id);
```

在discusspost-mapper.xml新增实现的sql语句

```xml
<select id="selectDiscussPostById" resultType="DiscussPost">
        select <include refid="selectFields"></include>
        from discuss_post
        where id = #{id}
</select>
```

2. service层

在DiscussPostService追加

```java
public DiscussPost findDiscussPostById(int id){
        return discussPostMapper.selectDiscussPostById(id);
}
```

3. controller层

在DiscussPostController追加

```java
    @Autowired
    private UserService userService;

    //帖子详情
    @RequestMapping(path = "/detail/{discussPostId}", method = RequestMethod.GET)
    public String getDiscussPost(@PathVariable("discussPostId") int discussPostId, Model model){
        //帖子
        DiscussPost discussPost = discussPostService.findDiscussPostById(discussPostId);
        model.addAttribute("post", discussPost);
        
        //作者
        User user = userService.findUserById(discussPost.getUserId());
        model.addAttribute("user", user);
        
        //待补充：回复的功能
        
        return "/site/discuss-detail";
    }
```

4. 前端 /site/discuss-detail

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />
<link rel="stylesheet" th:href="@{/css/discuss-detail.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

    <span th:utext="${post.title}">备战春招，面试刷题跟他复习，一个月全搞定！</span>

    <img th:src="${user.headerUrl}" class="align-self-start mr-4 rounded-circle user-header" alt="用户头像" >

    <div class="mt-0 text-warning" th:utext="${user.username}">寒江雪</div>

    发布于 <b th:text="${#dates.format(post.createTime, 'yyyy-MM-dd HH:mm:ss')}">2019-04-15 15:32:18</b>

    <div class="mt-4 mb-3 content" th:utext="${post.content}">
        金三银四的金三已经到了，你还沉浸在过年的喜悦中吗？
        如果是，那我要让你清醒一下了：目前大部分公司已经开启了内推，正式网申也将在3月份陆续开始，金三银四，春招的求职黄金时期已经来啦！！！
        再不准备，作为19应届生的你可能就找不到工作了。。。作为20届实习生的你可能就找不到实习了。。。
        现阶段时间紧，任务重，能做到短时间内快速提升的也就只有算法了，
        那么算法要怎么复习？重点在哪里？常见笔试面试算法题型和解题思路以及最优代码是怎样的？
        跟左程云老师学算法，不仅能解决以上所有问题，还能在短时间内得到最大程度的提升！！！
    </div>


<script th:src="@{/js/global.js}"></script>
```

index增加超链接：

```html
<a th:href="@{|/discuss/detail/${map.post.id}|}" th:utext="${map.post.title}">备战春招，面试刷题跟他复习，一个月全搞定！</a>
```