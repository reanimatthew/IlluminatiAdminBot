package org.example.illuminatiadminbot.inbound;

import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.inbound.menu.MenuBuilder;
import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.menu.MessageBuilder;
import org.example.illuminatiadminbot.inbound.menu.ThemeState;
import org.example.illuminatiadminbot.inbound.model.UploadDetails;
import org.example.illuminatiadminbot.outbound.model.MinioFileDetail;
import org.example.illuminatiadminbot.service.AdminBotService;
import org.example.illuminatiadminbot.service.SupergroupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Component
public class AdminTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final MenuBuilder menuBuilder;
    private final MessageBuilder messageBuilder;
    private final ArrayList<String> subscriptionDetails = new ArrayList<>(List.of("", ""));
    private UploadDetails uploadDetails;
    private String adminStatus = "";
    private MenuState menuState = MenuState.MAIN_MENU;
    private Integer lastMessageId = null;
    private final SupergroupService supergroupService;

    private final AdminBotService adminBotService;

    @Value("${telegram.bot.token}")
    private String token;

    public AdminTelegramBot(TelegramClient telegramClient, MenuBuilder menuBuilder, MessageBuilder messageBuilder, SupergroupService supergroupService, AdminBotService adminBotService) {
        this.telegramClient = telegramClient;
        this.menuBuilder = menuBuilder;
        this.messageBuilder = messageBuilder;
        this.supergroupService = supergroupService;
        this.adminBotService = adminBotService;
        uploadDetails = new UploadDetails();
    }

    @Override
    public void consume(Update update) {
        String text = "";
        String data = "";


//        try {
//            log.warn(supergroupService.getAllMembers());
//        } catch (ExecutionException e) {
//            throw new RuntimeException(e);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } catch (TimeoutException e) {
//            throw new RuntimeException(e);
//        }

//        log.warn(update.getMessage().getChatId().toString());

//        try {
//            SendMessage sendMessage = SendMessage.builder()
//                    .chatId(-1002844998465L)
//                    .messageThreadId(16)
//                    .text("Тестовое сообщение в Charisma")
//                    .build();
//            telegramClient.execute(sendMessage);
//        } catch (TelegramApiException e) {
//            throw new RuntimeException(e);
//        }

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
                uploadDetails.clearUploadDetails();
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
                    uploadDetails.clearUploadDetails();
                    menuState = MenuState.THEME_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getTheme(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите топик:");
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

            case THEME_MENU -> {
                if (ThemeState.contains(data)) {
                    uploadDetails.setTheme(data);
                    menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Загрузим файл в топик: " + uploadDetails.getTheme() + "\nВыберите тип контента.");
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    uploadDetails.clearUploadDetails();
                    menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case UPLOAD_MENU -> {
                if (List.of("TEXT", "AUDIO", "VIDEO").contains(data)) {
                    uploadDetails.setFileType(data);
                    menuState = MenuState.FILE_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getFile(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Контент: " + uploadDetails.getFileType() + "\nЗагрузите файл.");
                    safeExecute(editMessage);
                } else if ("BACK-TO-THEME".equals(data)) {
                    uploadDetails.clearUploadDetails();
                    menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case FILE_MENU -> {
                if (update.hasMessage() && update.getMessage().hasDocument()) {
                    String fileId = update.getMessage().getDocument().getFileId();
                    String fileName = update.getMessage().getDocument().getFileName();
                    uploadDetails.setFileName(fileName);

                    File uploadFile;
                    try {
                        uploadFile = telegramClient.execute(new GetFile(fileId));
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }

                    try (InputStream fileStream = telegramClient.downloadFileAsStream(uploadFile)) {
                        String minioFilePath = adminBotService.uploadFile(uploadDetails, fileName, fileStream);
                        uploadDetails.setFilePath(minioFilePath);
                    } catch (TelegramApiException | IOException e) {
                        throw new RuntimeException(e);
                    }

                    menuState = MenuState.DESCRIPTION_MENU;
                    clearMenu(update, lastMessageId, "Файл " + fileName + " загружен.");
                    InlineKeyboardMarkup markup = menuBuilder.getDescription(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Введите описание к файлу.");
                    lastMessageId = safeExecute(sendMessage).getMessageId();
                } else if ("BACK-TO-UPLOAD".equals(data)) {
                    uploadDetails.clearFileType();
                    menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, lastMessageId, markup, "Выберите тип контента:");
                    safeExecute(editMessage);
                } else {
                    clearMenu(update, lastMessageId, "Вы что-то не то делаете. Давайте заново.");
                    uploadDetails.clearFileType();
                    menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Выберите тип контента:");
                    lastMessageId = safeExecute(sendMessage).getMessageId();
                }
            }

            case DESCRIPTION_MENU -> {
                if (!text.isEmpty()) {
                    MinioFileDetail minioFileDetail = adminBotService.uploadFileDescription(text, uploadDetails);
                    uploadContentConfirmation(minioFileDetail.getDescription(), uploadDetails, update);
                } else if ("BACK-TO-UPLOAD".equals(data)) {
                    uploadDetails.clearUploadDetails();
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


    private void uploadContentConfirmation(String description, UploadDetails uploadDetails, Update update) {
        String trimedDescription;
        if (description.length() > 10) {
            trimedDescription = description.substring(0, 10) + "...";
        } else {
            trimedDescription = description;
        }
        String upload = "Загружен контент: " + uploadDetails.getFileType() + ", описание: " + trimedDescription;
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
