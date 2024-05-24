# 自己实现——修改用户名

1. dao

UserMapper新增：

```java
int updateUsername(int id, String username);
```

sql实现：

```xml
<update id="updateUsername">
update user
set username = #{username}
where id = #{id}
</update>
```

2. service:

```java
@Autowired
private SensitiveFilter sensitiveFilter;

// 更新用户名
public Map<String, Object> updateUsername(int userId, String username){
    Map<String, Object> map = new HashMap<>();
    User user = userMapper.selectById(userId);
    String oldUsername = user.getUsername();
    username = sensitiveFilter.filter(username);

    if (StringUtils.isBlank(username)) {
        map.put("errorMsg", "新用户名不能为空！");
        return map;
    }
    if(username.equals(oldUsername)){
        map.put("errorMsg", "新用户名和旧用户名不能重复！");
        return map;
    }
    userMapper.updateUsername(userId, username);
    clearCache(userId);  // 记得更新redis缓存，否则查询到的用户名还是不会变
    return map;
}
```

3. UserController:

```java
@LoginRequired
@RequestMapping(path = "/updateUsername", method = RequestMethod.POST)
public String updateUsername(String username,Model model){
    User user = hostHolder.getUser();
    Map<String, Object> map = userService.updateUsername(user.getId(), username);
    if (map == null || map.isEmpty()) {
        return "redirect:/logout";
    } else {
        model.addAttribute("errorMsg", map.get("errorMsg"));
        return "/site/setting";
    }
}
```

4. 更新前端setting.html:

```html
<!--修改用户名-->
<h6 class="text-left text-info border-bottom pb-2">修改用户名</h6>
<form class="mt-5" id="updateUsername" th:action="@{/user/updateUsername}" method="post">
    <div class="form-group row mt-4">
        <label for="head-image" class="col-sm-2 col-form-label text-right">更新用户名:</label>
        <div class="col-sm-10">
            <div class="custom-file">
                <input type="text" class="form-control" name="username" placeholder="请输入新用户名!" required>
                <div class="invalid-feedback" th:text="${errorMsg}">
                    该账号不存在!
                </div>
            </div>
        </div>
    </div>
    <div class="form-group row mt-4">
        <div class="col-sm-2"></div>
        <div class="col-sm-10 text-center">
            <button type="submit" class="btn btn-info text-white form-control">立即更改</button>
        </div>
    </div>
</form>
```