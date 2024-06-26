package com.nowcoder.community.controller;

import com.nowcoder.community.dao.DiscussPostMapper;
import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.Page;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.service.LikeService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.HostHolder;
import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

//controller映射路径可省，这样直接访问的就是方法
@Controller
public class HomeController implements CommunityConstant {

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private UserService userService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DiscussPostMapper discussPostMapper;

    @RequestMapping(path = "/", method = RequestMethod.GET)
    public String root(){
        return "forward:/index";
    }

    @RequestMapping(path = "/index", method = RequestMethod.GET)
    public String getIndexPage(Model model, Page page,
                               @RequestParam(name = "orderMode", defaultValue = "1")int orderMode){ // 默认热帖排序

        // orderMode 0新 1热 2关注 3删除

        //方法调用前，SpringMVC会自动实例化Model和Page，并将Page注入Model
        //所以不用model.addAttribute(Page),直接在thymeleaf可以访问Page的数据
        User user = hostHolder.getUser();
        if (user != null) {
            boolean hasUnreadPosts = discussPostService.hasUnreadPosts(user.getId());
            model.addAttribute("hasUnreadPosts", hasUnreadPosts);
        }

        List<DiscussPost> list = new ArrayList<>();
        if (orderMode == 2) {
            if (user == null) {
                return "site/login";
            }
            int cnt = discussPostService.findFolloweePostCount(user.getId());
            page.setRows(cnt);
            list = discussPostService.findFolloweePosts(user.getId(), page.getOffset(), page.getLimit());
            // 清除未读帖子标记
            discussPostService.clearUnreadPosts(user.getId());
        }else if(orderMode == 3){
            if(user == null || user.getType() != 1){
                return "error/404";
            }
            list = discussPostService.findDeletedPosts();
        }
        else {
            page.setRows(discussPostService.findDiscussPostRows(0));
            // 默认是第一页，前10个帖子
            list = discussPostService.findDiscussPosts(0, page.getOffset(), page.getLimit(), orderMode);
        }
        page.setPath("/index?orderMode=" + orderMode);

        // 将前10个帖子和对应的user对象封装
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if(list !=null){
            for(DiscussPost post:list){
                Map<String,Object> map = new HashMap<>();
                map.put("post" , post);
                user = userService.findUserById(post.getUserId());
                map.put("user", user);

                long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
                map.put("likeCount",likeCount);

                String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
                Object readCountObj = redisTemplate.opsForValue().get(redisKey);
                Integer readCount = null;
                if (readCountObj != null) {
                    readCount = (Integer) readCountObj;
                } else {
                    DiscussPost dbPost = discussPostMapper.selectDiscussPostById(post.getId());
                    if (dbPost != null) {
                        readCount = dbPost.getReadCount();
                        redisTemplate.opsForValue().set(redisKey, readCount);
                    } else {
                        readCount = 0;
                    }
                }
                map.put("postReadCount", readCount);

                discussPosts.add(map);
            }
        }
        // 处理完的数据填充给前端页面
        model.addAttribute("discussPosts", discussPosts);
        model.addAttribute("orderMode", orderMode);
        return "index";
    }

    // 重定向到错误页面
    @RequestMapping(path = "/error", method = RequestMethod.GET)
    public String getErrorPage(){
        return "error/500";
    }

    // 权限不足页面
    @RequestMapping(path = "/denied", method = RequestMethod.GET)
    public String getDeniedPage() {
        return "error/404";
    }
}