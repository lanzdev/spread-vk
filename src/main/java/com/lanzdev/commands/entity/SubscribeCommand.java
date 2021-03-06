package com.lanzdev.commands.entity;

import com.lanzdev.commands.AbstractCommand;
import com.lanzdev.commands.Commands;
import com.lanzdev.services.senders.MessageSender;
import com.lanzdev.services.senders.Sender;
import com.lanzdev.util.Util;
import org.telegram.telegrambots.api.objects.Chat;
import org.telegram.telegrambots.api.objects.User;
import org.telegram.telegrambots.bots.AbsSender;

public class SubscribeCommand extends AbstractCommand {

    public SubscribeCommand() {
        super(Commands.SUBSCRIBE, "Subscribe to new wall");
    }

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {

        String msgHeader = "*Subscribe*";
        String msgBody = "Please enter walls url:";
        StringBuilder msgPause = new StringBuilder();
        Util.appendPauseChecking(msgPause, chat.getId());

        Sender sender = new MessageSender();
        sender.send(absSender, chat.getId().toString(), msgHeader);
        if (msgPause.length() != 0) {
            sender.send(absSender, chat.getId().toString(), msgPause.toString());
        }
        sender.send(absSender, chat.getId().toString(), msgBody);

        updateChatLastCommand(chat.getId(), Commands.SUBSCRIBE);
    }

}
