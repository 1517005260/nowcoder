package com.nowcoder.community.controller;

import com.nowcoder.community.entity.Message;
import com.nowcoder.community.entity.Page;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.MessageService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private UserService userService;

    // 私信列表
    @RequestMapping(path = "/letter/list", method = RequestMethod.GET)
    public String getLetterList(Model model, Page page){
        User user = hostHolder.getUser();

        // 设置分页信息
        page.setLimit(5);
        page.setPath("/letter/list");
        page.setRows(messageService.findConversationCount(user.getId()));

        // 会话列表
        List<Message> conversationlist
                = messageService.findConversations(user.getId(), page.getOffset(), page.getLimit());

        // 封装 未读消息、消息总数等信息
        List<Map<String, Object>> conversations = new ArrayList<>();
        if(conversationlist != null){
            for(Message message : conversationlist){
                Map<String, Object> map = new HashMap<>();
                map.put("conversation", message);  // 和某个用户所有的对话
                map.put("letterCount", messageService.  // 私信总数量
                        findLetterCount(message.getConversationId()));
                map.put("unreadCount", messageService.  // 该对话的未读消息
                        findLetterUnreadCount(user.getId(), message.getConversationId()));
                int targetId = user.getId() == message.getFromId() ?
                        message.getToId() : message.getFromId();  // 和本用户相对的用户，用于显示头像
                map.put("target", userService.findUserById(targetId));

                conversations.add(map);
            }
        }

        model.addAttribute("conversations",conversations);

        // 未读消息总数
        int letterUnreadCount = messageService.findLetterUnreadCount(user.getId(), null);
        model.addAttribute("letterUnreadCount", letterUnreadCount);

        return "/site/letter";
    }

    // 私信详情
    @RequestMapping(path = "/letter/detail/{conversationId}", method = RequestMethod.GET)
    public String getLetterDetail(@PathVariable("conversationId")String conversationId,
                                  Page page, Model model){
        // 分页信息设置
        page.setLimit(5);
        page.setPath("/letter/detail/" + conversationId);
        page.setRows(messageService.findLetterCount(conversationId));

        // 和某个用户的所有对话记录
        List<Message> letterList =  messageService.findLetters(conversationId, page.getOffset(), page.getLimit());
        List<Map<String, Object>> letters = new ArrayList<>();
        if(letterList != null){
            for(Message message : letterList){
                Map<String, Object> map = new HashMap<>();
                map.put("letter", message);
                map.put("fromUser", userService.findUserById(message.getFromId()));

                letters.add(map);
            }
        }

        model.addAttribute("letters", letters);

        // 私信的目标
        model.addAttribute("target", getLetterTarget(conversationId));

        return "/site/letter-detail";
    }

    private User getLetterTarget(String conversationId){
        String[] ids = conversationId.split("_");
        int id1 = Integer.parseInt(ids[0]);
        int id2 = Integer.parseInt(ids[1]);

        if(hostHolder.getUser().getId() == id1){
            return userService.findUserById(id2);
        }else{
            return userService.findUserById(id1);
        }
    }
}
