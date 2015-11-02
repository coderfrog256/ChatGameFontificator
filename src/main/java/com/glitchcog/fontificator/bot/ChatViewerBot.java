package com.glitchcog.fontificator.bot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.jibble.pircbot.PircBot;

import com.glitchcog.fontificator.config.ConfigMessage;
import com.glitchcog.fontificator.gui.chat.ChatPanel;
import com.glitchcog.fontificator.gui.controls.panel.ControlPanelIrc;
import com.google.gson.Gson;

/**
 * The IRC bot that handles connecting to the IRC server and receiving all the posts. It also managed username casing.
 * 
 * @author Matt Yanos
 */
public class ChatViewerBot extends PircBot
{
    private static final Logger logger = Logger.getLogger(ChatViewerBot.class);

    /**
     * Reference to the chat panel to add messages as they're posted
     */
    private ChatPanel chat;

    /**
     * The base URL for looking up username casing on the Twitch API
     */
    private static final String USERNAME_LOOKUP_BASE_URL = "https://api.twitch.tv/kraken/users/";

    /**
     * The control panel that calls to connect or disconnect the bot, stored as a member to enable or to disable buttons
     * based on successful connections
     */
    private ControlPanelIrc controlPanel;

    /**
     * Used to determine the selected username case resolution type
     */
    private ConfigMessage messageConfig;

    /**
     * A map of name casing where the key is the username in all lowercase, and the value is the correctly cased
     * username
     */
    private Map<String, String> usernameCases;

    /**
     * The number of posts per username
     */
    private Map<String, Integer> usernamePostCount;

    /**
     * Default constructor, just initializes the username case map
     */
    public ChatViewerBot()
    {
        this.usernamePostCount = new HashMap<String, Integer>();
        this.usernameCases = new HashMap<String, String>();
    }

    public void reset()
    {
        usernamePostCount.clear();
        usernameCases.clear();
    }

    @Override
    public void log(String line)
    {
        controlPanel.log(line);
    }

    /**
     * Empty out the cache of usernames. Used when the option of how to case usernames is changed via the Message
     * control panel tab
     */
    public void clearUsernameCases()
    {
        usernameCases.clear();
    }

    /**
     * Set the reference to the chat panel to add messages as they're posted
     * 
     * @param chat
     */
    public void setChatPanel(ChatPanel chat)
    {
        this.chat = chat;
    }

    /**
     * PircBot doesn't let you set the username if you're connected, so this checks for that
     * 
     * @param name
     */
    public void setUsername(String name)
    {
        if (!isConnected())
        {
            super.setName(name);
        }
    }

    /**
     * This method is called once the PircBot has success to the IRC server.
     * <p>
     * The implementation of this method in the PircBot abstract class performs no actions and may be overridden as
     * required.
     * 
     * @since PircBot 0.9.6
     */
    @Override
    protected void onConnect()
    {
        logger.info("Connected");
        controlPanel.toggleConnect(true);
    }

    /**
     * This method is called whenever someone (possibly us) joins a channel which we are on.
     * <p>
     * The implementation of this method in the PircBot abstract class performs no actions and may be overridden as
     * required.
     *
     * @param channel
     *            The channel which somebody joined.
     * @param sender
     *            The nick of the user who joined the channel.
     * @param login
     *            The login of the user who joined the channel.
     * @param hostname
     *            The hostname of the user who joined the channel.
     */
    @Override
    protected void onJoin(String channel, String sender, String login, String hostname)
    {
        sendMessageToChat(MessageType.JOIN, sender, "joined " + channel + ".");
    }

    /**
     * This method is called whenever an ACTION is sent from a user. E.g. such events generated by typing
     * "/me goes shopping" in most IRC clients.
     * <p>
     * The implementation of this method in the PircBot abstract class performs no actions and may be overridden as
     * required.
     * 
     * @param sender
     *            The nick of the user that sent the action.
     * @param login
     *            The login of the user that sent the action.
     * @param hostname
     *            The hostname of the user that sent the action.
     * @param target
     *            The target of the action, be it a channel or our nick.
     * @param action
     *            The action carried out by the user.
     */
    @Override
    protected void onAction(String sender, String login, String hostname, String target, String action)
    {
        sendMessageToChat(MessageType.ACTION, sender, action);
    }

    /**
     * This method is called whenever a message is sent to a channel.
     * <p>
     * The implementation of this method in the PircBot abstract class performs no actions and may be overridden as
     * required.
     *
     * @param channel
     *            The channel to which the message was sent.
     * @param sender
     *            The nick of the person who sent the message.
     * @param login
     *            The login of the person who sent the message.
     * @param hostname
     *            The hostname of the person who sent the message.
     * @param message
     *            The actual message sent to the channel.
     */
    @Override
    protected void onMessage(String channel, String sender, String login, String hostname, String message)
    {
        sendMessageToChat(sender, message);
    }

    /**
     * Post a message to chat with just a username and message content, which defaults to a NORMAL type message
     * 
     * @param username
     * @param message
     */
    public void sendMessageToChat(String username, String message)
    {
        sendMessageToChat(MessageType.NORMAL, username, message);
    }

