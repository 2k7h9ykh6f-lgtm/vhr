package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.ChatMsgMapper;
import org.javaboy.vhr.model.ChatConversation;
import org.javaboy.vhr.model.ChatMsg;
import org.javaboy.vhr.utils.HrUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class ChatMsgService {
    @Autowired
    ChatMsgMapper chatMsgMapper;

    /**
     * 保存一条聊天消息。新消息默认时间为当前时间、状态为未读(0)。
     * 该方法不依赖 SecurityContext，由发送方（WebSocket）设置好 from/to/content 后调用。
     */
    public void saveChatMsg(ChatMsg chatMsg) {
        if (chatMsg.getDate() == null) {
            chatMsg.setDate(new Date());
        }
        if (chatMsg.getStatus() == null) {
            chatMsg.setStatus(0);
        }
        chatMsgMapper.insertChatMsg(chatMsg);
    }

    /**
     * 获取当前登录用户的会话列表。
     */
    public List<ChatConversation> getConversations() {
        return chatMsgMapper.getConversationsByUsername(HrUtils.getCurrentHr().getUsername());
    }

    /**
     * 获取当前登录用户与指定好友之间的历史消息。
     */
    public List<ChatMsg> getChatHistory(String friend) {
        return chatMsgMapper.getChatHistory(HrUtils.getCurrentHr().getUsername(), friend);
    }

    /**
     * 将指定好友发给当前登录用户的未读消息标记为已读，返回更新条数。
     */
    public Integer markRead(String friend) {
        return chatMsgMapper.markRead(HrUtils.getCurrentHr().getUsername(), friend);
    }
}
