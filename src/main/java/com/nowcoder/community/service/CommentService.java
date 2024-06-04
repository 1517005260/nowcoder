package com.nowcoder.community.service;

import com.nowcoder.community.dao.CommentMapper;
import com.nowcoder.community.dao.UserMapper;
import com.nowcoder.community.entity.Comment;
import com.nowcoder.community.entity.Event;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.event.EventProducer;
import com.nowcoder.community.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CommentService implements CommunityConstant {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private SensitiveFilter sensitiveFilter;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private EventProducer eventProducer;

    @Autowired
    private LikeService likeService;

    public List<Comment> findCommentsByEntity(int entityType, int entityId, int offset, int limit, int orderMode){
        if(orderMode != 2){
            return commentMapper.selectCommentsByEntity(entityType, entityId, offset, limit, orderMode);
        }else{
            // 获取所有评论
            List<Comment> comments = commentMapper.selectCommentsByEntity(entityType, entityId, 0, Integer.MAX_VALUE, 1);

            // 对每条评论获取其赞数，并存储在一个Map中
            Map<Comment, Integer> likeCountMap = new HashMap<>();
            for(Comment comment : comments){
                int likeCount = (int) likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, comment.getId());
                likeCountMap.put(comment, likeCount);
            }

            // 按赞数对评论进行降序排序
            comments.sort((c1, c2) -> likeCountMap.get(c2) - likeCountMap.get(c1));

            // 返回指定范围内的评论
            int toIndex = Math.min(offset + limit, comments.size());
            return comments.subList(offset, toIndex);
        }
    }

    public int findCommentCount(int entityType, int entityId){
        return commentMapper.selectCountByEntity(entityType, entityId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public int addComment(Comment comment, int postId){
        if(comment == null){
            throw new IllegalArgumentException("参数不能为空！");
        }
        //增加评论
        comment.setContent(HtmlUtils.htmlEscape(comment.getContent()));
        comment.setContent(sensitiveFilter.filter(comment.getContent()));
        int rows = commentMapper.insertComment(comment);

        //增加帖子的评论数
        if(comment.getEntityType() == ENTITY_TYPE_POST){
            int count = commentMapper.selectCountByEntity(comment.getEntityType(), comment.getEntityId());
            discussPostService.updateCommentCount(comment.getEntityId(),count);
        }

        // 匹配评论中的 @用户名 并发送通知
        String content = comment.getContent();
        Pattern pattern = Pattern.compile("@([a-zA-Z0-9_]+?)(?=[\\s@]|$)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String username = matcher.group(1);
            User user = userMapper.selectByName(username);
            if (user != null) {
                // 发送通知
                Event mentionEvent = new Event()
                        .setTopic(TOPIC_MENTION)
                        .setUserId(hostHolder.getUser().getId())
                        .setEntityType(ENTITY_TYPE_COMMENT)
                        .setEntityId(comment.getId())
                        .setEntityUserId(user.getId())
                        .setData("postId", postId);
                eventProducer.fireEvent(mentionEvent);
            }
        }
        return rows;
    }

    public Comment findCommentById(int id){
        return commentMapper.selectCommentById(id);
    }

    // 查询某个用户的所有评论
    public List<Comment> findUserComments(int userId, int offset, int limit) {
        return commentMapper.selectCommentsByUser(userId, offset, limit);
    }

    // 查询某个用户的评论数量
    public int findUserCount(int userId) {
        return commentMapper.selectCountByUser(userId);
    }
}
