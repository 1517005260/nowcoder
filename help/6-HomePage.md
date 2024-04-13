# 开发社区首页

综合前面几次的学习，开发社区首页的功能

web项目 = 若干请求的组合

<br>

![复习请求](/imgs/reviewRequest.png)

<br>

所以，可以按照依赖顺序：<b>dao -> service -> controller</b>的顺序开发

## 本次课内容
1. 开发流程
- 1次请求的执行过程
2. 分步实现
- 开发社区首页，显示前10个帖子
- 开发分页组件，分页显示所有帖子

## 代码实现
1. 建表，这在上次配置MySQL的时候已经做好了，帖子表名`discuss_post`
- 表的结构：
<br>

```
CREATE TABLE `discuss_post` (
  `id` int NOT NULL AUTO_INCREMENT,          // 帖子编号-主键 
  `user_id` varchar(45) DEFAULT NULL,        // 外键id
  `title` varchar(100) DEFAULT NULL,         //标题和内容
  `content` text,
  `type` int DEFAULT NULL COMMENT '0-普通; 1-置顶;',      // 帖子类型
  `status` int DEFAULT NULL COMMENT '0-正常; 1-精华; 2-拉黑;',     //帖子状态
  `create_time` timestamp NULL DEFAULT NULL,           //发布时间
  `comment_count` int DEFAULT NULL,           //评论数  冗余存储，我们有评论表但还是在帖子表存储了评论数，因为查询评论数是个频繁操作，这样就不用关联其他表增加时间了
  `score` double DEFAULT NULL,                //热度
  PRIMARY KEY (`id`),
  KEY `index_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=281 DEFAULT CHARSET=utf8mb3
```


2. 开发dao数据访问层
- 首先写好实体类封装表的数据，这步没有具体的逻辑。表里有什么字段照着抄就行
```java
package com.nowcoder.community.entity;

import java.util.Date;

public class DiscussPost {
    private int id;
    private int userId;
    private String title;
    private String content;
    private int type;
    private int status;
    private Date createTime;
    private int commentCount;
    private double score;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "DiscussPost{" +
                "id=" + id +
                ", userId=" + userId +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", createTime=" + createTime +
                ", commentCount=" + commentCount +
                ", score=" + score +
                '}';
    }
}
```
- 写完entity后在dao下开发mapper接口连接数据库，并声明查询方法。我们这次采用分页查询，返回的是一个链表/集合

```java
package com.nowcoder.community.dao;

import com.nowcoder.community.entity.DiscussPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiscussPostMapper {

    //"我的帖子"功能预备，但是首页查询不需要提供uerId，因为是查全体帖子
    //因此需要动态sql，有时需要userId，有时不需要
    //mysql的分页功能很方便，只要改limit参数即可，传入每页的起始行行号offset和最多显示多少条数据limit
    List<DiscussPost> selectDiscussPosts(int userId, int offset, int limit);

    //页数 = 帖子数 / 每页贴子数
    int selectDiscussPostRows(@Param("userId") int userId);  //@Param可以给参数起别名，使用名字过长的变量
    //另外 **若我想动态拼接sql，并且这个函数有且只有一个参数，就一定需要别名**

}
```

- 在Mapper下的xml文件里写sql语句。使用了if标签进行动态sql查询。倒序输出是因为置顶在上面，并且条件一致下按时间倒序。

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.nowcoder.community.dao.DiscussPostMapper">

    <sql id="selectFields">
        id, user_id, title, content, type, status, create_time, comment_count, score
    </sql>

    <select id="selectDiscussPosts" resultType="DiscussPost">
        select <include refid="selectFields"></include>
        from discuss_post
        where status !=2
        <if test="userId !=0">
            and user_id = #{userId}
        </if>
        order by type desc, create_time desc
        limit #{offset}, #{limit}
    </select>

    <select id="selectDiscussPostRows" resultType="int">
        select count(id)
        from discuss_post
        where status !=2
        <if test="userId!=0">
            and user_id = #{userId}
        </if>
    </select>

</mapper>
```

- 开发完毕，进行dao层的测试

