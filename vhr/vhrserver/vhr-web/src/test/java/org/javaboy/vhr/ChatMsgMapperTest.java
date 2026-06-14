package org.javaboy.vhr;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.javaboy.vhr.mapper.ChatMsgMapper;
import org.javaboy.vhr.model.ChatConversation;
import org.javaboy.vhr.model.ChatMsg;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对聊天消息持久化（离线消息）与已读状态的 Mapper 层测试。
 *
 * 不依赖 Spring 上下文 / MySQL / RabbitMQ / Redis：直接用 MyBatis 核心 API 在内嵌
 * H2 内存库上构建 {@link SqlSessionFactory}，并通过 {@code configuration.addMapper}
 * 自动绑定类路径上的 ChatMsgMapper.xml。每个用例前用纯 JDBC 重建表结构与基础数据，
 * 因此用例之间互相隔离、可重复执行。
 */
public class ChatMsgMapperTest {

    private SqlSession sqlSession;
    private ChatMsgMapper chatMsgMapper;

    @BeforeEach
    public void setUp() throws SQLException {
        DataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:chat;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        resetSchema(dataSource);

        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        // 绑定接口的同时自动加载同包下的 ChatMsgMapper.xml（来自 vhr-mapper 依赖的类路径）
        configuration.addMapper(ChatMsgMapper.class);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

        // 自动提交，使每次 insert/update 立即对后续查询可见
        sqlSession = sqlSessionFactory.openSession(true);
        chatMsgMapper = sqlSession.getMapper(ChatMsgMapper.class);
    }