    /**
     * Post a message to chat, specifying the message type, username, and message content
     * 
     * @param type
     * @param username
     * @param message
     */
    public void sendMessageToChat(MessageType type, String username, String message)
    {
        String casedUsername = username;

        if (type != MessageType.JOIN)
        {
            if (messageConfig.isSpecifyCaseAllowed())
            {
                if (message.toLowerCase().contains(username.toLowerCase()))
                {
                    // Run a quick regex find to make sure the username is not inside another word
                    Pattern pat = Pattern.compile("\\b" + username.toLowerCase() + "\\b");
                    Matcher mtch = pat.matcher(message.toLowerCase());
                    if (mtch.find())
                    {
                        final int usernameIndex = message.toLowerCase().indexOf(username.toLowerCase());
                        casedUsername = message.substring(usernameIndex, usernameIndex + username.length());
                        usernameCases.put(username.toLowerCase(), casedUsername);
                    }
                }
            }

            if (!usernameCases.containsKey(username.toLowerCase()))
            {
                switch (messageConfig.getCaseResolutionType())
                {
                case ALL_CAPS:
                    casedUsername = username.toUpperCase();
                    break;
                case ALL_LOWERCASE:
                    casedUsername = username.toLowerCase();
                    break;
                case FIRST:
                    casedUsername = username.substring(0, 1).toUpperCase() + username.substring(1).toLowerCase();
                    break;
                case LOOKUP:
                    if (type.containsParsableUsername())
                    {
                        try
                        {
                            URL url = new URL(USERNAME_LOOKUP_BASE_URL + username.toLowerCase());
                            URLConnection conn = url.openConnection();
                            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                            String jsonResult = "";
                            String line;
                            while ((line = br.readLine()) != null)
                            {
                                jsonResult += line;
                            }
                            br.close();

                            casedUsername = (String) new Gson().fromJson(jsonResult, Map.class).get("display_name");
                        }
                        catch (Exception e)
                        {
                            logger.error("Attempt to look up " + username + " on Twitch API failed.");
                        }
                    }
                    break;
                case NONE:
                default:
                    casedUsername = username;
                    break;
                }
                usernameCases.put(username.toLowerCase(), casedUsername);
            }

            if (usernameCases.containsKey(username.toLowerCase()))
            {
                casedUsername = usernameCases.get(username.toLowerCase());
            }
        }

        Message msg = new Message(type, casedUsername, message);

        Integer postCount = usernamePostCount.get(msg.getUsername().toLowerCase());
        if (postCount == null)
        {
            postCount = Integer.valueOf(1);
        }
        else
        {
            postCount++;
        }
        usernamePostCount.put(msg.getUsername().toLowerCase(), postCount);
        msg.setUserPostCount(postCount);

        chat.addMessage(msg);
    }

    /**
     * This method is called whenever a private message is sent to the PircBot.
     * <p>
     * The implementation of this method in the PircBot abstract class performs no actions and may be overridden as
     * required.
     *
     * @param sender
     *            The nick of the person who sent the private message.
     * @param login
     *            The login of the person who sent the private message.
     * @param hostname
     *            The hostname of the person who sent the private message.
     * @param message
     *            The actual message.
     */
    @Override
    protected void onPrivateMessage(String sender, String login, String hostname, String message)
    {
        logger.info("Private message from " + sender + "(" + login + "@" + hostname + "): " + message);
    }

    @Override
    protected void onSetChannelBan(String channel, String sourceNick, String sourceLogin, String sourceHostname, String hostmask)
    {
        chat.banUser(hostmask);
    }

    @Override
    protected void onRemoveChannelBan(String channel, String sourceNick, String sourceLogin, String sourceHostname, String hostmask)
    {
        chat.unbanUser(hostmask);
    }

    /**
     * This method carries out the actions to be performed when the PircBot gets disconnected. This may happen if the
     * PircBot quits from the server, or if the connection is unexpectedly lost.
     * <p>
     * Disconnection from the IRC server is detected immediately if either we or the server close the connection
     * normally. If the connection to the server is lost, but neither we nor the server have explicitly closed the
     * connection, then it may take a few minutes to detect (this is commonly referred to as a "ping timeout").
     * <p>
     * If you wish to get your IRC bot to automatically rejoin a server after the connection has been lost, then this is
     * probably the ideal method to override to implement such functionality.
     * <p>
     * The implementation of this method in the PircBot abstract class performs no actions and may be overridden as
     * required.
     */
    @Override
    protected void onDisconnect()
    {
        logger.info("Disconnected");
        controlPanel.toggleConnect(false);
    }

    /**
     * Set a reference to the control panel to update the connect/disconnect button text and log events on the
     * Connection tab
     * 
     * @param controlPanel
     */
    public void setControlPanel(ControlPanelIrc controlPanel)
    {
        this.controlPanel = controlPanel;
    }

    /**
     * Set a reference to the message config, used to determine the current username casing resolution type
     * 
     * @param messageConfig
     */
    public void setMessageConfig(ConfigMessage messageConfig)
    {
        this.messageConfig = messageConfig;
    }
}
