# 自己实现小功能——空白页面

1. 在letter、search-result、notice页面，原来查询结果为空就是空白页面，比较丑陋，遂加代码如下：

letter.html:

```html
<!-- 判断私信是否为空 -->
<div th:if="${#lists.isEmpty(conversations)}">
    <img th:src="@{/img/noResult.png}" alt="无私信" class="img-fluid mx-auto d-block mt-4">
    <p class="text-center mt-3">你暂时没有新的私信哦~</p>
</div>
```

notice.html:

```html
<!--通知为空-->
<div th:if="${commentNotice == null && likeNotice == null && followNotice == null}">
    <img th:src="@{/img/noResult.png}" alt="无通知" class="img-fluid mx-auto d-block mt-4">
    <p class="text-center mt-3">你暂时没有新的通知哦~</p>
</div>
```

search.html:

```html
<!-- 判断搜索结果是否为空 -->
<div th:if="${#lists.isEmpty(discussPosts)}">
    <img th:src="@{/img/noResult.png}" alt="无搜索结果" class="img-fluid mx-auto d-block mt-4">
    <p class="text-center mt-3">没有找到相关内容，请尝试其他关键词。</p>
</div>
```

2. noResult图片来自B站，本人在此声明仅供学习使用，不用于盈利目的

3. 补充：关注列表也可能为空，遂修改：

follower.html:

```html
<!--判空-->
<div th:if="${#lists.isEmpty(users)}">
    <img th:src="@{/img/noResult.png}" alt="无搜索结果" class="img-fluid mx-auto d-block mt-4">
    <p class="text-center mt-3">这里还没有数据呢~ 快去论坛里和大家互动吧！</p>
</div>
```

followee.html:

```html
<!--判空-->
<div th:if="${#lists.isEmpty(users)}">
    <img th:src="@{/img/noResult.png}" alt="无搜索结果" class="img-fluid mx-auto d-block mt-4">
    <p class="text-center mt-3">这里还没有数据呢~ 快去论坛里和大家互动吧！</p>
</div>
```