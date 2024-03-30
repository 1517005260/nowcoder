# Spring MVC 入门 —— 专用于web开发

## web预备知识
[MDN官网](https://developer.mozilla.org/zh-CN/)
### [HTTP](https://developer.mozilla.org/zh-CN/docs/Web/HTTP)超文本传输协议（HyperText Transfer Protocol）
- 图解：<br>
![图解http](/imgs/http.png)<br>
- 用于传输html等内容的应用层协议
- 规定了浏览器和服务器之间如何通信，以及通信时的数据格式
- [http流](https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Overview#http_%E6%B5%81)：http规定的浏览器和服务器通信的步骤
- [http报文](https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Overview#http_%E6%8A%A5%E6%96%87)：规定了传输数据的格式
- f12可以查看相关信息，如图：<br>
![图解f12](/imgs/f12.png)<br>
- 为什么会有这么多记录？因为浏览器请求后，服务器返回的是html。html中有其他的css和js等文件需要调用到了后再请求<br>
![图解请求流程](/imgs/request.png)

## Spring MVC
- 开发时对代码分层，解耦利于维护
1. 三层架构：表现层，业务层，数据访问层
2. MVC：<b>表现层</b>
- Model：模型层（数据）
- View：视图层
- Controller：控制层<br>
![图解MVC](/imgs/MVC.png)
<br>
<b>请求 -> controller -> 业务层 -> 数据层 -> model -> view -> 响应（返回html）</b><br>
3. 核心组件：
- 前端控制器（Front Controller）：<b>DispatcherServlet</b>，调用MVC
<br>
![图解DispatcherServlet](https://raw.githubusercontent.com/1517005260/nowcoder/master/imgs/DispatcherServlet.png)
<br>
![详细图解DispatcherServlet](https://raw.githubusercontent.com/1517005260/nowcoder/master/imgs/DispatcherServlet2.png)

### [Thymeleaf](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html)
- 模板引擎
  - 生成动态的html
  - 模板文件 + model -> 模板引擎 -> html
- Thymeleaf
  - 倡导自然模板，即以html文件为模板（大家基本都能看懂）
- 常用语法：标准表达式、判断循环、模板布局等

## 案例演示
1. 在`application.properties`配置Thymeleaf
- SpringBoot配置[详解](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#appendix.application-properties.core)，实质上就是给底层封装好的类的属性赋值
2. MVC代码：Controller，Model（自带），View，templates（html模板）
- controller接收请求和响应：
```java
    @RequestMapping("/http")
    //没有返回值是因为可以通过response向浏览器返回任何数据
    public void http(HttpServletRequest request, HttpServletResponse response){
        //简易处理请求：获取请求数据并输出
        System.out.println(request.getMethod()); //请求方式
        System.out.println(request.getServletPath());  //请求路径(第一行的数据)
        Enumeration<String> enumeration = request.getHeaderNames(); //请求行(key-value) （请求头）
        while(enumeration.hasMoreElements()){
            String name = enumeration.nextElement();
            String value = request.getHeader(name);
            System.out.println(name + ":" + value);
        }
        System.out.println(request.getParameter("code"));  //请求体

        //返回响应数据
        response.setContentType("text/html;charset=utf-8");  //指定返回类型
        
        //获取输出流
        //java语法：用小括号捕捉的异常在执行完毕后会自动finally消除pw
        try (PrintWriter  pw = response.getWriter())
        {
            pw.write("<h1>牛客论坛</h1>");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
```
控制台输出（null是因为code没有传参）：
```bash
GET
/alpha/http
host:localhost:8080
user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0
accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8
accept-language:zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2
accept-encoding:gzip, deflate, br
connection:keep-alive
cookie:Pycharm-b203a0ea=6eb9159f-4665-4fe9-a26e-4e48f54ebbf2
upgrade-insecure-requests:1
sec-fetch-dest:document
sec-fetch-mode:navigate
sec-fetch-site:cross-site
pragma:no-cache
cache-control:no-cache
null
销毁AlphaService
```

3. 一种更简便的方式——处理GET和POST
- 还是在controller中
- GET
```java
    //GET请求（查）
    //假设查询所有的学生，分页，路径为：/students?cur=1&limit=20
    @RequestMapping(path = "/students", method = RequestMethod.GET)  //指定处理的请求
    @ResponseBody
    public String getStudents(
            @RequestParam(name = "cur" , defaultValue = "1",required = false ) int cur,  //可以通过注解指定参数名，默认值和是否强制需要
            @RequestParam(name = "limit" , defaultValue = "10",required = false ) int limit){
        System.out.println(cur);
        System.out.println(limit);
        return "100";
    }

    //或者不用?传参：
    //根据学生id查询一个学生：/student/123
    @RequestMapping(path = "/student/{id}", method = RequestMethod.GET)
    @ResponseBody
    public String getStudent(@PathVariable("id") int id){
        System.out.println(id);
        return "student 123";
    }
```

- POST需要先有表单，于是我们进入static修改，路径为`http://localhost:8080/community/html/student.html`
```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport"
        content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
  <meta http-equiv="X-UA-Compatible" content="ie=edge">
  <title>学生表单</title>
</head>
<body>
    <!--注意action的路径要和controller一样，尤其是斜杠-->
    <form method="post" action="/community/alpha/student">
  <p>
    姓名：<input type="text" name="name">
  </p>
  <p>
    年龄：<input type="text" name="age">
  </p>
  <p>
    <input type="submit" value="保存">
  </p>
</form>
</body>
</html>
```

后端controller代码：
```java
    //POST（提交）
    @RequestMapping(path = "/student",method = RequestMethod.POST)
    @ResponseBody
    public String saveStudent(String name, String age){  //名称对应可以不要注解
        System.out.println(name + ":" + age );
        return "保存成功！";
    }
```

4. 不响应字符串，而是响应html：
- 首先新建模板view.html，并声明不是普通的html而是模板
```html
<!DOCTYPE html>
<!--指明模板的来源-->
<html lang="en" xmlns:th="https://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Teacher</title>
</head>
<body>
    <!--数据让后端给-->
    <p th:text="${name}"></p>
    <p th:text="${age}"></p>
</body>
</html>
```
- 后端代码：
```java
    //响应html数据
    //不写@ResponseBody默认返回html
    @RequestMapping(path = "/teacher", method = RequestMethod.GET)
    public ModelAndView getTeacher(){
        ModelAndView mav = new ModelAndView();
        mav.addObject("name", "张三");
        mav.addObject("age",30);
        mav.setViewName("/demo/view");  //templates子目录:templates/demo/view.html
        return mav;
    }
```
- 或者（没有上种直观，但简洁）：
```java
    @RequestMapping(path = "/school",method = RequestMethod.GET)
    public String getSchool(Model model){  //String这里代表返回view.html的路径
        model.addAttribute("name","北京大学");
        model.addAttribute("age",80);
        return "demo/view";
    }
```

5. 响应json
- 一般用于异步请求
  - 比如注册B站时，要判断昵称是否占用。而占用肯定向服务器查询了，但是<b>页面没有刷新</b>
```java
//响应json
//例子：把java对象 -> json -> js对象
@RequestMapping(path = "/employee",method = RequestMethod.GET)
@ResponseBody
public Map<String, Object> getEmp(){
  Map<String,Object> emp = new HashMap<>();
  emp.put("name","张三");
  emp.put("age",20);
  emp.put("salary",8000.00);
  return emp;
}
//多名员工
@RequestMapping(path = "/employees",method = RequestMethod.GET)
@ResponseBody
public List<Map<String, Object>> getEmps(){
  List<Map<String,Object>> list = new ArrayList<>();
  Map<String,Object> emp = new HashMap<>();
  emp.put("name","张三");
  emp.put("age",20);
  emp.put("salary",8000.00);
  list.add(emp);

  emp = new HashMap<>();
  emp.put("name","张四");
  emp.put("age",20);
  emp.put("salary",8000.00);
  list.add(emp);

  return list;
}
```