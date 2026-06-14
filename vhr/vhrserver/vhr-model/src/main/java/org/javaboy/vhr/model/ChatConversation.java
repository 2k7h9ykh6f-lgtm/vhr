package org.javaboy.vhr.model;

import java.util.Date;

/**
 * 聊天会话列表项：当前用户与某个好友之间的会话概要。
 */
public class ChatConversation {
    /**
     * 对方用户名
     */
    private String friendUsername;
    /**
     * 对方昵称
     */
    private String friendName;
    /**
     * 对方头像
     */
    private String friendUserface;
    /**
     * 最后一条消息内容
     */
    private String lastMessage;
    /**
     * 最后一条消息时间
     */
    private Date lastMessageTime;
    /**
     * 当前用户在该会话中的未读消息数
     */
    private Integer unreadCount;

    public String getFriendUsername() {
        return friendUsername;
    }

    public void setFriendUsername(String friendUsername) {
        this.friendUsername = friendUsername;
    }

    public String getFriendName() {
        return friendName;
    }

    public void setFriendName(String friendName) {
        this.friendName = friendName;
    }

    public String getFriendUserface() {
        return friendUserface;
    }

    public void setFriendUserface(String friendUserface) {
        this.friendUserface = friendUserface;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Date getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Date lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public Integer getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(Integer unreadCount) {
        this.unreadCount = unreadCount;
    }
}