```java
@Autowired
private DiscussPostMapper discussPostMapper;

//不加userId
@Test
public void testSelectPosts(){
    List<DiscussPost> lists = discussPostMapper.selectDiscussPosts(0,0,10);
    for (DiscussPost post:lists){
        System.out.println(post);
    }
    int rows = discussPostMapper.selectDiscussPostRows(0);
    System.out.println(rows);
}

//加userId
@Test
public void testSelectPosts(){
    List<DiscussPost> lists = discussPostMapper.selectDiscussPosts(149,0,10);
    for (DiscussPost post:lists){
        System.out.println(post);
    }
    int rows = discussPostMapper.selectDiscussPostRows(149);
    System.out.println(rows);
}
```

输出

```bash
// 不加userId
DiscussPost{id=275, userId=11, title='我是管理员', content='我是管理员，你们都老实点！', type=1, status=1, createTime=Thu May 16 18:58:44 CST 2019, commentCount=12, score=1751.2900346113624}
DiscussPost{id=234, userId=111, title='玄学帖', content='据说玄学贴很灵验，求大佬捞捞我这个菜鸡给个机会！', type=1, status=0, createTime=Sat Apr 13 17:54:04 CST 2019, commentCount=13, score=1718.1335389083702}
DiscussPost{id=280, userId=149, title='事务', content='事务的4个特性，包括原子性、一致性、隔离性、持久性。', type=0, status=0, createTime=Mon May 20 17:41:30 CST 2019, commentCount=16, score=1755.2095150145426}
DiscussPost{id=277, userId=149, title='Spring Cache', content='Spring Cache RedisCacheManager', type=0, status=0, createTime=Fri May 17 17:06:54 CST 2019, commentCount=38, score=1752.5797835966168}
DiscussPost{id=276, userId=149, title='新人报道', content='新人报道，请多关照！', type=0, status=0, createTime=Fri May 17 15:50:18 CST 2019, commentCount=6, score=1751.806179973984}
DiscussPost{id=274, userId=146, title='我要offer', content='跪求offer~~~', type=0, status=1, createTime=Wed May 15 11:34:14 CST 2019, commentCount=37, score=1750.6522463410033}
DiscussPost{id=273, userId=145, title='哈哈', content='哈哈哈哈', type=0, status=0, createTime=Sun Apr 28 15:32:45 CST 2019, commentCount=2, score=1732.3802112417115}
DiscussPost{id=271, userId=138, title='public', content='public static void main', type=0, status=0, createTime=Thu Apr 25 15:22:16 CST 2019, commentCount=2, score=1729.3802112417115}
DiscussPost{id=270, userId=138, title='xxx', content='xxx', type=0, status=0, createTime=Thu Apr 25 14:45:18 CST 2019, commentCount=6, score=1729.7923916894983}
DiscussPost{id=265, userId=103, title='互联网求职暖春计划', content='今年的就业形势，确实不容乐观。过了个年，仿佛跳水一般，整个讨论区哀鸿遍野！19届真的没人要了吗？！18届被优化真的没有出路了吗？！大家的&ldquo;哀嚎&rdquo;与&ldquo;悲惨遭遇&rdquo;牵动了每日潜伏于讨论区的牛客小哥哥小姐姐们的心，于是牛客决定：是时候为大家做点什么了！为了帮助大家度过&ldquo;寒冬&rdquo;，牛客网特别联合60+家企业，开启互联网求职暖春计划，面向18届&amp;19届，拯救0 offer！', type=0, status=0, createTime=Thu Apr 25 10:14:05 CST 2019, commentCount=0, score=0.0}
149

//加userId
DiscussPost{id=280, userId=149, title='事务', content='事务的4个特性，包括原子性、一致性、隔离性、持久性。', type=0, status=0, createTime=Mon May 20 17:41:30 CST 2019, commentCount=16, score=1755.2095150145426}
DiscussPost{id=277, userId=149, title='Spring Cache', content='Spring Cache RedisCacheManager', type=0, status=0, createTime=Fri May 17 17:06:54 CST 2019, commentCount=38, score=1752.5797835966168}
DiscussPost{id=276, userId=149, title='新人报道', content='新人报道，请多关照！', type=0, status=0, createTime=Fri May 17 15:50:18 CST 2019, commentCount=6, score=1751.806179973984}
3
```

