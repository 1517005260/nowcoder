package com.nowcoder.community.controller;

import com.google.code.kaptcha.Producer;
import com.nowcoder.community.entity.Event;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.event.EventProducer;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.RedisKeyUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
public class LoginController implements CommunityConstant {
    @Autowired
    private UserService userService;

    @Autowired
    private Producer kaptchaProducer;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SecurityContextLogoutHandler securityContextLogoutHandler;

    @Autowired
    private EventProducer eventProducer;

    private static final Logger logger =  LoggerFactory.getLogger(LoginController.class);

    @RequestMapping(path = "/register", method = RequestMethod.GET)
    public String getRegisterPage(){
        return "site/register";
    }

    @RequestMapping(path = "/login", method = RequestMethod.GET)
    public String getLoginPage(){
        return "site/login";
    }

    @RequestMapping(path = "/register", method = RequestMethod.POST)
    public String register(Model model, User user){
        Map<String, Object> map = userService.register(user);

        if(map == null || map.isEmpty()){
            // 没有错误信息，注册成功，直接跳到首页
            model.addAttribute("msg", "注册成功，已经向您的邮箱发送了一封激活邮件，请尽快激活！");
            model.addAttribute("target", "/index");
            return "site/operate-result";
        }else {
            model.addAttribute("usernameMsg", map.get("usernameMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            model.addAttribute("emailMsg", map.get("emailMsg"));
            return "site/register";
        }
    }

    //激活路径：https://{domain}/community/activation/{userid}/{activate_code}
    @RequestMapping(path = "/activation/{userId}/{code}", method = RequestMethod.GET)
    public String activation(Model model, @PathVariable("userId") int userId, @PathVariable("code") String code){
        int result = userService.activation(userId,code);
        if(result == ACTIVATION_SUCCESS){
            model.addAttribute("msg", "激活成功，您的账号已经可以正常使用了！");
            model.addAttribute("target", "/login");

            Event event = new Event()
                    .setTopic(TOPIC_REGISTER)
                    .setUserId(userId);
            eventProducer.fireEvent(event);
        } else if (result == ACTIVATION_REPEAT) {
            model.addAttribute("msg", "重复激活！");
            model.addAttribute("target", "/index");
        }else{
            model.addAttribute("msg", "激活失败！激活码不正确！");
            model.addAttribute("target", "/index");
        }
        return "site/operate-result";
    }

    //验证码图片
    @RequestMapping(path = "/kaptcha", method = RequestMethod.GET)
    public void getKaptcha(HttpServletResponse response) {
        // 生成验证码
        String text = kaptchaProducer.createText();
        BufferedImage image = kaptchaProducer.createImage(text);

        // 验证码归属
        String kaptchaOwner = CommunityUtil.genUUID();
        Cookie cookie = new Cookie("kaptchaOwner", kaptchaOwner);
        cookie.setMaxAge(600); // 秒
        cookie.setPath(contextPath); // cookie在整个项目下有效
        response.addCookie(cookie);  // 向用户发送cookie

        // 验证码存入redis
        String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
        redisTemplate.opsForValue().set(redisKey, text, 600, TimeUnit.SECONDS);


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
    public String login(String username, String password, String code,
                        @RequestParam(value = "rememberMe", required = false)boolean rememberMe,  //最后两个是”验证码“ 和 ”记住我“
                        Model model, HttpServletResponse response, @CookieValue("kaptchaOwner")String kaptchaOwner){   //Model返回数据，session存code
        //验证码判断
        String kaptcha = null;
        if(StringUtils.isNotBlank(kaptchaOwner)){  // 如果还未过期
            String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
            kaptcha = (String) redisTemplate.opsForValue().get(redisKey);
        }
        if(StringUtils.isBlank(kaptcha) || StringUtils.isBlank(code) || !kaptcha.equalsIgnoreCase(code)){  //验证码不区分大小写
            model.addAttribute("codeMsg", "验证码不正确");
            return "site/login";
        }

        //账号密码，交给service判断
        //定义常量区分“记住我”,见常量工具接口
        int expiredSeconds = rememberMe ? REMEMBER_EXPIRED_SECONDS : DEFAULT_EXPIRED_SECONDS;
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
            return "site/login";
        }
    }

    @RequestMapping(path = "/logout", method = RequestMethod.GET)
    public String logout(@CookieValue("ticket") String ticket, HttpServletRequest request,
                         HttpServletResponse response, Authentication authentication) {
        userService.logout(ticket);
        // 重构，使用Spring Security
        // 使用SecurityContextLogoutHandler清理SecurityContext
        securityContextLogoutHandler.logout(request, response, authentication);
        return "redirect:/login";
    }

    @RequestMapping(path = "/forget" ,method = RequestMethod.GET)
    public String getForgetPage(){
        return "site/forget";
    }

    // 邮箱验证码
    @RequestMapping(path = "/forget/code", method = RequestMethod.GET)
    @ResponseBody
    public String getForgetCode(String email, HttpSession session) {
        Map<String, Object> map = userService.getForgetCode(email);
        if (map.containsKey("verifyCode")) {
            // 保存验证码，注意这里要对不同的邮箱保存不同的验证码，防止换邮箱后验证码还是之前的
            session.setAttribute(email + "_verifyCode", map.get("verifyCode"));
            return CommunityUtil.getJSONString(0);
        } else {
            return CommunityUtil.getJSONString(1, (String) map.get("emailMsg"));
        }
    }

    @RequestMapping(path = "/forgetPassword", method = RequestMethod.POST)
    public String resetPassword(String email, String verifyCode, String password, Model model, HttpSession session) {
        // 检查验证码
        String code = (String) session.getAttribute(email + "_verifyCode");
        if (StringUtils.isBlank(verifyCode) || StringUtils.isBlank(code) || !code.equalsIgnoreCase(verifyCode)) {
            // 验证码错误，返回重置密码页面
            model.addAttribute("codeMsg", "验证码错误!");
            return "site/forget";
        }

        Map<String, Object> map = userService.resetPassword(email, password);
        if (map == null || map.isEmpty()) {
            model.addAttribute("msg", "重置密码成功，正在前往登录页面，请重新登录!");
            model.addAttribute("target", "/login");
            return "site/operate-result";
        } else {
            model.addAttribute("emailMsg", map.get("emailMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            return "site/forget";
        }
    }
}