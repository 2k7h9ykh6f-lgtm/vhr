package org.javaboy.vhr.mapper;

import org.apache.ibatis.annotations.Param;
import org.javaboy.vhr.model.ChatMsg;

import java.util.List;

public interface ChatMsgMapper {

    int insertSelective(ChatMsg record);

    List<ChatMsg> getChatHistory(@Param("from") String from, @Param("to") String to,
                                 @Param("offset") Integer offset, @Param("size") Integer size);

    Long getChatHistoryCount(@Param("from") String from, @Param("to") String to);

    List<String> getContactUsernames(@Param("username") String username);

    ChatMsg getLastMessage(@Param("from") String from, @Param("to") String to);

    Integer getUnreadCount(@Param("from") String from, @Param("to") String to);

    int markAsRead(@Param("from") String from, @Param("to") String to);
}