3. 开发service业务层
- 新建业务组件`DiscussPostService`

```java
package com.nowcoder.community.service;

import com.nowcoder.community.dao.DiscussPostMapper;
import com.nowcoder.community.entity.DiscussPost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiscussPostService {

    @Autowired
    private DiscussPostMapper discussPostMapper;

    public List<DiscussPost> findDiscussPosts(int userId, int offset, int limit){
        return discussPostMapper.selectDiscussPosts(userId, offset,limit);
    }

    public int findDiscussPostRows(int userId){
        return discussPostMapper.selectDiscussPostRows(userId);
    }
}

```

返回的DiscussPost里面有外键userId，但是页面上肯定不会显示id而是显示名字。<br>
解决：sql连接or单独对每个DiscussPost进行username查询，结果和原DiscussPost组合一起返回<br>
我们采用后者，和后续redis结合性能更高<br>

- 上述新功能不适合放在`DiscussPostService`组件里，因为不是帖子相关而是用户相关。因此新建组件`UserService`

```java
package com.nowcoder.community.service;

import com.nowcoder.community.dao.UserMapper;
import com.nowcoder.community.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    //根据id查用户
    public User findUserById(int id){
        return userMapper.selectById(id);
    }
}
```

- 开发完毕，接下来关键的View可以依赖本层实现。

4. View部分开发 —— 前端与controller
- 前端文件见`static`和`templates`
- 本节课只会用到`index.html`
- 新建controller`HomeController`处理首页请求

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//controller映射路径可省，这样直接访问的就是方法
@Controller
public class HomeController {

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private UserService userService;

    @RequestMapping(path = "/index", method = RequestMethod.GET)
    public String getIndexPage(Model model){
        // 默认是第一页，前10个帖子
        List<DiscussPost> list = discussPostService.findDiscussPosts(0,0,10);

        // 将前10个帖子和对应的user对象封装
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if(list !=null){
            for(DiscussPost post:list){
                Map<String,Object> map = new HashMap<>();
                map.put("post" , post);
                User user = userService.findUserById(post.getUserId());
                map.put("user", user);
                discussPosts.add(map);
            }
        }
        // 处理完的数据填充给前端页面
        model.addAttribute("discussPosts", discussPosts);
        return "index";
    }
}
```

- 前端`index.html`处理：

```html
...
<!--xmlns指明模板类型-->
<html lang="en" xmlns:th="http://www.thymeleaf.org">
...
<!--th:herf语法，固定向static/下找资源-->
<link rel="stylesheet" th:href="@{/css/global.css}" />
...
<!-- 帖子列表 用一个模板就行，循环输出，数据向后端请求即可-->
<ul class="list-unstyled">
    <!--th循环语法each="变量类型:${变量}}"-->
    <li class="media pb-3 pt-3 mb-3 border-bottom" th:each="map:${discussPosts}">
        <a href="site/profile.html">
            <!--引用后端数据库的头像-->
            <!--虽然是.，但是实际上底层调用的是get()方法-->
            <img th:src="${map.user.headerUrl}" class="mr-4 rounded-circle" alt="用户头像" style="width:50px;height:50px;">
        </a>
        <div class="media-body">
            <h6 class="mt-0 mb-3">
                <!--utext可以将转义字符转义-->
                <a href="#" th:utext="${map.post.title}">备战春招，面试刷题跟他复习，一个月全搞定！</a>
                <!--th的判断语法-->
                <span class="badge badge-secondary bg-primary" th:if="${map.post.type==1}">置顶</span>
                <span class="badge badge-secondary bg-danger" th:if="${map.post.status==1}">精华</span>
            </h6>
            <div class="text-muted font-size-12">
                <!--记得对时间格式化-->
                <u class="mr-3" th:utext="${map.user.username}">寒江雪</u> 发布于 <b th:text="${#dates.format(map.post.createTime,'yyyy-MM-dd HH:mm:ss')}">2019-04-15 15:32:18</b>
                <ul class="d-inline float-right">
                    <li class="d-inline ml-2">赞 11</li>
                    <li class="d-inline ml-2">|</li>
                    <li class="d-inline ml-2">回帖 7</li>
                </ul>
            </div>
        </div>
    </li>
