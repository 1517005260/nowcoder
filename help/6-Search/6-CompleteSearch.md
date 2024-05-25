# 自己实现——完善搜索功能

## 搜索用户界面显示——uid、粉丝数、获赞数、关注TA

1. SearchController修改：

```java
@RequestMapping(path = "/searchUser", method = RequestMethod.GET)
public String searchUser(String username, Page page, Model model){
    // 搜索用户
    org.springframework.data.domain.Page<User> searchResult =
            elasticsearchService.searchUser(username, page.getCurrent() - 1, page.getLimit());

    List<Map<String, Object>> Users = new ArrayList<>();
    if(searchResult != null){
        for(User user : searchResult){
            Map<String, Object> map = new HashMap<>();

            map.put("user", user);
            map.put("uid", user.getId());
            map.put("username", user.getUsername());
            map.put("headerUrl", user.getHeaderUrl());
            map.put("createTime", user.getCreateTime());
            map.put("type", user.getType());

            map.put("likeCount", likeService.findUserLikeCount(user.getId()));
            map.put("followers", followService.findFollowerCount(ENTITY_TYPE_USER, user.getId()));

            boolean hasFollowed = false;
            if(hostHolder.getUser() != null){
                hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, user.getId());
            }
            map.put("hasFollowed", hasFollowed);

            Users.add(map);
        }
    }
    model.addAttribute("Users", Users);
    model.addAttribute("name", username);
    model.addAttribute("hostUser", hostHolder.getUser());

    page.setPath("/searchUser?name=" + username);
    page.setRows(searchResult == null ? 0 : (int)searchResult.getTotalElements());

    return "/site/searchUser";
}
```

2. SearchUser前端界面样式修改：

```html
<style>
    .media-body {
        display: flex;
        flex-direction: column;
    }

    .media-body h6 {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }

    .status-container {
        display: flex;
        align-items: center;
    }

    .status-badge {
        position: absolute;
        background-color: rgb(51, 133, 255);
        font-size: 14px;
        color: white;
        padding: 5px;
        border-radius: 10px;
        user-select: none;
        left: 560px;
    }

    .follow-btn {
        margin-left: auto;
    }

    .media-body .d-flex {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }

    .media-body .d-flex span {
        margin-left: 10px;
    }
</style>

<ul class="list-unstyled mt-4">
    <li class="media pb-3 pt-3 mb-3 border-bottom" th:each="map:${Users}">
        <a th:href="@{|/user/profile/${map.uid}|}">
            <img th:src="${map.headerUrl}" class="mr-4 rounded-circle" alt="用户头像" style="width: 50px;height: 50px">
        </a>
        <div class="media-body">
            <h6 class="mt-0 mb-3 d-flex align-items-center justify-content-between">
                <a th:href="@{|/user/profile/${map.uid}|}" th:utext="${map.username}" style="margin-left: 10px">备战<em>春招</em>，面试刷题跟他复习，一个月全搞定！</a>
                <span class="status-badge" th:if="${map.type==0}">普通用户</span>
                <span class="status-badge" th:if="${map.type==1}">管理员</span>
                <span class="status-badge" th:if="${map.type==2}">版主</span>
                <span class="status-container">
                                <input type="hidden" id="entityId" th:value="${map.uid}">
                                <button type="button"
                                        th:class="|btn ${map.hasFollowed?'btn-secondary':'btn-info'} btn-sm follow-btn|"
                                        th:text="${map.hasFollowed?'已关注':'关注TA'}"
                                        th:if="${hostUser != null && hostUser.id != map.uid}"
                                >关注TA</button>
                            </span>
            </h6>
            <div class="d-flex align-items-center justify-content-between">
                <a th:href="@{|/user/profile/${map.uid}|}">
                    <span>uid: <span th:text="${map.uid}"></span></span>
                </a>
                <a th:href="@{|/user/profile/${map.uid}|}">
                    <span>获赞 <span th:text="${map.likeCount}" style="color: rgb(217,52,111)"></span></span>
                </a>
                <a th:href="@{|/followers/${map.uid}|}">
                    <span>粉丝 <span th:text="${map.followers}" style="color: rgb(47,122,253);"></span> 人</span>
                </a>
                <a th:href="@{|/user/profile/${map.uid}|}">
                    <span>注册于 <span th:text="${#dates.format(map.createTime, 'yyyy-MM-dd HH:mm:ss')}"></span></span>
                </a>
            </div>
        </div>
    </li>
</ul>

<script th:src="@{/js/profile.js}"></script>
```

3. 顺便修复搜索的bug——搜索框输入空值，会找到所有的东西：

在ESservice中搜索时，加上判空即可：

```java
if (keyword == null || keyword.trim().isEmpty()) {
            // 处理空关键字的情况，例如返回空结果
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(current, limit), 0);
        }
```