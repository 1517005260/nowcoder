# 登录与登出

- 访问登录页面（已在上节实现）
  - 点击顶部导航栏链接，打开登录页面
- 登录
  - 验证：账号，密码，验证码
  - 成功：生成登录凭证（cookie + session），发回客户端
  - 失败：跳回登录页
- 退出
  - 将登录凭证修改为失效状态
  - 跳转到网站首页

## 登录
1. 由于没有学习Redis，因此我们这次课先将session存入数据库中，相关的表格是`login_ticket`
- ticket：凭证
- status：0正常，1过期
- expired：过期时间

![login_ticket](/imgs/login_ticket.png)

2. 后端进行验证`dao-service-controller`

a. 新建实体`LoginTicket`

```java
package com.nowcoder.community.entity;

import java.util.Date;

public class LoginTicket {
    private int id;
    private int userId;
    private String ticket;
    private int status;
    private Date expired;

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

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getExpired() {
        return expired;
    }

    public void setExpired(Date expired) {
        this.expired = expired;
    }

    @Override
    public String toString() {
        return "LoginTicket{" +
                "id=" + id +
                ", userId=" + userId +
                ", ticket='" + ticket + '\'' +
                ", status=" + status +
                ", expired=" + expired +
                '}';
    }
}
```

b. 新建dao接口`LoginTicketMapper`

```java
package com.nowcoder.community.dao;

import com.nowcoder.community.entity.LoginTicket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginTicketMapper {
    
    int insertLoginTicket(LoginTicket loginTicket);
    
    LoginTicket selectByTicket(String Ticket);
    
    int updateStatus(String Ticket, int status);
}
```

c. 实现sql语句——本次在Mapper中用注解的方式实现sql语句，和之前的xml写sql有所不同，也支持动态sql（这里业务不需要，但是演示了）

```java
@Insert({
        "insert into login_ticket(user_id,ticket,status,expired) ",
        "values(#{userId},#{ticket},#{status},#{expired})"
})
@Options(useGeneratedKeys = true, keyProperty = "id")  // 自动生成id
int insertLoginTicket(LoginTicket loginTicket);

@Select({
        "select id,user_id,ticket,status,expired ",
        "from login_ticket where ticket=#{ticket}"
})
LoginTicket selectByTicket(String ticket);

@Update({
        "<script>",
        "update login_ticket set status=#{status} where ticket=#{ticket} ",
        "<if test=\"ticket!=null\"> ",
        "and 1=1 ",
        "</if>",
        "</script>"
})
int updateStatus(String ticket, int status);
```

d. 由于都是手写的且ide无提示，故先test一下：

```java
    @Autowired
    private LoginTicketMapper loginTicketMapper;    

    @Test
    public void testInsertLoginTicket(){
        LoginTicket loginTicket = new LoginTicket();
        loginTicket.setUserId(100);
        loginTicket.setTicket("xyz");
        loginTicket.setStatus(0);
        loginTicket.setExpired(new Date(System.currentTimeMillis() + 1000 * 60 * 10));  //10min

        loginTicketMapper.insertLoginTicket(loginTicket);
    }

    @Test
    public void testSelectLoginTicket(){
        LoginTicket loginTicket = loginTicketMapper.selectByTicket("xyz");
        System.out.println(loginTicket);
        loginTicketMapper.updateStatus("xyz",1);
        loginTicket = loginTicketMapper.selectByTicket("xyz");
        System.out.println(loginTicket);
    }
```

e. 开发service层，接收传入的条件，进行验证

