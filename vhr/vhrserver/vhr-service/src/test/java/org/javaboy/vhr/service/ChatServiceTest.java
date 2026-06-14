package org.javaboy.vhr.service;

import org.javaboy.vhr.mapper.ChatMsgMapper;
import org.javaboy.vhr.mapper.HrMapper;
import org.javaboy.vhr.model.ChatConversation;
import org.javaboy.vhr.model.ChatMsg;
import org.javaboy.vhr.model.Hr;
import org.javaboy.vhr.model.RespPageBean;
import org.javaboy.vhr.utils.HrUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatServiceTest {

    @Mock
    private ChatMsgMapper chatMsgMapper;

    @Mock
    private HrMapper hrMapper;

    @InjectMocks
    private ChatService chatService;

    private MockedStatic<HrUtils> hrUtilsMockedStatic;

    private Hr currentHr;

    @BeforeEach
    void setUp() {
        currentHr = new Hr();
        currentHr.setId(1);
        currentHr.setUsername("admin");
        currentHr.setName("管理员");
        hrUtilsMockedStatic = mockStatic(HrUtils.class);
        hrUtilsMockedStatic.when(HrUtils::getCurrentHr).thenReturn(currentHr);
    }

    @AfterEach
    void tearDown() {
        hrUtilsMockedStatic.close();
    }

    @Test
    void saveMessage_setsReadStatusFalse_whenNull() {
        ChatMsg msg = new ChatMsg();
        msg.setFrom("admin");
        msg.setTo("zhangsan");
        msg.setContent("hello");
        msg.setDate(new Date());

        chatService.saveMessage(msg);

        assertFalse(msg.getReadStatus());
        verify(chatMsgMapper).insertSelective(msg);
    }

    @Test
    void saveMessage_preservesExistingReadStatus_whenTrue() {
        ChatMsg msg = new ChatMsg();
        msg.setFrom("admin");
        msg.setTo("zhangsan");
        msg.setContent("hello");
        msg.setDate(new Date());
        msg.setReadStatus(true);

        chatService.saveMessage(msg);

        assertTrue(msg.getReadStatus());
        verify(chatMsgMapper).insertSelective(msg);
    }

    @Test
    void saveMessage_storesCorrectFromTo() {
        ChatMsg msg = new ChatMsg();
        msg.setFrom("admin");
        msg.setTo("zhangsan");
        msg.setContent("test message");
        msg.setDate(new Date());

        ArgumentCaptor<ChatMsg> captor = ArgumentCaptor.forClass(ChatMsg.class);
        chatService.saveMessage(msg);

        verify(chatMsgMapper).insertSelective(captor.capture());
        ChatMsg saved = captor.getValue();
        assertEquals("admin", saved.getFrom());
        assertEquals("zhangsan", saved.getTo());
        assertEquals("test message", saved.getContent());
    }

    @Test
    void getConversationList_returnsCorrectData() {
        Hr contactHr = new Hr();
        contactHr.setId(2);
        contactHr.setUsername("zhangsan");
        contactHr.setName("张三");
        contactHr.setPassword("secret");

        Date now = new Date();
        ChatMsg lastMsg = new ChatMsg();
        lastMsg.setContent("last message");
        lastMsg.setDate(now);

        when(chatMsgMapper.getContactUsernames("admin")).thenReturn(Arrays.asList("zhangsan"));
        when(hrMapper.loadUserByUsername("zhangsan")).thenReturn(contactHr);
        when(chatMsgMapper.getLastMessage("admin", "zhangsan")).thenReturn(lastMsg);
        when(chatMsgMapper.getUnreadCount("zhangsan", "admin")).thenReturn(3);

        List<ChatConversation> conversations = chatService.getConversationList();

        assertEquals(1, conversations.size());
        ChatConversation conv = conversations.get(0);
        assertEquals("zhangsan", conv.getHr().getUsername());
        assertNull(conv.getHr().getPassword(), "password should be nullified");
        assertEquals("last message", conv.getLastMessage());
        assertEquals(now, conv.getLastMessageTime());
        assertEquals(3, conv.getUnreadCount());
    }

    @Test
    void getConversationList_emptyWhenNoContacts() {
        when(chatMsgMapper.getContactUsernames("admin")).thenReturn(Collections.emptyList());

        List<ChatConversation> conversations = chatService.getConversationList();

        assertTrue(conversations.isEmpty());
    }

    @Test
    void getConversationList_handlesDeletedUser() {
        when(chatMsgMapper.getContactUsernames("admin")).thenReturn(Arrays.asList("deleted_user"));
        when(hrMapper.loadUserByUsername("deleted_user")).thenReturn(null);

        List<ChatConversation> conversations = chatService.getConversationList();

        assertTrue(conversations.isEmpty());
        verify(chatMsgMapper, never()).getLastMessage(anyString(), anyString());
    }

    @Test
    void getConversationList_handlesNullLastMessage() {
        Hr contactHr = new Hr();
        contactHr.setId(2);
        contactHr.setUsername("zhangsan");
        contactHr.setName("张三");
        contactHr.setPassword("secret");

        when(chatMsgMapper.getContactUsernames("admin")).thenReturn(Arrays.asList("zhangsan"));
        when(hrMapper.loadUserByUsername("zhangsan")).thenReturn(contactHr);
        when(chatMsgMapper.getLastMessage("admin", "zhangsan")).thenReturn(null);
        when(chatMsgMapper.getUnreadCount("zhangsan", "admin")).thenReturn(0);

        List<ChatConversation> conversations = chatService.getConversationList();

        assertEquals(1, conversations.size());
        assertNull(conversations.get(0).getLastMessage());
        assertNull(conversations.get(0).getLastMessageTime());
        assertEquals(0, conversations.get(0).getUnreadCount());
    }

    @Test
    void getChatHistory_paginationOffset_page1() {
        List<ChatMsg> messages = Arrays.asList(new ChatMsg(), new ChatMsg());
        when(chatMsgMapper.getChatHistory("admin", "zhangsan", 0, 10)).thenReturn(messages);
        when(chatMsgMapper.getChatHistoryCount("admin", "zhangsan")).thenReturn(25L);

        RespPageBean result = chatService.getChatHistory("zhangsan", 1, 10);

        assertEquals(25L, result.getTotal());
        assertEquals(2, result.getData().size());
        verify(chatMsgMapper).getChatHistory("admin", "zhangsan", 0, 10);
    }

    @Test
    void getChatHistory_paginationOffset_page2() {
        List<ChatMsg> messages = Arrays.asList(new ChatMsg());
        when(chatMsgMapper.getChatHistory("admin", "zhangsan", 10, 10)).thenReturn(messages);
        when(chatMsgMapper.getChatHistoryCount("admin", "zhangsan")).thenReturn(11L);

        RespPageBean result = chatService.getChatHistory("zhangsan", 2, 10);

        assertEquals(11L, result.getTotal());
        verify(chatMsgMapper).getChatHistory("admin", "zhangsan", 10, 10);
    }

    @Test
    void getChatHistory_paginationOffset_page3Size5() {
        when(chatMsgMapper.getChatHistory("admin", "zhangsan", 10, 5)).thenReturn(Collections.emptyList());
        when(chatMsgMapper.getChatHistoryCount("admin", "zhangsan")).thenReturn(12L);

        RespPageBean result = chatService.getChatHistory("zhangsan", 3, 5);

        assertEquals(12L, result.getTotal());
        // offset = (3-1)*5 = 10
        verify(chatMsgMapper).getChatHistory("admin", "zhangsan", 10, 5);
    }

    @Test
    void markAsRead_callsCorrectDirection() {
        when(chatMsgMapper.markAsRead("zhangsan", "admin")).thenReturn(5);

        int result = chatService.markAsRead("zhangsan");

        assertEquals(5, result);
        // Verify: marks messages FROM zhangsan TO admin (not the reverse)
        verify(chatMsgMapper).markAsRead("zhangsan", "admin");
    }

    @Test
    void markAsRead_idempotent_zeroOnSecondCall() {
        when(chatMsgMapper.markAsRead("zhangsan", "admin")).thenReturn(0);

        int result = chatService.markAsRead("zhangsan");

        assertEquals(0, result);
        verify(chatMsgMapper).markAsRead("zhangsan", "admin");
    }
}
