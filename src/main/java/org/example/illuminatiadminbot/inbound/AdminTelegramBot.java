package org.example.illuminatiadminbot.inbound;

import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.inbound.menu.MenuBuilder;
import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.menu.MessageBuilder;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.service.AdminBotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class AdminTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final MenuBuilder menuBuilder;
    private final MessageBuilder messageBuilder;
    private final ArrayList<String> subscriptionDetails = new ArrayList<>(List.of("", ""));
    private final ArrayList<String> uploadDetails = new ArrayList<>(List.of("", ""));
    private String adminStatus = "";
    private MenuState menuState = MenuState.MAIN_MENU;
    private Integer lastMessageId = null;

    private final AdminBotService adminBotService;

    @Value("${telegram.bot.token}")
    private String token;

    public AdminTelegramBot(TelegramClient telegramClient, MenuBuilder menuBuilder, MessageBuilder messageBuilder, AdminBotService adminBotService) {
        this.telegramClient = telegramClient;
        this.menuBuilder = menuBuilder;
        this.messageBuilder = messageBuilder;
        this.adminBotService = adminBotService;
    }

    @Override
    public void consume(Update update) {
        String text = "";
        String data = "";

        User from = update.hasMessage()
                ? update.getMessage().getFrom()
                : update.getCallbackQuery().getFrom();

        //
        Set<String> allowed = Set.of("reanimatthew", "hehekge");
        String adminNickname = from.getUserName();
        if (adminNickname == null || !allowed.contains(adminNickname.toLowerCase())) {
            System.out.println("Попытка незарегистрированного входа. Пользователь: " + from.getUserName() + ", ID: " + from.getId());
            // просто выходим — дальше ваш код не выполнится
            return;
        }

        // TODO перенести в UpdateSqlTelegramBot
        // метод скачивает администраторов из группы и обновляет Postgres
        if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().getText().equals("/initializeThisBeautifulBot")) {
                String message = adminBotService.loadUsers();
                SendMessage sendMessage = SendMessage.builder()
                        .text(message)
                        .build();
                try {
                    telegramClient.execute(sendMessage);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            text = update.getMessage().getText();
            if (text.equals("/menu") || text.equals("/cancel") || text.equals("/start")) {
                subscriptionDetails.set(0, "");
                subscriptionDetails.set(1, "");
                uploadDetails.set(0, "");
                uploadDetails.set(1, "");
                adminStatus = "";
                menuState = MenuState.MAIN_MENU;
                InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                SendMessage menuMessage = messageBuilder.createMessage(update, markup, "Выберите действие:");
                lastMessageId = safeExecute(menuMessage).getMessageId();
                return;
            }
        }


        if (update.hasCallbackQuery()) {
            data = update.getCallbackQuery().getData();
        }

        switch (menuState) {
            case MAIN_MENU -> {
                if ("SUBSCRIPTION".equals(data)) {
                    subscriptionDetails.set(0, "");
                    subscriptionDetails.set(1, "");
                    menuState = MenuState.SUBSCRIPTION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getSubscription(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите подписку:");
                    safeExecute(editMessage);
                } else if ("UPLOAD".equals(data)) {
                    uploadDetails.set(0, "");
                    uploadDetails.set(1, "");
                    menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите тип контента:");
                    safeExecute(editMessage);
                } else if ("ADMIN".equals(data)) {
                    adminStatus = "";
                    menuState = MenuState.ADMIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Добавить или удалить администратора");
                    safeExecute(editMessage);
                }
            }

            case SUBSCRIPTION_MENU -> {
                if (List.of("BASIC", "PREMIUM", "GOLD").contains(data)) {
                    subscriptionDetails.set(0, data);
                    subscriptionDetails.set(1, "");
                    menuState = MenuState.DURATION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getDuration(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Подписка: " + subscriptionDetails.get(0) + "\nВыберите срок подписки:");
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    subscriptionDetails.set(0, "");
                    subscriptionDetails.set(1, "");
                    menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case DURATION_MENU -> {
                if (List.of("1M", "3M", "6M", "12M").contains(data)) {
                    subscriptionDetails.set(1, data);
                    menuState = MenuState.NICKNAME_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getNickname(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Подписка: " + subscriptionDetails.get(0) + "\nCрок: " + subscriptionDetails.get(1) + "\nВведите nickname подписчика:");
                    safeExecute(editMessage);
                } else if ("BACK-TO-SUBSCRIPTION".equals(data)) {
                    subscriptionDetails.set(0, "");
                    subscriptionDetails.set(1, "");
                    menuState = MenuState.SUBSCRIPTION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getSubscription(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите подписку:");
                    safeExecute(editMessage);
                }
            }

            case NICKNAME_MENU -> {
                if (!text.isEmpty()) {
                    text = text.trim();
                    if (text.startsWith("@")) {
                        text = text.substring(1);
                    }
                    String subscription = adminBotService.subscribeUser(text, subscriptionDetails);
                    clearMenu(update, lastMessageId, subscription);
                } else if ("BACK-TO-DURATION".equals(data)) {
                    subscriptionDetails.set(1, "");
                    menuState = MenuState.DURATION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getDuration(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Подписка: " + subscriptionDetails.get(0) + "\nВыберите срок подписки:");
                    safeExecute(editMessage);
                }
            }

            case UPLOAD_MENU -> {
                if (List.of("TEXT", "AUDIO", "VIDEO").contains(data)) {
                    uploadDetails.set(0, data);
                    uploadDetails.set(1, "");
                    menuState = MenuState.FILE_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getFile(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Контент: " + uploadDetails.get(0) + "\nЗагрузите файл.");
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    uploadDetails.set(0, "");
                    uploadDetails.set(1, "");
                    menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case FILE_MENU -> {
                if ("TEXT".equals(uploadDetails.get(0)) && update.hasMessage() && update.getMessage().hasDocument()) {
                    String fileId = update.getMessage().getDocument().getFileId();
                    uploadDetails.set(1, fileId);
                    adminBotService.uploadFile(uploadDetails);
                    menuState = MenuState.DESCRIPTION_MENU;
                    clearMenu(update, lastMessageId, "Документ загружен");
                    InlineKeyboardMarkup markup = menuBuilder.getDescription(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Введите описание");
                    lastMessageId = safeExecute(sendMessage).getMessageId();
                } else if ("AUDIO".equals(uploadDetails.get(0)) && update.hasMessage() && update.getMessage().hasAudio()) {
                    String fileId = update.getMessage().getAudio().getFileId();
                    uploadDetails.set(1, fileId);
                    menuState = MenuState.DESCRIPTION_MENU;
                    clearMenu(update, lastMessageId, "Аудио загружено");
                    InlineKeyboardMarkup markup = menuBuilder.getDescription(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Введите описание");
                    lastMessageId = safeExecute(sendMessage).getMessageId();
                } else if ("VIDEO".equals(uploadDetails.get(0)) && update.hasMessage() && update.getMessage().hasVideo()) {
                    String fileId = update.getMessage().getVideo().getFileId();
                    uploadDetails.set(1, fileId);
                    menuState = MenuState.DESCRIPTION_MENU;
                    clearMenu(update, lastMessageId, "Видео загружено");
                    InlineKeyboardMarkup markup = menuBuilder.getDescription(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Введите описание");
                    lastMessageId = safeExecute(sendMessage).getMessageId();
                } else if ("BACK-TO-UPLOAD".equals(data)) {
                    uploadDetails.set(0, "");
                    uploadDetails.set(1, "");
                    menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите тип контента:");
                    safeExecute(editMessage);
                } else {
                    clearMenu(update, lastMessageId, "Несоответствие файла указанному типу.");
                    uploadDetails.set(0, "");
                    uploadDetails.set(1, "");
                    menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Выберите тип контента:");
                    lastMessageId = safeExecute(sendMessage).getMessageId();
                }
            }

            case DESCRIPTION_MENU -> {
                if (!text.isEmpty()) {
                    uploadContent(text, uploadDetails, update);
                } else if ("BACK-TO-UPLOAD".equals(data)) {
                    uploadDetails.set(0, "");
                    uploadDetails.set(1, "");
                    menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите тип контента:");
                    safeExecute(editMessage);
                }
            }

            case ADMIN_MENU -> {
                if (List.of("ADD", "REMOVE").contains(data)) {
                    adminStatus = data;
                    menuState = MenuState.APPOINTMENT_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, adminBotService.adminShow(adminStatus));
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    adminStatus = "";
                    menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case APPOINTMENT_MENU -> {
                if (!text.trim().isEmpty()) {
                    try {
                        Long adminId = Long.parseLong(text);
                        String result = adminBotService.adminAddOrRemove(adminId, adminStatus);
                        if (!"Администратор не найден".equals(result)) {
                            clearMenu(update, lastMessageId, result);
                        } else {
                            clearMenu(update, lastMessageId, result);
                            menuState = MenuState.APPOINTMENT_MENU;
                            InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                            SendMessage sendMessage = messageBuilder.createMessage(update, markup, adminBotService.adminShow(adminStatus));
                            lastMessageId = safeExecute(sendMessage).getMessageId();
                        }
                    } catch (NumberFormatException e) {
                        clearMenu(update, lastMessageId, "Некорректный ID");
                        menuState = MenuState.APPOINTMENT_MENU;
                        InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                        SendMessage sendMessage = messageBuilder.createMessage(update, markup, adminBotService.adminShow(adminStatus));
                        lastMessageId = safeExecute(sendMessage).getMessageId();
                    }
                } else if ("BACK-TO-ADMIN".equals(data)) {
                    adminStatus = "";
                    menuState = MenuState.ADMIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Добавить или удалить администратора");
                    safeExecute(editMessage);
                }
            }
        }
    }

    private Message safeExecute(SendMessage menuMessage) {
        Message message;
        try {
            message = telegramClient.execute(menuMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        return message;
    }

    private void safeExecute(EditMessageText editMessage) {
        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void clearMenu(Update update, Integer messageId, String text) {

        String chatId = update.hasMessage()
                ? update.getMessage().getChatId().toString()
                : update.getCallbackQuery().getMessage().getChatId().toString();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .replyMarkup(new InlineKeyboardMarkup(Collections.emptyList()))
                .build();

        safeExecute(editMessage);
    }



    private void uploadContent(String description, ArrayList<String> uploadDetails, Update update) {
        String trimedDescription;
        if (description.length() > 10) {
            trimedDescription = description.substring(0, 10) + "...";
        } else {
            trimedDescription = description;
        }
        String upload = "Загружен контент: " + uploadDetails.get(0) + ", fileId: " + uploadDetails.get(1) + ", описание: " + trimedDescription;
        clearMenu(update, lastMessageId, upload);
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }
}
