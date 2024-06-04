package com.nowcoder.community.service;

import com.nowcoder.community.dao.UserMapper;
import com.nowcoder.community.entity.LoginTicket;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class UserService implements CommunityConstant {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MailClient mailClient;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private SensitiveFilter sensitiveFilter;

    @Value("${community.path.domain}")
    private String domain;  // 注入主域名，即“https://......”

    @Value("${server.servlet.context-path}")
    private String contextPath;  // 注入项目名，即“/community”

    @Autowired
    private RedisTemplate redisTemplate;

    //根据id查用户
    public User findUserById(int id){
        User user = getCache(id);
        if(user == null){
            user = initCache(id);
        }
        return user;
    }

    public Map<String, Object> register(User user){
        Map<String, Object> map = new HashMap<>();

        // 对空值判断处理
        if(user == null){  // 程序的错误
            throw new IllegalArgumentException("参数不能为空！");
        }
        if(StringUtils.isBlank(user.getUsername())){  //业务的漏洞
            map.put("usernameMsg", "账号不能为空！");
            return map;
        }
        if(user.getUsername().contains(" ")){
            map.put("usernameMsg", "账号不能包含空格！");
            return map;
        }
        if(StringUtils.isBlank(user.getPassword())){
            map.put("passwordMsg", "密码不能为空！");
            return map;
        }
        if(user.getPassword().length()<8){
            map.put("passwordMsg", "密码不能少于8位！");
            return map;
        }
        if(StringUtils.isBlank(user.getEmail())){
            map.put("emailMsg", "邮箱不能为空！");
            return map;
        }

        // 验证是否已经注册
        User u = userMapper.selectByName(user.getUsername());
        if(u != null){
            map.put("usernameMsg", "该账号已存在！");
            return map;
        }
        u = userMapper.selectByEmail(user.getEmail());
        if(u != null){
            map.put("emailMsg", "该邮箱已被使用！");
            return map;
        }

        // 注册，即存入数据库
        user.setSalt(CommunityUtil.genUUID().substring(0,5));  // 5位salt
        user.setPassword(CommunityUtil.md5(user.getPassword() + user.getSalt()));
        user.setType(0);  // 普通用户
        user.setStatus(0);  // 未激活
        user.setActivationCode(CommunityUtil.genUUID());  // 给个激活码
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png",
                new Random().nextInt(1000)));  //默认头像，由牛客网提供，格式：http://images.nowcoder.com/head/1t.png
        user.setCreateTime(new Date());
        userMapper.insertUser(user);  // 插入并生成id

        // 发送激活邮件，流程和上节课一样
        Context context = new Context();
        context.setVariable("email",user.getEmail());
        //激活路径：https://{domain}/community/activation/{userid}/{activate_code}
        String url = domain + contextPath + "/activation/" + user.getId() + "/" +user.getActivationCode();
        context.setVariable("url", url);
        String content = templateEngine.process("/mail/activation",context);
        mailClient.sendMail(user.getEmail(), "邮箱激活账号", content);

        return map;
    }

    //激活邮件
    public int activation(int userId, String code){
        User user = userMapper.selectById(userId);
        if(user.getStatus() == 1){
            return ACTIVATION_REPEAT;
        } else if (user.getActivationCode().equals(code)) {
            userMapper.updateStatus(userId,1);
            clearCache(userId);
            return ACTIVATION_SUCCESS;
        }else{
            return ACTIVATION_FAILURE;
        }
    }

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

        // 存入redis
        String redisKey = RedisKeyUtil.getTicketKey(loginTicket.getTicket());
        redisTemplate.opsForValue().set(redisKey, loginTicket);  // redis自动把这个对象序列化为json字符串

        map.put("ticket", loginTicket.getTicket());
        return map;
    }

    public void logout(String ticket){
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(redisKey);
        loginTicket.setStatus(1); //删除态
        redisTemplate.opsForValue().set(redisKey, loginTicket);
    }

    public LoginTicket findLoginTicket(String ticket){
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        return (LoginTicket) redisTemplate.opsForValue().get(redisKey);
    }

    public int updateHeader(int userId, String headerUrl){
        int rows = userMapper.updateHeader(userId, headerUrl);
        clearCache(userId);
        return rows;
    }

    // 重置密码 - forget
    public Map<String, Object> resetPassword(String email, String password) {
        Map<String, Object> map = new HashMap<>();
        if (StringUtils.isBlank(email)) {
            map.put("emailMsg", "邮箱不能为空!");
            return map;
        }
        if (StringUtils.isBlank(password)) {
            map.put("passwordMsg", "密码不能为空!");
            return map;
        }
        if(password.length()<8){
            map.put("passwordMsg", "密码不能少于8位！");
            return map;
        }
        User user = userMapper.selectByEmail(email);
        if (user == null) {
            map.put("emailMsg", "该邮箱不存在!");
            return map;
        }
        password = CommunityUtil.md5(password + user.getSalt());
        if (user.getPassword().equals(password)) {
            map.put("passwordMsg", "新密码不能和原密码相同!");
            return map;
        }
        userMapper.updatePassword(user.getId(), password);
        clearCache(user.getId());
        return map;
    }

    // 更新密码 - not forget
    public Map<String, Object> updatePassword(int userId, String oldPassword, String newPassword1, String newPassword2){
        Map<String, Object> map = new HashMap<>();
        User user = userMapper.selectById(userId);
        String password = CommunityUtil.md5(oldPassword + user.getSalt());

        if (StringUtils.isBlank(oldPassword)) {
            map.put("oldPasswordMsg", "原密码不能为空！");
            return map;
        }
        if (StringUtils.isBlank(newPassword1) || StringUtils.isBlank(newPassword2)) {
            map.put("newPasswordMsg", "新密码不能为空！");
            return map;
        }
        if(newPassword1.length()<8){
            map.put("newPasswordMsg", "新密码不能少于8位！");
            return map;
        }
        if(!newPassword1.equals(newPassword2)){
            map.put("newPasswordMsg", "两次密码不一致！");
            return map;
        }
        if (!password.equals(user.getPassword())) {
            map.put("oldPasswordMsg", "原密码不正确！");
            return map;
        }
        userMapper.updatePassword(userId, CommunityUtil.md5(newPassword1+ user.getSalt()));
        clearCache(userId);
        return map;
    }

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
        if(username.contains(" ")){
            map.put("errorMsg", "新用户名不能包含空格！");
            return map;
        }
        if(username.equals(oldUsername)){
            map.put("errorMsg", "新用户名和旧用户名不能重复！");
            return map;
        }
        // 检查新用户名是否已存在
        User existingUser = userMapper.selectByName(username);
        if (existingUser != null) {
            map.put("errorMsg", "新用户名已存在，请选择其他用户名！");
            return map;
        }
        userMapper.updateUsername(userId, username);
        clearCache(userId);
        return map;
    }

    public void updateUserType(int userId, int type){
        userMapper.updateType(userId, type);
        clearCache(userId);
    }

    public Map<String, Object> updateUserSaying(int userId, String saying){
        Map<String, Object> map = new HashMap<>();
        saying = sensitiveFilter.filter(saying);
        if(saying == null){
            map.put("errorMsg", "新简介不能为空！");
            return map;
        }
        if(saying.length() > 255){
            map.put("errorMsg", "简介过长！");
            return map;
        }
        userMapper.updateSaying(userId, saying);
        clearCache(userId);
        return map;
    }

    // 用户名查用户
    public User findUserByName(String username){
        return userMapper.selectByName(username);
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

    // 获取用户权限
    public Collection<? extends GrantedAuthority> getAuthorities(int userId){
        User user = this.findUserById(userId);

        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new GrantedAuthority() {
            @Override
            public String getAuthority() {
                switch (user.getType()){
                    case 1:
                        return AUTHORITY_ADMIN;
                    case 2:
                        return AUTHORITY_MODERATOR;
                    default:
                        return AUTHORITY_USER;
                }
            }
        });

        return list;
    }
}