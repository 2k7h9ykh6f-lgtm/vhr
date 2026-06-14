package org.javaboy.vhr.controller;

import org.javaboy.vhr.model.ChatMsg;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.service.ChatMsgService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Date;

@Controller
public class WsController {
    @Autowired
    SimpMessagingTemplate simpMessagingTemplate;
    @Autowired
    ChatMsgService chatMsgService;

    @MessageMapping("/ws/chat")
    public void handleMsg(Authentication authentication, ChatMsg chatMsg) {
        Hr hr = (Hr) authentication.getPrincipal();
        chatMsg.setFrom(hr.getUsername());
        chatMsg.setFromNickname(hr.getName());
        chatMsg.setDate(new Date());
        // 落库保存消息（默认未读），不影响下方实时推送路径，离线用户可通过历史接口查看
        chatMsgService.saveChatMsg(chatMsg);
        simpMessagingTemplate.convertAndSendToUser(chatMsg.getTo(), "/queue/chat", chatMsg);
    }
}
