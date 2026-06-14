package org.javaboy.vhr.mapper;

import org.apache.ibatis.annotations.Param;
import org.javaboy.vhr.model.ChatConversation;
import org.javaboy.vhr.model.ChatMsg;

import java.util.List;

public interface ChatMsgMapper {
    /**
     * 保存一条聊天消息（落库）。
     */
    int insertChatMsg(ChatMsg chatMsg);

    /**
     * 获取当前用户的会话列表，包含每个会话的最后一条消息、时间及未读数。
     */
    List<ChatConversation> getConversationsByUsername(@Param("username") String username);

    /**
     * 获取当前用户与某个好友之间的全部历史消息（双向），按时间升序。
     */
    List<ChatMsg> getChatHistory(@Param("username") String username, @Param("friend") String friend);

    /**
     * 将好友发给当前用户的未读消息标记为已读，返回更新条数。
     */
    int markRead(@Param("username") String username, @Param("friend") String friend);
}
