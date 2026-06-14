package org.javaboy.vhr.controller;

import org.javaboy.vhr.model.ChatConversation;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.model.RespBean;
import org.javaboy.vhr.model.RespPageBean;
import org.javaboy.vhr.service.ChatService;
import org.javaboy.vhr.service.HrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {
    @Autowired
    HrService hrService;

    @Autowired
    ChatService chatService;

    @GetMapping("/hrs")
    public List<Hr> getAllHrs() {
        return hrService.getAllHrsExceptCurrentHr();
    }

    /**
     * 获取当前用户的会话列表
     */
    @GetMapping("/conversations")
    public List<ChatConversation> getConversations() {
        return chatService.getConversationList();
    }

    /**
     * 获取与指定联系人的聊天历史（分页）
     */
    @GetMapping("/history")
    public RespPageBean getChatHistory(@RequestParam String contactUsername,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size) {
        return chatService.getChatHistory(contactUsername, page, size);
    }

    /**
     * 标记来自指定用户的所有消息为已读
     */
    @PutMapping("/markRead")
    public RespBean markAsRead(@RequestParam String fromUsername) {
        chatService.markAsRead(fromUsername);
        return RespBean.ok("标记已读成功");
    }
}
