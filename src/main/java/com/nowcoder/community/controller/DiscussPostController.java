package com.nowcoder.community.controller;

import com.nowcoder.community.entity.*;
import com.nowcoder.community.event.EventProducer;
import com.nowcoder.community.service.CommentService;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.service.LikeService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.HostHolder;
import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.*;

@Controller
@RequestMapping("/discuss")
public class DiscussPostController implements CommunityConstant {
    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private UserService userService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private EventProducer eventProducer;

    @Autowired
    private RedisTemplate redisTemplate;

    @RequestMapping(path = "/publish", method = RequestMethod.GET)
    public String getPublishPage(Model model){
        return "/site/publish-posts";
    }

    //处理增加帖子异步请求
    @RequestMapping(path = "/add", method = RequestMethod.POST)
    @ResponseBody
    public String addDiscussPost(String title, String content){
        User user = hostHolder.getUser();
        if(user == null){
            return CommunityUtil.getJSONString(403, "你还没有登录哦!");  // 403表示没有权限
        }
        DiscussPost discussPost = new DiscussPost();
        discussPost.setUserId(user.getId());
        discussPost.setTitle(title);
        discussPost.setContent(content);
        discussPost.setCreateTime(new Date());
        discussPostService.addDiscussPost(discussPost);

        // 通知所有粉丝有新帖子
        discussPostService.notifyFollowersNewPost(user.getId(), discussPost.getId());

        // 发帖事件，存进es服务器
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(user.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(discussPost.getId());
        eventProducer.fireEvent(event);

        // 初始分数计算
        String redisKey = RedisKeyUtil.getPostScoreKey();
        redisTemplate.opsForSet().add(redisKey, discussPost.getId());

        return CommunityUtil.getJSONString(0, "发布成功！");
    }

    // 进入更改帖子的页面
    @RequestMapping(path = "/updatePost/{postId}", method = RequestMethod.GET)
    public String getUpdatePage(@PathVariable("postId") int postId, Model model){
        DiscussPost post = discussPostService.findDiscussPostById(postId);
        User user = hostHolder.getUser();
        if (post == null || post.getUserId() != user.getId() || post.getStatus() == 2) {
            return "/error/404";  // 只能作者访问， 被删除的帖子无法访问
        }
        model.addAttribute("title", post.getTitle());
        model.addAttribute("content", post.getContent());
        model.addAttribute("id", postId);

        return "/site/update-posts";
    }

    // 更改帖子请求
    @RequestMapping(path = "/update/{postId}", method = RequestMethod.POST)
    @ResponseBody
    public String UpdateDiscussPost(@PathVariable("postId") int postId,String title, String content){
        User user = hostHolder.getUser();
        if(user == null){
            return CommunityUtil.getJSONString(403, "你还没有登录哦!");
        }
        DiscussPost post = discussPostService.findDiscussPostById(postId);
        if (post == null || post.getUserId() != user.getId()) {
            return CommunityUtil.getJSONString(403, "你没有权限修改此帖子!");
        }
        post.setTitle(title);
        post.setContent(content);
        post.setCreateTime(new Date());
        discussPostService.updatePost(post);

        // 改帖事件，存进es服务器
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(user.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(post.getId());
        eventProducer.fireEvent(event);

        // 初始分数计算
        String redisKey = RedisKeyUtil.getPostScoreKey();
        redisTemplate.opsForSet().add(redisKey, post.getId());

        return CommunityUtil.getJSONString(0, "修改成功！");
    }

    //帖子详情
    @RequestMapping(path = "/detail/{discussPostId}", method = RequestMethod.GET)
    public String getDiscussPost(@PathVariable("discussPostId") int discussPostId, Model model, Page page
    ,@RequestParam(name = "orderMode", defaultValue = "0")int orderMode){
        //帖子
        DiscussPost discussPost = discussPostService.findDiscussPostById(discussPostId);
        User user = hostHolder.getUser();
        if(discussPost.getStatus() == 2 && (user == null || user.getType() != 1)){
            return "/error/404";  // 非管理员无法查看已删除帖子
        }
        String content = HtmlUtils.htmlUnescape(discussPost.getContent()); // 内容反转义，不然 markdown 格式无法显示
        discussPost.setContent(content);
        model.addAttribute("post", discussPost);

        //作者
        user = userService.findUserById(discussPost.getUserId());
        model.addAttribute("user", user);

        // 赞
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, discussPostId);
        model.addAttribute("likeCount", likeCount);
        int likeStatus = hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_POST, discussPostId);
        model.addAttribute("likeStatus", likeStatus);

        //评论分页信息
        page.setLimit(5);
        page.setPath("/discuss/detail/" + discussPostId);
        page.setRows(discussPost.getCommentCount());
        List<Comment> commentList = commentService.
                findCommentsByEntity(ENTITY_TYPE_POST, discussPost.getId(), page.getOffset(), page.getLimit(), orderMode);

        //找到评论的用户
        List<Map<String, Object>> commentVoList = new ArrayList<>();  // Vo = view objects 显示对象
        if(commentList != null){
            for(Comment comment : commentList){
                Map<String, Object> commentVo = new HashMap<>();
                // 评论
                commentVo.put("comment", comment);
                // 作者
                commentVo.put("user", userService.findUserById(comment.getUserId()));
                // 赞
                likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, comment.getId());
                commentVo.put("likeCount", likeCount);
                likeStatus = hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, comment.getId());
                commentVo.put("likeStatus", likeStatus);

                //评论的评论——回复
                 List<Comment> replyList = commentService.
                        findCommentsByEntity(ENTITY_TYPE_COMMENT,
                                comment.getId(), 0, Integer.MAX_VALUE,0);  // 回复就不需要分页了，就一页显示所有评论，且按回复顺序显示

                //找到回复的用户
                List<Map<String, Object>> replyVoList = new ArrayList<>();
                if(replyList != null){
                    for(Comment reply : replyList){
                        Map<String, Object> replyVo = new HashMap<>();
                        // 回复
                        replyVo.put("reply", reply);
                        // 作者
                        replyVo.put("user", userService.findUserById(reply.getUserId()));
                        // 赞
                        likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, reply.getId());
                        replyVo.put("likeCount", likeCount);
                        likeStatus = hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, reply.getId());
                        replyVo.put("likeStatus", likeStatus);

                        //回复的目标
                        User target = reply.getTargetId() == 0 ? null : userService.findUserById(reply.getTargetId());

                        replyVo.put("target", target);
                        replyVoList.add(replyVo);
                    }
                }
                commentVo.put("replys", replyVoList);

                int replycnt = commentService.findCommentCount(ENTITY_TYPE_COMMENT, comment.getId());
                commentVo.put("replyCount", replycnt);

                commentVoList.add(commentVo);
            }
        }

        model.addAttribute("comments", commentVoList);

        // 增加帖子访问量
        discussPostService.updatePostReadCount(discussPostId);
        String redisKey = RedisKeyUtil.getPostReadKey(discussPostId);
        if(redisKey != null){
            model.addAttribute("postReadCount", redisTemplate.opsForValue().get(redisKey));
        }else {
            model.addAttribute("postReadCount", discussPost.getReadCount());
        }

        model.addAttribute("orderMode", orderMode);

        return "/site/discuss-detail";
    }

    // 置顶
    @RequestMapping(path = "/top", method = RequestMethod.POST)
    @ResponseBody  // 异步请求
    public String setTop(int id){
        DiscussPost post = discussPostService.findDiscussPostById(id);
        int type = post.getType() == 1 ? 0 : 1;
        discussPostService.updateType(id, type);

        // 别忘了把最新的帖子状态同步给es
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);

        return CommunityUtil.getJSONString(0);
    }

    // 加精
    @RequestMapping(path = "/wonderful", method = RequestMethod.POST)
    @ResponseBody
    public String setWonderful(int id){
        DiscussPost post = discussPostService.findDiscussPostById(id);
        int status = post.getStatus() == 1 ? 0 : 1;
        discussPostService.updateStatus(id, status);

        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);

        // 加精分数计算
        String redisKey = RedisKeyUtil.getPostScoreKey();
        redisTemplate.opsForSet().add(redisKey, id);

        return CommunityUtil.getJSONString(0);
    }

    // 删除
    @RequestMapping(path = "/delete", method = RequestMethod.POST)
    @ResponseBody
    public String setDelete(int id){
        DiscussPost post = discussPostService.findDiscussPostById(id);
        int status = post.getStatus() == 2 ? 0 : 2;
        discussPostService.updateStatus(id, status);

        // 这时同步es应该是删除帖子
        Event event = new Event()
                .setTopic(TOPIC_DELETE)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);

        return CommunityUtil.getJSONString(0);
    }
}