    @AfterEach
    public void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    /** 用 H2 语法重建 hr / chat_msg 表，并预置参与会话的 hr 基础数据。 */
    private void resetSchema(DataSource dataSource) throws SQLException {
        String[] ddl = {
                "DROP TABLE IF EXISTS chat_msg",
                "DROP TABLE IF EXISTS hr",
                "CREATE TABLE hr (" +
                        "  id INT PRIMARY KEY," +
                        "  name VARCHAR(255)," +
                        "  username VARCHAR(255)," +
                        "  userface VARCHAR(255)" +
                        ")",
                "CREATE TABLE chat_msg (" +
                        "  id INT AUTO_INCREMENT PRIMARY KEY," +
                        "  from_user VARCHAR(255)," +
                        "  to_user VARCHAR(255)," +
                        "  content VARCHAR(1024)," +
                        "  create_date TIMESTAMP," +
                        "  status INT DEFAULT 0" +
                        ")",
                "INSERT INTO hr (id, name, username, userface) VALUES (1, '系统管理员', 'admin', 'admin.png')",
                "INSERT INTO hr (id, name, username, userface) VALUES (2, '李白', 'libai', 'libai.png')",
                "INSERT INTO hr (id, name, username, userface) VALUES (3, '韩愈', 'hanyu', 'hanyu.png')"
        };
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.execute(sql);
            }
        }
    }

    private ChatMsg msg(String from, String to, String content, long epochMillis, Integer status) {
        ChatMsg m = new ChatMsg();
        m.setFrom(from);
        m.setTo(to);
        m.setContent(content);
        m.setDate(new Date(epochMillis));
        m.setStatus(status);
        return m;
    }

    @Test
    public void offlineMessagePersistsAsUnreadAndIsRetrievable() {
        // admin 给（离线的）libai 发消息：实时推送即便丢失，消息也已落库
        chatMsgMapper.insertChatMsg(msg("admin", "libai", "你在吗?", 1_000L, 0));

        // libai 上线后仍可通过历史接口看到这条消息，且为未读
        List<ChatMsg> history = chatMsgMapper.getChatHistory("libai", "admin");
        assertThat(history).hasSize(1);
        ChatMsg only = history.get(0);
        assertThat(only.getId()).isNotNull();                 // 自增主键已回填
        assertThat(only.getFrom()).isEqualTo("admin");
        assertThat(only.getTo()).isEqualTo("libai");
        assertThat(only.getContent()).isEqualTo("你在吗?");
        assertThat(only.getStatus()).isEqualTo(0);            // 0 = 未读
    }

    @Test
    public void conversationListReturnsLastMessageTimeAndUnreadCount() {
        // libai <-> admin 三条，其中两条 admin->libai 未读
        chatMsgMapper.insertChatMsg(msg("admin", "libai", "第一条", 1_000L, 0));
        chatMsgMapper.insertChatMsg(msg("libai", "admin", "回复一下", 2_000L, 1));
        chatMsgMapper.insertChatMsg(msg("admin", "libai", "最后一条", 3_000L, 0));
        // libai <-> hanyu 一条（已读），用于验证多会话与排序
        chatMsgMapper.insertChatMsg(msg("hanyu", "libai", "hi", 500L, 1));

        List<ChatConversation> conversations = chatMsgMapper.getConversationsByUsername("libai");
        assertThat(conversations).hasSize(2);

        // 按最后消息时间倒序：admin 会话(3000) 在 hanyu 会话(500) 之前
        ChatConversation withAdmin = conversations.get(0);
        assertThat(withAdmin.getFriendUsername()).isEqualTo("admin");
        assertThat(withAdmin.getFriendName()).isEqualTo("系统管理员");
        assertThat(withAdmin.getFriendUserface()).isEqualTo("admin.png");
        assertThat(withAdmin.getLastMessage()).isEqualTo("最后一条");
        assertThat(withAdmin.getLastMessageTime().getTime()).isEqualTo(3_000L);
        assertThat(withAdmin.getUnreadCount()).isEqualTo(2);  // 两条 admin->libai 未读

        ChatConversation withHanyu = conversations.get(1);
        assertThat(withHanyu.getFriendUsername()).isEqualTo("hanyu");
        assertThat(withHanyu.getLastMessage()).isEqualTo("hi");
        assertThat(withHanyu.getUnreadCount()).isEqualTo(0);
    }

    @Test
    public void markReadFlipsStatusAndZeroesUnreadCount() {
        chatMsgMapper.insertChatMsg(msg("admin", "libai", "未读1", 1_000L, 0));
        chatMsgMapper.insertChatMsg(msg("admin", "libai", "未读2", 2_000L, 0));
        chatMsgMapper.insertChatMsg(msg("libai", "admin", "我发的", 3_000L, 0)); // 反向，不应被标记

        // libai 把 admin 发来的未读标记为已读
        int updated = chatMsgMapper.markRead("libai", "admin");
        assertThat(updated).isEqualTo(2);

        // libai 视角下与 admin 会话的未读数清零
        List<ChatConversation> conversations = chatMsgMapper.getConversationsByUsername("libai");
        assertThat(conversations).hasSize(1);
        assertThat(conversations.get(0).getUnreadCount()).isEqualTo(0);

        List<ChatMsg> history = chatMsgMapper.getChatHistory("libai", "admin");
        // admin->libai 的消息均已读
        long stillUnreadFromAdmin = history.stream()
                .filter(m -> "admin".equals(m.getFrom()))
                .filter(m -> m.getStatus() != null && m.getStatus() == 0)
                .count();
        assertThat(stillUnreadFromAdmin).isZero();
        // 反向消息（libai->admin）的状态不受影响，仍为未读
        ChatMsg mine = history.stream()
                .filter(m -> "libai".equals(m.getFrom()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 libai 发出的消息"));
        assertThat(mine.getStatus()).isEqualTo(0);
    }

    @Test
    public void historyReturnsFullThreadBothDirectionsInTimeOrder() {
        chatMsgMapper.insertChatMsg(msg("admin", "libai", "A", 3_000L, 1));
        chatMsgMapper.insertChatMsg(msg("libai", "admin", "B", 1_000L, 1));
        chatMsgMapper.insertChatMsg(msg("admin", "libai", "C", 2_000L, 1));

        // 不论从哪一方查询，都返回完整会话，按时间升序
        List<ChatMsg> fromAdmin = chatMsgMapper.getChatHistory("admin", "libai");
        assertThat(fromAdmin).extracting(ChatMsg::getContent).containsExactly("B", "C", "A");

        List<ChatMsg> fromLibai = chatMsgMapper.getChatHistory("libai", "admin");
        assertThat(fromLibai).extracting(ChatMsg::getContent).containsExactly("B", "C", "A");
    }
}
