package com.nowcoder.community.controller;

import com.nowcoder.community.annotation.LoginRequired;
import com.nowcoder.community.entity.*;
import com.nowcoder.community.event.EventProducer;
import com.nowcoder.community.service.*;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.HostHolder;
import com.nowcoder.community.util.RedisKeyUtil;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping(path = "/user")
public class UserController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Value("${community.path.upload}")
    private String uploadPath;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FollowService followService;

    @Value("${qiniu.key.access}")
    private String accessKey;

    @Value("${qiniu.key.secret}")
    private String secretKey;

    @Value("${qiniu.bucket.header.name}")
    private String headerBucketName;

    @Value("${qiniu.bucket.header.url}")
    private String headerBucketUrl;

    @Autowired
    private EventProducer eventProducer;

    @Autowired
    private CommentService commentService;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private RedisTemplate redisTemplate;

    // @用户用，获取用户的id
    @RequestMapping(path = "/id", method = RequestMethod.GET)
    @ResponseBody
    public String getUserId(@RequestParam String username){
        if(username == null){
            return CommunityUtil.getJSONString(1,"用户名为空！");
        }
        User user = userService.findUserByName(username);
        if(user== null){
            return CommunityUtil.getJSONString(1,"用户不存在！");
        }
        Map<String, Object> map = new HashMap<>();
        map.put(username, user.getId());
        return CommunityUtil.getJSONString(0, "已找到用户",map);
    }

    @LoginRequired
    @RequestMapping(path = "/setting", method = RequestMethod.GET)
    public String getSettingPage(Model model){
        // 上传头像
        String fileName = CommunityUtil.genUUID();
        // 设置响应信息
        StringMap policy = new StringMap();
        policy.put("returnBody", CommunityUtil.getJSONString(0));
        // 生成上传七牛云的凭证
        Auth auth = Auth.create(accessKey, secretKey);
        String token = auth.uploadToken(headerBucketName, fileName, 3600, policy);

        model.addAttribute("uploadToken", token);
        model.addAttribute("fileName", fileName);
        return "/site/setting";
    }

    // 更新头像路径
    @RequestMapping(path = "/header/url", method = RequestMethod.POST)
    @ResponseBody
    public String updateHeaderUrl(String fileName){
        if(fileName == null){
            return CommunityUtil.getJSONString(1, "文件名为空！");
        }
        String url = headerBucketUrl + "/" + fileName;
        userService.updateHeader(hostHolder.getUser().getId(), url);

        Event event = new Event()
                .setTopic(TOPIC_UPDATE)
                .setUserId(hostHolder.getUser().getId());
        eventProducer.fireEvent(event);

        return CommunityUtil.getJSONString(0);
    }

    @LoginRequired
    @RequestMapping(path = "/updatePassword", method = RequestMethod.POST)
    public String updatePassword(String oldPassword, String newPassword1, String newPassword2,Model model){
        User user = hostHolder.getUser();
        Map<String, Object> map = userService.updatePassword(user.getId(), oldPassword, newPassword1, newPassword2);
        if (map == null || map.isEmpty()) {
            return "redirect:/logout";
        } else {
            model.addAttribute("oldPasswordMsg", map.get("oldPasswordMsg"));
            model.addAttribute("newPasswordMsg", map.get("newPasswordMsg"));
            return "/site/setting";
        }
    }

    @LoginRequired
    @RequestMapping(path = "/updateSaying", method = RequestMethod.POST)
    public String updateSaying(String saying, Model model) {
        User user = hostHolder.getUser();
        Map<String, Object> map = userService.updateUserSaying(user.getId(), saying);
        if (map == null || map.isEmpty()) {
            Event event = new Event()
                    .setTopic(TOPIC_UPDATE)
                    .setUserId(user.getId());
            eventProducer.fireEvent(event);
            return "redirect:/user/profile/" + user.getId();
        } else {
            model.addAttribute("errorMsg", map.get("errorMsg"));
            return "/site/setting";
        }
    }

    @LoginRequired
    @RequestMapping(path = "/updateUsername", method = RequestMethod.POST)
    public String updateUsername(String username,Model model){
        User user = hostHolder.getUser();
        Map<String, Object> map = userService.updateUsername(user.getId(), username);
        if (map == null || map.isEmpty()) {
            Event event = new Event()
                    .setTopic(TOPIC_UPDATE)
                    .setUserId(user.getId());
            eventProducer.fireEvent(event);
            return "redirect:/user/profile/" + user.getId();
        } else {
            model.addAttribute("errorMsg", map.get("errorMsg"));
            return "/site/setting";
        }
    }

    // 个人主页
    @RequestMapping(path = "/profile/{userId}", method = RequestMethod.GET)
    public String getProfilePage(@PathVariable("userId") int userId, Model model){
        User user = userService.findUserById(userId);
        if(user == null){
            throw new RuntimeException("该用户不存在！");
        }
        // 用户
        model.addAttribute("user", user);
        // 获赞
        int likeCount = likeService.findUserLikeCount(userId);
        model.addAttribute("likeCount", likeCount);

        // 关注了
        long followeeCount = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        model.addAttribute("followeeCount",followeeCount);

        // 粉丝
        long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, userId);
        model.addAttribute("followerCount",followerCount);

        // 是否关注
        boolean hasFollowed = false;
        if(hostHolder.getUser() != null){
            hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
        }
        model.addAttribute("hasFollowed", hasFollowed);

        return "/site/profile";
    }

    // 我的帖子、我的回复
    @RequestMapping(path = "/mypost/{userId}", method = RequestMethod.GET)
    public String getMyPost(@PathVariable("userId") int userId, Page page, Model model) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在！");
        }
        model.addAttribute("user", user);

        // 分页信息
        page.setLimit(10);
        page.setPath("/user/mypost/" + userId);
        page.setRows(discussPostService.findDiscussPostRows(userId));

        // 帖子列表
        List<DiscussPost> discussList = discussPostService
                .findDiscussPosts(userId, page.getOffset(), page.getLimit(), 0);
        List<Map<String, Object>> discussVOList = new ArrayList<>();
        if (discussList != null) {
            for (DiscussPost post : discussList) {
                Map<String, Object> map = new HashMap<>();
                map.put("post", post);
                map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
                String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
                map.put("postReadCount", redisTemplate.opsForValue().get(redisKey));
                discussVOList.add(map);
            }
        }
        model.addAttribute("discussPosts", discussVOList);

        return "/site/my-post";
    }

    @RequestMapping(path = "/myreply/{userId}", method = RequestMethod.GET)
    public String getMyReply(@PathVariable("userId") int userId, Page page, Model model) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在！");
        }
        model.addAttribute("user", user);

        // 分页信息
        page.setLimit(10);
        page.setPath("/user/myreply/" + userId);
        page.setRows(commentService.findUserCount(userId));

        // 回复列表
        List<Comment> commentList = commentService.findUserComments(userId, page.getOffset(), page.getLimit());
        List<Map<String, Object>> commentVOList = new ArrayList<>();
        if (commentList != null) {
            for (Comment comment : commentList) {
                Map<String, Object> map = new HashMap<>();
                map.put("comment", comment);
                DiscussPost post = discussPostService.findDiscussPostById(comment.getEntityId());
                map.put("discussPost", post);
                commentVOList.add(map);
            }
        }
        model.addAttribute("comments", commentVOList);

        return "/site/my-reply";
    }

    @RequestMapping(path = "/mylikes/{userId}", method = RequestMethod.GET)
    public String getMyLikes(@PathVariable("userId") int userId, Page page, Model model) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在！");
        }
        model.addAttribute("user", user);

        // 获取用户点赞的帖子列表
        List<DiscussPost> discussList = likeService.findUserLikePosts(userId);

        // 分页信息
        page.setLimit(10);
        page.setPath("/user/mylikes/" + userId);
        page.setRows(discussList.size());

        List<Map<String, Object>> discussVOList = new ArrayList<>();
        if (discussList != null) {
            for (DiscussPost post : discussList) {
                Map<String, Object> map = new HashMap<>();
                map.put("post", post);
                map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
                String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
                map.put("postReadCount", redisTemplate.opsForValue().get(redisKey));
                discussVOList.add(map);
            }
        }
        model.addAttribute("discussPosts", discussVOList);

        return "/site/my-likes";
    }

    @RequestMapping(path = "/updatetype", method = RequestMethod.POST)
    @ResponseBody
    public String updateUserType(int oldType, int newType, int userId){
        User user = hostHolder.getUser();
        if(user == null){
            return CommunityUtil.getJSONString(1, "您还未登录！");
        }
        if(oldType == 1){
            // 已经是管理员了
            return CommunityUtil.getJSONString(1, "无法修改管理员的权限！");
        }

        userService.updateUserType(userId, newType);
        Event event = new Event()
                .setUserId(userId)
                .setTopic(TOPIC_UPDATE);
        eventProducer.fireEvent(event);
        return CommunityUtil.getJSONString(0, "修改用户权限成功！");
    }
}