```java
@Autowired
private LoginTicketMapper loginTicketMapper;

public Map<String, Object> login(String username, String password, int expiredSeconds){
  Map<String, Object> map = new HashMap<>();

  //空值判断
  if(StringUtils.isBlank(username)){
    map.put("usernameMsg", "账号不能为空！");
    return map;
  }
  if(StringUtils.isBlank(password)){
    map.put("passwordMsg", "密码不能为空！");
    return map;
  }

  //合法性验证
  User user = userMapper.selectByName(username);
  if(user == null){
    map.put("usernameMsg", "账号不存在！");
    return map;
  }
  if(user.getStatus() == 0) {
    map.put("usernameMsg", "账号未激活！");
    return map;
  }

  password = CommunityUtil.md5(password + user.getSalt());
  if(!user.getPassword().equals(password)){
    map.put("passwordMsg", "密码不正确！");
    return map;
  }

  //登录成功，生成登录凭证
  LoginTicket loginTicket = new LoginTicket();
  loginTicket.setUserId(user.getId());
  loginTicket.setTicket(CommunityUtil.genUUID());
  loginTicket.setStatus(0);
  loginTicket.setExpired(new Date(System.currentTimeMillis() + expiredSeconds * 1000));
  loginTicketMapper.insertLoginTicket(loginTicket);

  map.put("ticket", loginTicket.getTicket());
  return map;
}
```

f. 开发controller层，进行用户交互

```java
    //登录
    @RequestMapping(path = "/login", method = RequestMethod.POST)
    public String login(String username, String password, String code, boolean rememberme,  ////最后两个是”验证码“ 和 ”记住我“
                        Model model, HttpSession session, HttpServletResponse response){   //Model返回数据，session存code
        //验证码判断
        String kaptcha = (String) session.getAttribute("kaptcha");
        if(StringUtils.isBlank(kaptcha) || StringUtils.isBlank(code) || !kaptcha.equalsIgnoreCase(code)){  //验证码不区分大小写
            model.addAttribute("codeMsg", "验证码不正确");
            return "/site/login";
        }

        //账号密码，交给service判断
        //定义常量区分“记住我”,见常量工具接口
        int expiredSeconds = rememberme ? REMEMBER_EXPIRED_SECONDS : DEFAULT_EXPIRED_SECONDS;
        Map<String, Object> map =userService.login(username, password, expiredSeconds);
        if(map.containsKey("ticket")){
            //success
            //发送cookie让客户端保存
            Cookie cookie = new Cookie("ticket", map.get("ticket").toString());
            cookie.setPath(contextPath);
            cookie.setMaxAge(expiredSeconds);
            response.addCookie(cookie);
            return "redirect:/index";
        }else{
            model.addAttribute("usernameMsg", map.get("usernameMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            return "/site/login";
        }
    }
```

g. 前端开发

```html
<form class="mt-5" method="post" th:action="@{/login}">
  <div class="col-sm-10">
    <input type="text" th:class="|form-control ${usernameMsg!=null?'is-invalid':''}| "
           th:value="${param.username}"
           name="username" id="username" placeholder="请输入您的账号!" required>
    <div class="invalid-feedback" th:text="${usernameMsg}">
      该账号不存在!
    </div>
  </div>
  <div class="col-sm-10">
    <input type="password" th:class="|form-control ${passwordMsg!=null?'is-invalid':''}| "
           th:value="${param.password}"
           name="password" id="password" placeholder="请输入您的密码!" required>
    <div class="invalid-feedback" th:text="${passwordMsg}">
      密码长度不能小于8位!
    </div>
  </div>
  <div class="col-sm-6">
    <input type="text" th:class="|form-control ${codeMsg!=null?'is-invalid':''}| "
           name="code" id="verifycode" placeholder="请输入验证码!">
    <div class="invalid-feedback" th:text="${codeMsg}">
      验证码不正确!
    </div>
  </div>
  <div class="col-sm-10">
  <input type="checkbox"
         name="rememberme" id="remember-me"
         th:checked="${param.rememberme}">
  <label class="form-check-label" for="remember-me">记住我</label>
```

## 登出
1. ticket无效：

```java
public void logout(String ticket){
        loginTicketMapper.updateStatus(ticket,1);
    }
```

2. controller处理

```java
    @RequestMapping(path = "/logout", method = RequestMethod.GET)
    public String logout(@CookieValue("ticket") String ticket){
        userService.logout(ticket);
        return "redirect:/login";
    }
```

3. 前端——导航栏index中

```html
<a class="dropdown-item text-center" th:href="@{/logout}">退出登录</a>
```