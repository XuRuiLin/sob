package com.hyd.sob;

import com.hyd.sob.bots.Bot;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class BotService {

    private static final Logger LOG = LoggerFactory.getLogger(BotService.class);

    @Autowired
    private SobConfiguration configuration;

    @Autowired
    private BotFactory botFactory;

    private XMPPConnection connection;

    private ChatManager chatManager;

    @PostConstruct
    private void init() throws Exception {

        XMPPTCPConnection tcpConnection = botLogin();
        LOG.info("SOB bot is ready.");

        this.connection = tcpConnection;
        initChatManager();
    }

    private XMPPTCPConnection botLogin() throws Exception {
        LOG.info("SOB bot is connecting to " + configuration.getXmppServerHost() + "...");

        XMPPTCPConnectionConfiguration.Builder builder = XMPPTCPConnectionConfiguration
                .builder()
                .setUsernameAndPassword(
                        configuration.getBotUserName(),
                        configuration.getBotUserPass()
                )
                .setXmppDomain(configuration.getXmppServerHost())
                .setResource(configuration.getBotResource());

        if (!configuration.isUseSsl()) {
            builder.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        }

        XMPPTCPConnectionConfiguration config = builder.build();
        XMPPTCPConnection tcpConnection = new XMPPTCPConnection(config);

        tcpConnection.connect().login();
        return tcpConnection;
    }

    private void initChatManager() {
        chatManager = ChatManager.getInstanceFor(connection);
        chatManager.addIncomingListener(this::onMessageReceived);
    }

    private void onMessageReceived(EntityBareJid jid, Message message, Chat chat) {
        try {
            Bot bot = botFactory.getBot(message);
            if (bot != null) {
                bot.handleMessage(message, chat);
            }
        } catch (Exception e) {
            LOG.error(e.toString(), e);
        }
    }

    public void sendMessage(Map<String, Object> message) {

        String timestamp = FastDateFormat
                .getDateTimeInstance(FastDateFormat.MEDIUM, FastDateFormat.MEDIUM)
                .format(new Date());

        String strMessage = timestamp + "\n" +
                message.entrySet().stream()
                        .map(this::entryToString)
                        .collect(Collectors.joining("\n"));

        configuration.getUsers()
                .stream()
                .map(this::toJid)
                .filter(Objects::nonNull)
                .forEach(jid -> sendToJid(jid, strMessage));
    }

    private String entryToString(Map.Entry<String, Object> entry) {

        if (entry.getValue() == null) {
            return entry.getKey();

        } else if (entry.getValue() instanceof CharSequence) {
            if (StringUtils.isBlank((CharSequence) entry.getValue())) {
                return entry.getKey();
            } else {
                return entry.getKey() + ": " + entry.getValue();
            }

        } else {
            return entry.getKey() + ": " + entry.getValue();
        }
    }

    private void sendToJid(EntityBareJid jid, String strMessage) {
        try {
            chatManager.chatWith(jid).send(strMessage);
        } catch (Exception e) {
            LOG.error("", e);
        }
    }

    private EntityBareJid toJid(String user) {
        try {
            return JidCreate.entityBareFrom(user + "@" + configuration.getXmppServerHost());
        } catch (Exception e) {
            LOG.error("", e);
            return null;
        }
    }

    @PreDestroy
    private void fin() {
        if (connection instanceof XMPPTCPConnection) {
            ((XMPPTCPConnection) connection).disconnect();
        }
    }
}
