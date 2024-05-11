# 用redis重构登录模块

- 使用redis缓存验证码
  - 验证码需要频繁访问和刷新，因此对性能要求高
  - 验证码无需永久保存，一般生效时间很短（登录完就没用了）
  - 分布式存储时，存在[Session共享问题](../2-LoginAndRegister/3-SessionManagement.md)
- 使用redis存储登录凭证（login_ticket）
  - 每次拦截请求时，都要查询用户的登录凭证，访问频率非常高
- 使用redis缓存用户信息
  - 每次请求时，都要根据凭证查询用户信息，访问频率非常高

## 使用redis缓存验证码

1. 在RedisKeyUtil新增：

```java
private static final String PREFIX_KAPTCHA = "kaptcha";

// 验证码，由于用户未登录，我们用cookie里的随机字符串区分
public static String getKaptchaKey(String owner){
  return PREFIX_KAPTCHA + SPLIT + owner;
}
```

2. 改动LoginController，由session变成redis:

```java
@Autowired
private RedisTemplate redisTemplate;

//验证码图片
@RequestMapping(path = "/kaptcha", method = RequestMethod.GET)
public void getKaptcha(HttpServletResponse response) {
  // 生成验证码
  String text = kaptchaProducer.createText();
  BufferedImage image = kaptchaProducer.createImage(text);

  // 验证码归属
  String kaptchaOwner = CommunityUtil.genUUID();
  Cookie cookie = new Cookie("kaptchaOwner", kaptchaOwner);
  cookie.setMaxAge(60); // 秒
  cookie.setPath(contextPath); // cookie在整个项目下有效
  response.addCookie(cookie);  // 向用户发送cookie

  // 验证码存入redis
  String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
  redisTemplate.opsForValue().set(redisKey, text, 60, TimeUnit.SECONDS);


  // 将图片输出给浏览器
  response.setContentType("image/png");
  try {
    OutputStream os = response.getOutputStream();
    ImageIO.write(image, "png", os);
  } catch (IOException e) {
    logger.error("响应验证码失败:" + e.getMessage());
  }
}

//登录
@RequestMapping(path = "/login", method = RequestMethod.POST)
public String login(String username, String password, String code, boolean rememberme,  //最后两个是”验证码“ 和 ”记住我“
                    Model model, HttpServletResponse response, @CookieValue("kaptchaOwner")String kaptchaOwner){   //Model返回数据，session存code
  //验证码判断
  String kaptcha = null;
  if(StringUtils.isNoneBlank(kaptchaOwner)){  // 如果还未过期
    String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
    kaptcha = (String) redisTemplate.opsForValue().get(redisKey);
  }
  
  /* 剩下的代码不动 */
}
```

## 使用redis存储登录凭证（login_ticket）

1. 定义key

```java
private static final String PREFIX_TICKET = "ticket";

// 登录凭证
public static String getTicketKey(String ticket){
  return PREFIX_TICKET + SPLIT + ticket;
}
```

2. 由于转向了redis，于是废弃原loginTickerMapper

在接口上声明`@Deprecated`即可

3. 重构UserService，删除loginTickerMapper的调用

```java
/* 原loginTickerMapper的注入换成redisTemplate */
@Autowired
private RedisTemplate redisTemplate;

public Map<String, Object> login(String username, String password, int expiredSeconds){
    /* 上面代码不变 */
  //登录成功，生成登录凭证
  LoginTicket loginTicket = new LoginTicket();
  loginTicket.setUserId(user.getId());
  loginTicket.setTicket(CommunityUtil.genUUID());
  loginTicket.setStatus(0);
  loginTicket.setExpired(new Date(System.currentTimeMillis() + expiredSeconds * 1000));

  // 存入redis
  String redisKey = RedisKeyUtil.getTicketKey(loginTicket.getTicket());
  redisTemplate.opsForValue().set(redisKey, loginTicket);  // redis自动把这个对象序列化为json字符串

  map.put("ticket", loginTicket.getTicket());
  return map;
}

public void logout(String ticket){
  String redisKey = RedisKeyUtil.getTicketKey(ticket);
  LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(redisKey);
  loginTicket.setStatus(1); //删除态，我们不能直接删，要保留用户的登录记录
  redisTemplate.opsForValue().set(redisKey, loginTicket);
}

public LoginTicket findLoginTicket(String ticket){
  String redisKey = RedisKeyUtil.getTicketKey(ticket);
  return (LoginTicket) redisTemplate.opsForValue().get(redisKey);
}
```

## 使用redis缓存用户信息
1. 创建redisKey

```java
private static final String PREFIX_USER = "user";

// 用户信息
public static String getUserKey(int userId){
  return PREFIX_USER + SPLIT + userId;
}
```

2. 类似于cache命中，我们优先从redis里取用户，取不到再初始化。并且，如果进行了更改头像等的操作，我们会把用户信息从缓存删了，用到时再加载

根据这个思想更新UserService:

```java
    //根据id查用户
    public User findUserById(int id){
        User user = getCache(id);
        if(user == null){
            user = initCache(id);
        }
        return user;
    }

    //激活邮件
    public int activation(int userId, String code){
      User user = userMapper.selectById(userId);
      if(user.getStatus() == 1){
        return ACTIVATION_REPEAT;
      } else if (user.getActivationCode().equals(code)) {
        userMapper.updateStatus(userId,1);
        clearCache(userId);  // 新增
        return ACTIVATION_SUCCESS;
      }else{
        return ACTIVATION_FAILURE;
      }
    }

    public int updateHeader(int userId, String headerUrl){
      int rows = userMapper.updateHeader(userId, headerUrl);
      clearCache(userId);
      return rows;
    }

    // 1.优先从redis取用户
    private User getCache(int userId){
        String redisKey = RedisKeyUtil.getUserKey(userId);
        return (User) redisTemplate.opsForValue().get(redisKey);
    }
    
    // 2.redis取不到，从mysql取
    private User initCache(int userId){
        User user = userMapper.selectById(userId);
        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.opsForValue().set(redisKey, user, 3600, TimeUnit.SECONDS);
        return user;
    }
    
    // 3.数据变更时缓存清除
    private void clearCache(int userId){
        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.delete(redisKey);
    }
```