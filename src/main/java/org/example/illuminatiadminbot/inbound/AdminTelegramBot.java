package org.example.illuminatiadminbot.inbound;

import it.tdlight.jni.TdApi;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.configuration.TopicProperties;
import org.example.illuminatiadminbot.inbound.menu.MenuBuilder;
import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.menu.MessageBuilder;
import org.example.illuminatiadminbot.inbound.menu.ThemeState;
import org.example.illuminatiadminbot.inbound.model.UploadDetails;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.outbound.model.MinioFileDetail;
import org.example.illuminatiadminbot.service.AdminBotService;
import org.example.illuminatiadminbot.service.SupergroupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.UnbanChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AdminTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final MenuBuilder menuBuilder;
    private final MessageBuilder messageBuilder;
    private final AdminBotService adminBotService;
    private final Map<Long, ConversationContext> contextMap = new ConcurrentHashMap<>();
    private final TopicProperties topicProperties;


    @Value("${telegram.bot.token}")
    private String token;

    @Value("${supergroup.id}")
    private Long supergroupId;

    public AdminTelegramBot(TelegramClient telegramClient, MenuBuilder menuBuilder, MessageBuilder messageBuilder, SupergroupService supergroupService, AdminBotService adminBotService, TopicProperties topicProperties) {
        this.telegramClient = telegramClient;
        this.menuBuilder = menuBuilder;
        this.messageBuilder = messageBuilder;
        this.adminBotService = adminBotService;
        this.topicProperties = topicProperties;
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

        Set<String> allowed = Set.of("reanimatthew", "hehekge");
        String adminNickname = from.getUserName();
        if (adminNickname == null || !allowed.contains(adminNickname.toLowerCase())) {
            System.out.println("Попытка незарегистрированного входа. Пользователь: " + from.getUserName() + ", ID: " + from.getId());
            // просто выходим — дальше ваш код не выполнится
            return;
        }

        // сохраняем chatId приватного чата
        if (update.hasMessage()) {
            Chat chat = update.getMessage().getChat();
            if (!chat.isSuperGroupChat()) {
                String nickname = update.getMessage().getFrom().getUserName();
                adminBotService.saveChatId(nickname, chat.getId());
            }
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

        Long chatId = update.hasMessage()
                ? update.getMessage().getChatId()
                : update.getCallbackQuery().getMessage().getChatId();

        ConversationContext ctx = contextMap.computeIfAbsent(chatId, _ -> new ConversationContext());

        if (update.hasMessage() && update.getMessage().hasText()) {
            text = update.getMessage().getText();
            if (text.equals("/menu") || text.equals("/cancel") || text.equals("/start")) {
                ctx.subscriptionDetails.set(0, "");
                ctx.subscriptionDetails.set(1, "");
                ctx.uploadDetails.clearUploadDetails();
                ctx.adminStatus = "";
                ctx.menuState = MenuState.MAIN_MENU;
                InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                SendMessage menuMessage = messageBuilder.createMessage(update, markup, "Выберите действие:");
                ctx.lastMessageId = safeExecute(menuMessage).getMessageId();
                return;
            }
        }


        if (update.hasCallbackQuery()) {
            data = update.getCallbackQuery().getData();
        }

        switch (ctx.menuState) {
            case MAIN_MENU -> {
                if ("SUBSCRIPTION".equals(data)) {
                    ctx.subscriptionDetails.set(0, "");
                    ctx.subscriptionDetails.set(1, "");
                    ctx.menuState = MenuState.SUBSCRIPTION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getSubscription(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите подписку:");
                    safeExecute(editMessage);
                } else if ("UPLOAD".equals(data)) {
                    ctx.uploadDetails.clearUploadDetails();
                    ctx.menuState = MenuState.THEME_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getTheme(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите топик:");
                    safeExecute(editMessage);
                } else if ("ADMIN".equals(data)) {
                    ctx.adminStatus = "";
                    ctx.menuState = MenuState.ADMIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Добавить или удалить администратора");
                    safeExecute(editMessage);
                } else if ("BAN-USER".equals(data)) {
                    ctx.menuState = MenuState.BAN_USER;
                    InlineKeyboardMarkup markup = menuBuilder.getBanUser(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Введите никнейм пользователя для блокировки:");
                    safeExecute(editMessage);
                }
            }

            case SUBSCRIPTION_MENU -> {
                if (List.of("BASIC", "PREMIUM", "GOLD").contains(data)) {
                    ctx.subscriptionDetails.set(0, data);
                    ctx.subscriptionDetails.set(1, "");
                    ctx.menuState = MenuState.DURATION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getDuration(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nВыберите срок подписки:");
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    ctx.subscriptionDetails.set(0, "");
                    ctx.subscriptionDetails.set(1, "");
                    ctx.menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case DURATION_MENU -> {
                if (List.of("1M", "3M", "6M", "12M").contains(data)) {
                    ctx.subscriptionDetails.set(1, data);
                    ctx.menuState = MenuState.NICKNAME_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getNickname(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nCрок: " + ctx.subscriptionDetails.get(1) + "\nВведите nickname подписчика:");
                    safeExecute(editMessage);
                } else if ("BACK-TO-SUBSCRIPTION".equals(data)) {
                    ctx.subscriptionDetails.set(0, "");
                    ctx.subscriptionDetails.set(1, "");
                    ctx.menuState = MenuState.SUBSCRIPTION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getSubscription(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите подписку:");
                    safeExecute(editMessage);
                }
            }

            case NICKNAME_MENU -> {
                if (!text.isEmpty()) {
                    text = text.trim();
                    if (text.startsWith("@")) {
                        text = text.substring(1);
                    }
                    String subscription = adminBotService.subscribeUser(text, ctx.subscriptionDetails);
                    clearMenu(update, ctx.lastMessageId, subscription);
                } else if ("BACK-TO-DURATION".equals(data)) {
                    ctx.subscriptionDetails.set(1, "");
                    ctx.menuState = MenuState.DURATION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getDuration(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nВыберите срок подписки:");
                    safeExecute(editMessage);
                }
            }

            case THEME_MENU -> {
                if (ThemeState.contains(data)) {
                    ctx.uploadDetails.setTheme(data);
                    ctx.menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Загрузим файл в топик: " + ctx.uploadDetails.getTheme() + "\nВыберите тип контента.");
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    ctx.uploadDetails.clearUploadDetails();
                    ctx.menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case UPLOAD_MENU -> {
                if (List.of("TEXT", "AUDIO", "VIDEO").contains(data)) {
                    ctx.uploadDetails.setFileType(data);
                    ctx.menuState = MenuState.FILE_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getFile(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "В топик " + ctx.uploadDetails.getTheme() + " грузим контент: " + ctx.uploadDetails.getFileType() + "\nЗагрузите файл.");
                    safeExecute(editMessage);
                } else if ("BACK-TO-THEME".equals(data)) {
                    ctx.uploadDetails.clearUploadDetails();
                    ctx.menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case FILE_MENU -> {
                if (update.hasMessage() && update.getMessage().hasDocument()) {
                    String fileId = update.getMessage().getDocument().getFileId();
                    String fileName = update.getMessage().getDocument().getFileName();
                    ctx.uploadDetails.setFileName(fileName);

                    File uploadFile;
                    try {
                        uploadFile = telegramClient.execute(new GetFile(fileId));
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }

                    try (InputStream fileStream = telegramClient.downloadFileAsStream(uploadFile)) {
                        InputFile inputFile = adminBotService.uploadFile(ctx.uploadDetails, fileStream);
                        ctx.uploadDetails.setInputFile(inputFile);
                    } catch (TelegramApiException | IOException e) {
                        throw new RuntimeException(e);
                    }

                    ctx.menuState = MenuState.DESCRIPTION_MENU;
                    clearMenu(update, ctx.lastMessageId, "Файл " + fileName + " загружен.");
                    InlineKeyboardMarkup markup = menuBuilder.getDescription(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Введите описание к файлу.");
                    ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                } else if ("BACK-TO-UPLOAD".equals(data)) {
                    ctx.uploadDetails.clearFileType();
                    ctx.menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите тип контента:");
                    safeExecute(editMessage);
                } else {
                    clearMenu(update, ctx.lastMessageId, "Вы что-то не то делаете. Давайте заново.");
                    ctx.uploadDetails.clearFileType();
                    ctx.menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Выберите тип контента:");
                    ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                }
            }

            case DESCRIPTION_MENU -> {
                if (!text.isEmpty()) {
                    MinioFileDetail minioFileDetail = adminBotService.uploadFileDescription(text, ctx.uploadDetails);

                    try {
                        SendDocument sendDocument = SendDocument.builder()
                                .document(ctx.uploadDetails.getInputFile())
                                .chatId("-100" + supergroupId)
                                .build();
                        sendDocument.setCaption(text);

                        String topicName = ctx.uploadDetails.getTheme().toLowerCase();
                        log.info(topicName);
                        Integer topicId = topicProperties.getMap().get(topicName).getId();
                        sendDocument.setMessageThreadId(topicId);

                        telegramClient.execute(sendDocument);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }

                    uploadContentConfirmation(minioFileDetail.getDescription(), ctx.uploadDetails, update, ctx.lastMessageId);
                } else if ("BACK-TO-UPLOAD".equals(data)) {
                    ctx.uploadDetails.clearUploadDetails();
                    ctx.menuState = MenuState.UPLOAD_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите тип контента:");
                    safeExecute(editMessage);
                }
            }

            case ADMIN_MENU -> {
                if (List.of("ADD", "REMOVE").contains(data)) {
                    ctx.adminStatus = data;
                    ctx.menuState = MenuState.APPOINTMENT_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, adminBotService.adminShow(ctx.adminStatus));
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    ctx.adminStatus = "";
                    ctx.menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case APPOINTMENT_MENU -> {
                if (!text.trim().isEmpty()) {
                    try {
                        Long adminId = Long.parseLong(text);
                        String result = adminBotService.adminAddOrRemove(adminId, ctx.adminStatus);
                        if (!"Администратор не найден".equals(result)) {
                            clearMenu(update, ctx.lastMessageId, result);
                        } else {
                            clearMenu(update, ctx.lastMessageId, result);
                            ctx.menuState = MenuState.APPOINTMENT_MENU;
                            InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                            SendMessage sendMessage = messageBuilder.createMessage(update, markup, adminBotService.adminShow(ctx.adminStatus));
                            ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                        }
                    } catch (NumberFormatException e) {
                        clearMenu(update, ctx.lastMessageId, "Некорректный ID");
                        ctx.menuState = MenuState.APPOINTMENT_MENU;
                        InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                        SendMessage sendMessage = messageBuilder.createMessage(update, markup, adminBotService.adminShow(ctx.adminStatus));
                        ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                    }
                } else if ("BACK-TO-ADMIN".equals(data)) {
                    ctx.adminStatus = "";
                    ctx.menuState = MenuState.ADMIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Добавить или удалить администратора");
                    safeExecute(editMessage);
                }
            }

            case BAN_USER -> {
                String nickname;
                if (!text.isEmpty()) {
                    nickname = text.trim();
                    if (text.startsWith("@")) {
                        nickname = text.substring(1);
                    }

                    if (adminBotService.banUser(nickname)) {
                        clearMenu(update, ctx.lastMessageId, "Пользователь " + nickname + " заблокирован");
                    } else {
                        ctx.menuState = MenuState.BAN_USER;
                        InlineKeyboardMarkup markup = menuBuilder.getBanUser(update);
                        SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Нет такого пользователя.\nВведите никнейм пользователя для удаления:");
                        ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                    }
                } else {
                    InlineKeyboardMarkup markup = menuBuilder.getBanUser(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Пустой никнейм.\nВведите никнейм пользователя для удаления:");
                    ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
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

    private void uploadContentConfirmation(String description, UploadDetails uploadDetails, Update update, Integer lastMessageId) {
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

    @Data
    public static class ConversationContext {
        private final ArrayList<String> subscriptionDetails = new ArrayList<>(List.of("", ""));
        private UploadDetails uploadDetails = new UploadDetails();
        private String adminStatus = "";
        private MenuState menuState = MenuState.MAIN_MENU;
        private Integer lastMessageId = null;
    }
}
