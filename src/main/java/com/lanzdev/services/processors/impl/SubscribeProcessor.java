package com.lanzdev.services.processors.impl;

import com.lanzdev.managers.entity.ChatManager;
import com.lanzdev.managers.entity.SubscriptionManager;
import com.lanzdev.managers.entity.WallManager;
import com.lanzdev.managers.mysql.impl.MySqlChatManager;
import com.lanzdev.managers.mysql.impl.MySqlSubscriptionManager;
import com.lanzdev.managers.mysql.impl.MySqlWallManager;
import com.lanzdev.domain.Chat;
import com.lanzdev.domain.Subscription;
import com.lanzdev.domain.Wall;
import com.lanzdev.services.processors.AbstractProcessor;
import com.lanzdev.services.senders.MessageSender;
import com.lanzdev.services.senders.Sender;
import com.lanzdev.util.Parser;
import com.lanzdev.util.Regex;
import com.lanzdev.vk.group.PublicItem;
import com.lanzdev.vk.group.VkPublicChecker;
import com.lanzdev.vk.group.VkPublicGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.bots.AbsSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class SubscribeProcessor extends AbstractProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeProcessor.class);

    public SubscribeProcessor(Message message, AbsSender absSender) {
        super(message, absSender);
    }

    @Override
    public void process( ) {
        LOGGER.debug("Processing subscribe command.");
        ChatManager chatManager = new MySqlChatManager();
        Chat currentChat = chatManager.getById(message.getChatId());
        String[] params = message.getText().split("\\s+");
        Arrays.stream(params).forEach(item -> item = item.toLowerCase());
        List<Wall> subscribedWalls = new LinkedList<>();
        createSubscriptions(currentChat, params, subscribedWalls);
        sendSubscribedWalls(currentChat, subscribedWalls);
        List<String> subscribedDomains = subscribedWalls.stream()
                .map(Wall::getWallDomain)
                .collect(Collectors.toList());
        LOGGER.debug("Subscribed: {}", subscribedDomains.toString());
    }

    private void createSubscriptions(Chat currentChat, String[] params, List<Wall> subscribedWalls) {
        WallManager wallManager = new MySqlWallManager();
        SubscriptionManager subscriptionManager = new MySqlSubscriptionManager();
        Arrays.stream(params)
                .forEach(item -> {
                    String regex = "com\\/(.*)";
                    String domain = Regex.getDomain(regex, item, 1);
                    if (!VkPublicChecker.contains(domain)) {
                        return;
                    }
                    Wall wall = getWall(wallManager, domain);
                    Subscription subscription = subscriptionManager.getByChatAndWall(
                            currentChat.getId(), wall.getWallDomain());
                    if (subscription == null) {
                        subscription = new Subscription();
                        subscription.setChatId(currentChat.getId());
                        subscription.setWallDomain(domain);
                        subscriptionManager.add(subscription);
                        wall = wallManager.getByDomain(domain);
                        subscribedWalls.add(wall);
                    } else {
                        if (subscription.isActive()) {
                            PublicItem publicItem = VkPublicGetter.getItems(Collections.singletonList(wall))
                                    .iterator().next();
                            String alreadySubscribedString =
                                    String.format("You have already subscribed public: %s", publicItem.getName());
                            Sender sender = new MessageSender();
                            sender.send(bot, currentChat.getId().toString(), alreadySubscribedString);
                        } else {
                            subscription.setActive(true);
                            subscriptionManager.update(subscription);
                            wall = wallManager.getByDomain(domain);
                            subscribedWalls.add(wall);
                        }
                    }
                });
    }

    private Wall getWall(WallManager wallManager, String domain) {
        Wall wall = wallManager.getByDomain(domain);
        if (wall == null) {
            wall = new Wall();
            wall.setWallDomain(domain);
            wall.setApproved(false);
            wallManager.add(wall);
        }
        return wall;
    }

    private void sendSubscribedWalls(Chat currentChat, List<Wall> subscribedWalls) {
        VkPublicGetter groupGetter = new VkPublicGetter();
        List<PublicItem> publicItems = groupGetter.getItems(subscribedWalls);
        StringBuilder builder = new StringBuilder();

        if (publicItems.size() != 0) {
            builder.append("Subscribed:\n");
            publicItems.stream()
                    .forEach(item -> builder
                            .append(String.format("%-5d", item.getId()))
                            .append("-  ").append(item.getName()).append("\n"));
            builder.deleteCharAt(builder.length() - 1);
        } else {
            builder.append("Didn't subscribe on anything.");
        }
        String message = Parser.parseMarkdown(builder.toString());
        Sender sender = new MessageSender();
        sender.send(bot, currentChat.getId().toString(), message);
    }
}
