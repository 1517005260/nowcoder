package com.nowcoder.community.controller;

import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.Page;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.ElasticsearchService;
import com.nowcoder.community.service.FollowService;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SearchController implements CommunityConstant {
    // 搜索
    @Autowired
    private ElasticsearchService elasticsearchService;

    // 搜到后展示作者和点赞数
    @Autowired
    private LikeService likeService;

    @Autowired
    private UserService userService;

    @Autowired
    private FollowService followService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private RedisTemplate redisTemplate;

    // 路径格式：/search?keyword=xxx
    @RequestMapping(path = "/search", method = RequestMethod.GET)
    public String search(String keyword, Page page, Model model) {
        // 搜索帖子
        org.springframework.data.domain.Page<DiscussPost> searchResult =
                elasticsearchService.searchDiscussPost(keyword, page.getCurrent() - 1, page.getLimit());

        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if (searchResult != null) {
            for (DiscussPost post : searchResult) {
                Map<String, Object> map = new HashMap<>();
                String contentPlainText = extractPlainText(post.getContent());

                map.put("post", post);
                map.put("user", userService.findUserById(post.getUserId()));  // 作者
                map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));  // 赞数
                map.put("contentPlainText", contentPlainText);  // Processed plain text content

                String redisKey = RedisKeyUtil.getPostReadKey(post.getId());
                map.put("postReadCount", redisTemplate.opsForValue().get(redisKey));

                discussPosts.add(map);
            }
        }
        model.addAttribute("discussPosts", discussPosts);
        model.addAttribute("keyword", keyword);

        page.setPath("/search?keyword=" + keyword);
        page.setRows(searchResult == null ? 0 : (int) searchResult.getTotalElements());

        return "/site/search";
    }

    private String extractPlainText(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        // 替换掉所有Markdown语法标记
        String plainText = markdown.replaceAll("\\!\\[.*?\\]\\(.*?\\)", "")  // Remove images
                .replaceAll("\\[(.*?)\\]\\(.*?\\)", "$1") // Remove links, keep text
                .replaceAll("`", "")                      // Remove code markers
                .replaceAll("\\*\\*|__", "")              // Remove bold markers
                .replaceAll("\\*", "")                    // Remove italic markers
                .replaceAll("~~", "")                     // Remove strikethrough markers
                .replaceAll("#+", "")                     // Remove headers
                .replaceAll("> ", "")                     // Remove blockquotes
                .replaceAll("- ", "")                     // Remove list items
                .replaceAll("\\n{2,}", "\n")              // Remove extra newlines
                .replaceAll("\\r\\n|\\r|\\n", " ")        // Convert newlines to spaces
                .trim();                                   // Trim leading/trailing whitespace

        return plainText;
    }


    @RequestMapping(path = "/searchUser", method = RequestMethod.GET)
    public String searchUser(String username, Page page, Model model){
        // 搜索用户
        org.springframework.data.domain.Page<User> searchResult =
                elasticsearchService.searchUser(username, page.getCurrent() - 1, page.getLimit());

        List<Map<String, Object>> Users = new ArrayList<>();
        if(searchResult != null){
            for(User user : searchResult){
                Map<String, Object> map = new HashMap<>();

                map.put("user", user);
                map.put("uid", user.getId());
                map.put("username", user.getUsername());
                map.put("headerUrl", user.getHeaderUrl());
                map.put("createTime", user.getCreateTime());
                map.put("type", user.getType());

                map.put("likeCount", likeService.findUserLikeCount(user.getId()));
                map.put("followers", followService.findFollowerCount(ENTITY_TYPE_USER, user.getId()));

                boolean hasFollowed = false;
                if(hostHolder.getUser() != null){
                    hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, user.getId());
                }
                map.put("hasFollowed", hasFollowed);

                Users.add(map);
            }
        }
        model.addAttribute("Users", Users);
        model.addAttribute("name", username);
        model.addAttribute("hostUser", hostHolder.getUser());

        page.setPath("/searchUser?name=" + username);
        page.setRows(searchResult == null ? 0 : (int)searchResult.getTotalElements());

        return "/site/searchUser";
    }
}