package org.javaboy.vhr.controller;

import org.javaboy.vhr.model.ChatConversation;
import org.javaboy.vhr.model.ChatMsg;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.model.RespBean;
import org.javaboy.vhr.service.ChatMsgService;
import org.javaboy.vhr.service.HrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {
    @Autowired
    HrService hrService;
    @Autowired
    ChatMsgService chatMsgService;

    @GetMapping("/hrs")
    public List<Hr> getAllHrs() {
        return hrService.getAllHrsExceptCurrentHr();
    }

    /**
     * 当前登录用户的会话列表，含每个会话的最后一条消息、时间及未读数。
     */
    @GetMapping("/conversations")
    public List<ChatConversation> getConversations() {
        return chatMsgService.getConversations();
    }

    /**
     * 当前登录用户与指定好友之间的历史消息（双向，按时间升序）。
     */
    @GetMapping("/history")
    public List<ChatMsg> getChatHistory(@RequestParam String friend) {
        return chatMsgService.getChatHistory(friend);
    }

    /**
     * 将指定好友发给当前登录用户的未读消息标记为已读。
     */
    @PutMapping("/read")
    public RespBean markRead(@RequestParam String friend) {
        Integer count = chatMsgService.markRead(friend);
        return RespBean.ok("标记已读成功!", count);
    }
}
