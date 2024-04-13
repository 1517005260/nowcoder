# [会话管理](https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Overview#http_%E6%97%A0%E7%8A%B6%E6%80%81%EF%BC%8C%E4%BD%86%E5%B9%B6%E9%9D%9E%E6%97%A0%E4%BC%9A%E8%AF%9D)——用户如何进行与服务端的持续交互？

- HTTP的基本性质
  - 简单的
  - 可扩展的
  - 无状态的、有会话（session）的
- [Cookie](https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Cookies)——<b>服务器如何识别浏览器？</b>
  - 是服务器发送到浏览器，并保存在浏览器端的一小块数据
  - 浏览器下次访问该服务器时，会自动携带该块数据，将其发送给服务器，服务器接收到后会自动识别这个是哪个浏览器
  - 缺点：存在客户端的数据不安全；每次发送给服务器的时候会访问服务器，增加访问压力

![图解cookie](/imgs/cookie.png)

- Session（依赖于cookie）
  - 是JavaEE的标准，用于在服务端记录客户端信息
  - 数据存在服务器会更安全，但是也会增加服务端的内存压力（非必要不用session）
  - 相比于cookie来回传只能存字符串，session什么都能存

![图解session](/imgs/session.png)

- 单体服务器下session用的比较多，但是分布式服务器用的少了：负载均衡下session在服务器不互通
  - 解决策略1：“粘性session”（固定访问一台服务器）  ==> 问题：负载并不均衡
  - 解决策略2：“同步session”（广播机制） ==> 问题：同步会对性能产生影响，且传播后相当于服务器之间产生了耦合
  - 解决策略3：“共享session”（单独用个服务器存session） ==> 问题：万一存的服务器挂了怎么办？ 

![图解session2](/imgs/session2.png)

因此，现在基本非必要不session，一般都用cookie，敏感数据存数据库里，数据库之间互通技术比较成熟，缺点是关系型数据库性能不佳。
<br>
所以，可以把敏感数据存Redis等NoSQL数据库（推荐）

## 例子

1. 在AlphaController下新增

```java
//cookie示例
    @RequestMapping(path = "/cookie/set",method = RequestMethod.GET)
    @ResponseBody
    public String setCookie(HttpServletResponse response){
        Cookie cookie = new Cookie("code", CommunityUtil.genUUID());  //给cookie命名code
        //设置生效路径，浏览器只有访问这个路径或其子路径携带cookie才有效
        cookie.setPath("/community/alpha");
        cookie.setMaxAge(60*10);  // 60s*10，即有效期10分钟
        response.addCookie(cookie);
        
        return "set cookie";
    }
    @RequestMapping(path = "/cookie/get",method = RequestMethod.GET)
    @ResponseBody
    public String getCookie(){
      return "get Cookie";
    }
```

直接访问/alpha/cookie/set，f12查看

![cookie测试](/imgs/testCookie.png)

![cookie测试](/imgs/testCookie1.png)

获取cookie：
```java
@RequestMapping(path = "/cookie/get",method = RequestMethod.GET)
@ResponseBody
public String getCookie(@CookieValue("code") String code){
    System.out.println(code);
    return "get Cookie";
}
```

2. 也是在alphaController下

```java
//session示例
    @RequestMapping(path = "/session/set",method = RequestMethod.GET)
    @ResponseBody
    public String setSession(HttpSession session){
        session.setAttribute("id", 1);
        session.setAttribute("name", "Test");
        return "set session";
    }
```

![session示例](/imgs/sessionTest.png)

获取session

```java
@RequestMapping(path = "/session/get",method = RequestMethod.GET)
@ResponseBody
public String getSession(HttpSession session){
    System.out.println(session.getAttribute("id"));
    System.out.println(session.getAttribute("name"));
    return "get session";
}
```