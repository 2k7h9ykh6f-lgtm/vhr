package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.ChatMsgMapper;
import org.javaboy.vhr.mapper.HrMapper;
import org.javaboy.vhr.model.ChatConversation;
import org.javaboy.vhr.model.ChatMsg;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.model.RespPageBean;
import org.javaboy.vhr.utils.HrUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

    @Autowired
    ChatMsgMapper chatMsgMapper;

    @Autowired
    HrMapper hrMapper;

    /**
     * 保存聊天消息到数据库
     */
    public int saveMessage(ChatMsg chatMsg) {
        if (chatMsg.getReadStatus() == null) {
            chatMsg.setReadStatus(false);
        }
        return chatMsgMapper.insertSelective(chatMsg);
    }

    /**
     * 获取当前用户的会话列表
     */
    public List<ChatConversation> getConversationList() {
        Hr currentHr = HrUtils.getCurrentHr();
        String currentUsername = currentHr.getUsername();

        List<String> contactUsernames = chatMsgMapper.getContactUsernames(currentUsername);
        List<ChatConversation> conversations = new ArrayList<>();

        for (String contactUsername : contactUsernames) {
            Hr contactHr = hrMapper.loadUserByUsername(contactUsername);
            if (contactHr == null) {
                // 用户已被删除，跳过
                continue;
            }

            ChatMsg lastMsg = chatMsgMapper.getLastMessage(currentUsername, contactUsername);
            Integer unreadCount = chatMsgMapper.getUnreadCount(contactUsername, currentUsername);

            ChatConversation conversation = new ChatConversation();
            contactHr.setPassword(null);
            conversation.setHr(contactHr);
            if (lastMsg != null) {
                conversation.setLastMessage(lastMsg.getContent());
                conversation.setLastMessageTime(lastMsg.getDate());
            }
            conversation.setUnreadCount(unreadCount != null ? unreadCount : 0);
            conversations.add(conversation);
        }

        return conversations;
    }

    /**
     * 获取与指定联系人的聊天历史（分页）
     */
    public RespPageBean getChatHistory(String contactUsername, Integer page, Integer size) {
        Hr currentHr = HrUtils.getCurrentHr();
        String currentUsername = currentHr.getUsername();

        int offset = (page - 1) * size;
        List<ChatMsg> messages = chatMsgMapper.getChatHistory(currentUsername, contactUsername, offset, size);
        Long total = chatMsgMapper.getChatHistoryCount(currentUsername, contactUsername);

        RespPageBean respPageBean = new RespPageBean();
        respPageBean.setData(messages);
        respPageBean.setTotal(total);
        return respPageBean;
    }

    /**
     * 标记来自指定用户的所有消息为已读
     */
    public int markAsRead(String fromUsername) {
        Hr currentHr = HrUtils.getCurrentHr();
        String currentUsername = currentHr.getUsername();
        return chatMsgMapper.markAsRead(fromUsername, currentUsername);
    }
}
