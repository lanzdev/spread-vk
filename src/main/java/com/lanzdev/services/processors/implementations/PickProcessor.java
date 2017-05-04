package com.lanzdev.services.processors.implementations;

import com.lanzdev.managers.entity.ChatManager;
import com.lanzdev.managers.entity.SubscriptionManager;
import com.lanzdev.managers.entity.WallManager;
import com.lanzdev.managers.mysql.implementation.MySqlChatManager;
import com.lanzdev.managers.mysql.implementation.MySqlSubscriptionManager;
import com.lanzdev.managers.mysql.implementation.MySqlWallManager;
import com.lanzdev.model.entity.Chat;
import com.lanzdev.model.entity.Subscription;
import com.lanzdev.model.entity.Wall;
import com.lanzdev.services.processors.AbstractProcessor;
import com.lanzdev.services.senders.MessageSender;
import com.lanzdev.services.senders.Sender;
import com.lanzdev.util.MarkdownParser;
import com.lanzdev.vk.group.GroupItem;
import com.lanzdev.vk.group.VkGroupGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.bots.AbsSender;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class PickProcessor extends AbstractProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PickProcessor.class);

    public PickProcessor(Message message, AbsSender absSender) {
        super(message, absSender);
    }

    @Override
    public void process( ) {

        String[] params = message.getText().split(", ");
        LOGGER.debug("Processing pick command, params: {}", Arrays.toString(params));

        ChatManager chatManager = new MySqlChatManager();
        Chat currentChat = chatManager.getById(message.getChatId());
        List<Wall> pickedWalls = new LinkedList<>();

        createSubscriptions(currentChat, params, pickedWalls);
        sendPickedWalls(currentChat, pickedWalls);

        List<String> pickedDomains = pickedWalls.stream()
                .map(Wall::getWallDomain)
                .collect(Collectors.toList());
        LOGGER.debug("{} {} #{} unsubscribed: {}", currentChat.getFirstName(), currentChat.getLastName(),
                currentChat.getId(), pickedDomains.toString());
    }

    private void createSubscriptions(Chat currentChat, String[] params, List<Wall> pickedWalls) {

        WallManager wallManager = new MySqlWallManager();
        SubscriptionManager subscriptionManager = new MySqlSubscriptionManager();
        Arrays.stream(params)
                .forEach((item) -> {
                    Integer wallId = null;
                    Wall wall;
                    Subscription subscription = null;
                    try {
                        wallId = Integer.parseInt(item.trim());
                        wall = wallManager.getById(wallId);
                        subscription = new Subscription();
                        subscription.setChatId(currentChat.getId());
                        subscription.setWallDomain(wall.getWallDomain());
                        subscriptionManager.add(subscription);
                        pickedWalls.add(wall);
                    } catch (Exception e) {
                        LOGGER.error("Cannot create subscription for pick. wall_id = {}.", wallId, e);
                    }
                });
    }

    private void sendPickedWalls(Chat currentChat, List<Wall> pickedWalls) {

        VkGroupGetter groupGetter = new VkGroupGetter();
        List<GroupItem> groupItems = groupGetter.getItems(pickedWalls);
        StringBuilder pickedWallsBuilder = new StringBuilder();

        if (groupItems.size() != 0) {
            pickedWallsBuilder.append("Added:\n");
            groupItems.stream()
                    .forEach(item -> pickedWallsBuilder
                            .append(String.format("%-5d", item.getId()))
                            .append("-  ").append(item.getName()).append("\n"));
            pickedWallsBuilder.deleteCharAt(pickedWallsBuilder.length() - 1);
        } else {
            pickedWallsBuilder.append("Didn't add anything");
        }
        String message = MarkdownParser.parse(pickedWallsBuilder.toString());
        Sender sender = new MessageSender();
        sender.send(bot, currentChat.getId().toString(), message);
    }


}