</ul>
...
<script th:src="@{/js/global.js}"></script>
<script th:src="@{js/index.js}"></script>
```

5. 完善分页功能——点页数跳转到相应页面
- 开发相应组件：也是由dao->service->controller的思路

- Page实体访问数据库：

```java
package com.nowcoder.community.entity;

//封装分页相关信息
public class Page {
    
    //当前页码
    private int current = 1;
    
    //显示上限
    private int limit = 10;
    
    //数据总数（计算总页数）
    private int rows;
    
    //查询路径（复用分页链接）
    private String path;

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        if(current >= 1 )   //注意判断数据合法性
            this.current = current;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        if(limit >= 1 && limit <= 100)
            this.limit = limit;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        if(rows >= 0)
            this.rows = rows;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
    
    public int getOffset(){
        //获取当前页的起始行：current * limit - limit
        return (current - 1) * limit;
    }
    
    public int getTotal(){
        //获取总页数
        if(rows % limit == 0)
            return rows / limit;
        else 
            return rows / limit + 1;
    }
    
    // 根据当前页算出导航栏起始页和结束页
    public int getFrom(){
        int from = current - 2;
        if(from < 1)
            return 1;
        else 
            return from;
    }
    
    public int getTo(){
        int to = current + 2;
        int total = getTotal();
        if(to > total)
            return total;
        else 
            return to;
    }
}
```

- Controller逻辑更新：

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.Page;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//controller映射路径可省，这样直接访问的就是方法
@Controller
public class HomeController {

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private UserService userService;

    @RequestMapping(path = "/index", method = RequestMethod.GET)
    public String getIndexPage(Model model, Page page){
        
        //方法调用前，SpringMVC会自动实例化Model和Page，并将Page注入Model
        //所以不用model.addAttribute(Page),直接在thmeleaf可以访问Page的数据
        
        page.setRows(discussPostService.findDiscussPostRows(0));
        page.setPath("/index");
        // 默认是第一页，前10个帖子
        List<DiscussPost> list = discussPostService.findDiscussPosts(0, page.getOffset(), page.getLimit());

        // 将前10个帖子和对应的user对象封装
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if(list !=null){
            for(DiscussPost post:list){
                Map<String,Object> map = new HashMap<>();
                map.put("post" , post);
                User user = userService.findUserById(post.getUserId());
                map.put("user", user);
                discussPosts.add(map);
            }
        }
        // 处理完的数据填充给前端页面
        model.addAttribute("discussPosts", discussPosts);
        return "index";
    }
}
```

- index.html更新：

```html
<!-- 分页 -->
<nav class="mt-5" th:if="${page.rows>0}">
    <ul class="pagination justify-content-center">
        <li class="page-item">
            <!--即转换为： /index?current=1-->
            <a class="page-link" th:href="@{${page.path}(current=1)}">首页</a>
        </li>
        <!--如果当前是第一页，则上一页按钮不可点击-->
        <li th:class="|page-item ${page.current==1?'disabled':''}|">
            <!--上一页按钮-->
            <a class="page-link" th:href="@{${page.path}(current=${page.current-1})}">上一页</a></li>
        <!--生成从from->to的数组-->
        <!--active表示 刚好是当前页时，图标点亮-->
        <li th:class="|page-item ${i==page.current?'active':''}|" th:each="i:${#numbers.sequence(page.from,page.to)}">
            <a class="page-link" href="#" th:text="${i}">1</a>
        </li>
        <li th:class="|page-item ${page.current==page.total?'disabled':''}|">
            <a class="page-link" th:href="@{${page.path}(current=${page.current+1})}">下一页</a>
        </li>
        <li class="page-item">
            <a class="page-link" th:href="@{${page.path}(current=${page.total})}">末页</a>
        </li>
    </ul>
</nav>
```