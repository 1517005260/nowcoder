package com.nowcoder.community.controller;

import com.nowcoder.community.annotation.LoginRequired;
import com.nowcoder.community.dao.DiscussPostMapper;
import com.nowcoder.community.entity.*;
import com.nowcoder.community.event.EventProducer;
import com.nowcoder.community.service.*;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.HostHolder;
import com.nowcoder.community.util.RedisKeyUtil;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
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

    @Autowired
    private EventProducer eventProducer;

    @Autowired
    private CommentService commentService;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DiscussPostMapper discussPostMapper;

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
    public String getSettingPage() {
        return "site/setting";
    }

    @LoginRequired
    @RequestMapping(path = "/upload", method = RequestMethod.POST)
    public String uploadHeader(MultipartFile headerImage, Model model) {
        if (headerImage == null) {
            model.addAttribute("error", "上传的头像图片为空！");
            return "site/setting";
        }

        // 检查文件大小
        if (headerImage.getSize() > 500 * 1024) { // 500KB = 500 * 1024 bytes
            model.addAttribute("error", "上传的文件大小不能超过500KB！建议使用QQ截图缩小！");
            return "site/setting";
        }

        // 给上传的文件重命名
        String fileName = headerImage.getOriginalFilename();
        if (fileName == null || StringUtils.isBlank(fileName)) {
            model.addAttribute("error", "您还没有选择图片!");
            return "site/setting";
        }

        int index = fileName.lastIndexOf(".");
        if (index == -1) {
            model.addAttribute("error", "文件格式不正确!（只支持*.png/*.jpg/*.jpeg）");
            return "site/setting";
        }

        String suffix = fileName.substring(index);
        if (StringUtils.isBlank(suffix) || (!".png".equals(suffix) && !".jpg".equals(suffix) && !".jpeg".equals(suffix))) {
            model.addAttribute("error", "文件格式不正确!（只支持*.png/*.jpg/*.jpeg）");
            return "site/setting";
        }

        // 随机文件名
        fileName = CommunityUtil.genUUID() + suffix;

        // 存储文件
        File dist = new File(uploadPath + "/" + fileName); // 存放路径
        try {
            headerImage.transferTo(dist);
        } catch (IOException e) {
            logger.error("上传文件失败：" + e.getMessage());
            throw new RuntimeException("上传文件失败，服务器发生异常！", e);
        }

        // 更新用户头像（非服务器，而是web路径）
        // http://...../community/user/header/xxx.png
        User user = hostHolder.getUser();
        String headerUrl = domain + contextPath + "/user/header/" + fileName;
        userService.updateHeader(user.getId(), headerUrl);

        return "redirect:/index";
    }

    @RequestMapping(path = "/header/{fileName}", method = RequestMethod.GET)
    public void getHeader(@PathVariable("fileName") String fileName, HttpServletResponse response){
        // 服务器存放路径
        fileName = uploadPath + "/" + fileName;
        String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);

        //响应文件
        response.setContentType("image/" + suffix);
        try (
                OutputStream os = response.getOutputStream();
                FileInputStream fis = new FileInputStream(fileName);
        ){
            byte[] buffer = new byte[1024];
            int b = 0;
            while( (b = fis.read(buffer)) != -1){
                os.write(buffer, 0, b);
            }
        } catch (IOException e) {
            logger.error("读取头像失败：" + e.getMessage());
        }
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
            return "site/setting";
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
            model.addAttribute("SayingErrorMsg", map.get("SayingErrorMsg"));
            return "site/setting";
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
            return "site/setting";
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

        return "site/profile";
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
                discussVOList.add(map);
            }
        }
        model.addAttribute("discussPosts", discussVOList);

        return "site/my-post";
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

        return "site/my-reply";
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
                discussVOList.add(map);
            }
        }
        model.addAttribute("discussPosts", discussVOList);

        return "site/my-likes";
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