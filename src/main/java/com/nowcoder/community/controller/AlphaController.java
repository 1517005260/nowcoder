package com.nowcoder.community.controller;

import com.nowcoder.community.service.AlphaService;
import com.nowcoder.community.util.CommunityUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@Controller
@RequestMapping("/alpha")
public class AlphaController {

    @Autowired
    private AlphaService alphaService;

    @RequestMapping("/hello")
    @ResponseBody
    public String sayhello(){
        return "Hello World!";
    }

    //被浏览器访问需要加注解
    @RequestMapping("/data")
    @ResponseBody
    public String getData(){
        return alphaService.find();
    }

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

    //POST（提交）
    @RequestMapping(path = "/student",method = RequestMethod.POST)
    @ResponseBody
    public String saveStudent(String name, String age){  //名称对应可以不要注解
        System.out.println(name + ":" + age );
        return "保存成功！";
    }

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

    @RequestMapping(path = "/school",method = RequestMethod.GET)
    public String getSchool(Model model){  //String这里代表返回view.html的路径
        model.addAttribute("name","北京大学");
        model.addAttribute("age",80);
        return "demo/view";
    }

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

    //cookie示例
    @RequestMapping(path = "/cookie/set",method = RequestMethod.GET)
    @ResponseBody
    public String setCookie(HttpServletResponse response){
        Cookie cookie = new Cookie("code", CommunityUtil.genUUID());
        //设置生效路径，浏览器只有访问这个路径或其子路径携带cookie才有效
        cookie.setPath("/community/alpha");
        cookie.setMaxAge(60*10);  // 60s*10，即有效期10分钟
        response.addCookie(cookie);

        return "set cookie";
    }

    @RequestMapping(path = "/cookie/get",method = RequestMethod.GET)
    @ResponseBody
    public String getCookie(@CookieValue("code") String code){
        System.out.println(code);
        return "get Cookie";
    }

    //session示例
    @RequestMapping(path = "/session/set",method = RequestMethod.GET)
    @ResponseBody
    public String setSession(HttpSession session){
        session.setAttribute("id", 1);
        session.setAttribute("name", "Test");
        return "set session";
    }

    @RequestMapping(path = "/session/get",method = RequestMethod.GET)
    @ResponseBody
    public String getSession(HttpSession session){
        System.out.println(session.getAttribute("id"));
        System.out.println(session.getAttribute("name"));
        return "get session";
    }
}
