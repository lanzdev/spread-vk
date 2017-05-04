package com.lanzdev;

import com.lanzdev.managers.entity.SubscriptionManager;
import com.lanzdev.managers.entity.WallManager;
import com.lanzdev.managers.mysql.implementation.MySqlSubscriptionManager;
import com.lanzdev.managers.mysql.implementation.MySqlWallManager;
import com.lanzdev.model.entity.Subscription;
import com.lanzdev.model.entity.Wall;
import com.lanzdev.services.senders.MessageSender;
import com.lanzdev.services.senders.PhotoSender;
import com.lanzdev.services.senders.Sender;
import com.lanzdev.util.MarkdownParser;
import com.lanzdev.vk.group.GroupItem;
import com.lanzdev.vk.group.VkGroupGetter;
import com.lanzdev.vk.wall.Photo;
import com.lanzdev.vk.wall.VkWallGetter;
import com.lanzdev.vk.wall.WallItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.bots.AbsSender;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        LOGGER.debug("Enter main().");
        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        MemologyBot bot = new MemologyBot();
        try {
            telegramBotsApi.registerBot(bot);
            LOGGER.info("Bot registered successfully!");
        } catch (TelegramApiRequestException e) {
            LOGGER.error("Cannot register bot.", e);
        }

        Main main = new Main();
        main.distribution(bot);
    }

    /**
     * method which distributes new posts to every user according to their subscriptions
     * @param absSender
     */
    private void distribution(AbsSender absSender) {

        LOGGER.debug("Enter distribution().");
        SubscriptionManager subscriptionManager = new MySqlSubscriptionManager();
        WallManager wallManager = new MySqlWallManager();

        LOGGER.info("Start to distribute posts.");
        new Runnable() {
            @Override
            public void run( ) {

                while (true) {
                    List<Subscription> subscriptions = subscriptionManager.getAll();
                    subscriptions.stream()
                            .forEach(item -> {
                                Wall wall = wallManager.getByDomain(item.getWallDomain());
                                sendMessages(wall, item, absSender);
                                if (subscriptionManager.getById(item.getId()) != null) {
                                    subscriptionManager.update(item);
                                }
                            });
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        LOGGER.error("Thread was interrupted.", e);
                    }
                }
            }
        }.run();
    }

    private void sendMessages(Wall wall, Subscription subscription, AbsSender absSender) {

        VkWallGetter vkWallGetter = new VkWallGetter();
        List<WallItem> wallItems = vkWallGetter.getItems(wall.getWallDomain(), 10, 0);
        List<WallItem> newItems = pickNewest(wallItems, subscription.getLastPostId());
        if (newItems.size() != 0) {
            LOGGER.info("{} new posts found on the wall \"{}\".", newItems.size(), wall.getWallDomain());
            sendMessageHeader(wall, subscription, absSender);
            newItems.stream()
                    .forEach(item -> {
                        sendPostsText(item, subscription, absSender);
                        item.getPhotos().stream()
                                .forEach(photo -> {
                                    sendPostsPhoto(photo, subscription, absSender);
                                });
                    });
            subscription.setLastPostId(newItems.get(newItems.size() - 1).getId());
        }
    }

    /**
     * Sends message header before sending actual messages. Contains wall name in bald style
     * @param wall
     * @param subscription
     * @param absSender
     */
    private void sendMessageHeader(Wall wall, Subscription subscription, AbsSender absSender) {

        GroupItem groupItem = new VkGroupGetter().getItems(Collections.singletonList(wall)).iterator().next();
        StringBuilder sendPublicBuilder = new StringBuilder();
        String groupName = groupItem.getName();
        groupName = MarkdownParser.parse(groupName);
        sendPublicBuilder.append("*").append(groupName).append("*");

        LOGGER.info("Send message header \"{}\" to chat {}",
                sendPublicBuilder.toString(), subscription.getChatId().toString());

        Sender sender = new MessageSender();
        sender.send(absSender, subscription.getChatId().toString(), sendPublicBuilder.toString());
    }

    /**
     * Sends text message to chat
     * @param item
     * @param subscription
     * @param absSender
     */
    private void sendPostsText(WallItem item, Subscription subscription, AbsSender absSender) {

        Sender sender = new MessageSender();
        if (item.getText() != null && !item.getText().isEmpty()) {
            String message = MarkdownParser.parse(item.getText());
            sender.send(absSender, subscription.getChatId().toString(), message);
        }
    }

    /**
     * Sends photo message to chat
     * @param photo
     * @param subscription
     * @param absSender
     */
    private void sendPostsPhoto(Photo photo, Subscription subscription, AbsSender absSender) {

        Sender sender = new PhotoSender();
        if (photo.getSrcBig() != null && !photo.getSrcBig().isEmpty()) {
            sender.send(absSender, subscription.getChatId().toString(), photo.getSrcBig());
        }
    }

    /**
     * Picks newest wall items from list {@param wallItems}
     * @param wallItems
     * @param lastPostId
     * @return
     */
    private List<WallItem> pickNewest(List<WallItem> wallItems, Long lastPostId) {

        List<WallItem> list = new ArrayList<>();

        int last = 0;
        for (int i = 0; i < wallItems.size(); i++) {
            if (wallItems.get(i).getId().equals(lastPostId)) {
                last = i;
                break;
            }
        }

        if (lastPostId != 0) {
            for (int i = last + 1; i < wallItems.size(); i++) {
                list.add(wallItems.get(i));
            }
        } else {
            list = Collections.singletonList(wallItems.get(wallItems.size() - 1));
        }

        return list;
    }
}
