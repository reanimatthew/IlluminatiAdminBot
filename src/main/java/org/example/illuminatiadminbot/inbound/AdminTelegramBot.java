package org.example.illuminatiadminbot.inbound;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.configuration.TopicProperties;
import org.example.illuminatiadminbot.inbound.menu.*;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
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
import org.telegram.telegrambots.meta.api.methods.groupadministration.ExportChatInviteLink;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.PromoteChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final MenuBuilder menuBuilder;
    private final MessageBuilder messageBuilder;
    private final AdminBotService adminBotService;
    private final SupergroupService supergroupService;
    private final Map<Long, ConversationContext> contextMap = new ConcurrentHashMap<>();
    private final TopicProperties topicProperties;
    private final PhoneValidatorAndNormalizer phoneValidatorAndNormalizer;
    private final TelegramGateway telegramGateway;

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${supergroup.id}")
    private long supergroupId;

    @Value("${chat.id}")
    private long supergroupChatId;

    @Override
    public void consume(Update update) {

        //проверка на входе
//        User from = update.hasMessage()
//                ? update.getMessage().getFrom()
//                : update.getCallbackQuery().getFrom();
//        Set<Long> allowed = Set.copyOf(adminBotService.getAllAdminOrCreatorIds());
//        long adminId = from.getId();
//        if (adminId <= 0L || !allowed.contains(adminId)) {
//            System.out.println("Попытка незарегистрированного входа. Пользователь: " + from.getUserName() + ", ID: " + from.getId());
//            // просто выходим — дальше код не выполнится
//            return;
//        }

        String text = "";
        String data = "";
        Long chatId = update.hasMessage()
                ? update.getMessage().getChatId()
                : update.getCallbackQuery().getMessage().getChatId();
        ConversationContext ctx = contextMap.computeIfAbsent(chatId, _ -> new ConversationContext());
        if (ctx.chatId == null)
            ctx.chatId = chatId;

        // сохраняем chatId приватного чата
        if (update.hasMessage()) {
            if (chatId != supergroupChatId) {
                long telegramId = update.getMessage().getFrom().getId();
                adminBotService.saveChatId(telegramId, ctx.chatId);
            }
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().getText().equals("/showMeInfo")) {
                List<GroupUser> groupUsersFromSupergroup = supergroupService.getAllGroupUsers();
                StringBuilder stringBuilder = new StringBuilder();
                for (GroupUser groupUserFromSupergroup : groupUsersFromSupergroup) {
                    stringBuilder.append(groupUserFromSupergroup.toStringSupergroup()).append("\n");
                }
                SendMessage sendMessage = SendMessage.builder()
                        .chatId(update.getMessage().getChatId())
                        .text(stringBuilder.toString())
                        .build();
                telegramGateway.safeExecute(sendMessage);
            }
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().getText().equals("/correctSql")) {
                String message = supergroupService.correctSql();
                SendMessage sendMessage = SendMessage.builder()
                        .chatId(update.getMessage().getChatId())
                        .text(message)
                        .build();
                telegramGateway.safeExecute(sendMessage);
            }
        }


        if (update.hasMessage() && update.getMessage().hasText()) {
            text = update.getMessage().getText();
            if (text.equals("/menu") || text.equals("/cancel") || text.equals("/start")) {
                ctx.subscriptionDetails.set(0, "");
                ctx.subscriptionDetails.set(1, "");
                ctx.subscriptionDetails.set(2, "");
                ctx.uploadDetails.clearUploadDetails();
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
                    ctx.subscriptionDetails.set(2, "");
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
                } else if ("ADMIN-ADD-REMOVE".equals(data)) {
                    ctx.menuState = MenuState.ADMIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Добавить или удалить администратора");
                    safeExecute(editMessage);
                } else if ("BAN-USER".equals(data)) {
                    ctx.menuState = MenuState.BAN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getBanUser(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Введите никнейм пользователя для блокировки:");
                    safeExecute(editMessage);
                }
            }

            case SUBSCRIPTION_MENU -> {
                if (Subscription.contains(data)) {
                    ctx.subscriptionDetails.set(0, data);
                    ctx.subscriptionDetails.set(1, "");
                    ctx.subscriptionDetails.set(2, "");
                    ctx.menuState = MenuState.DURATION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getDuration(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nВыберите срок подписки:");
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    ctx.subscriptionDetails.set(0, "");
                    ctx.subscriptionDetails.set(1, "");
                    ctx.subscriptionDetails.set(2, "");
                    ctx.menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case DURATION_MENU -> {
                if (List.of("1M", "3M", "6M", "12M").contains(data)) {
                    ctx.subscriptionDetails.set(1, data);
                    ctx.subscriptionDetails.set(2, "");
                    ctx.menuState = MenuState.NICK_OR_PHONE_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getNickOrPhone(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nCрок: " + ctx.subscriptionDetails.get(1) + "\nБудете вводить nickname или телефон подписчика?");
                    safeExecute(editMessage);
                } else if ("BACK-TO-SUBSCRIPTION".equals(data)) {
                    ctx.subscriptionDetails.set(0, "");
                    ctx.subscriptionDetails.set(1, "");
                    ctx.subscriptionDetails.set(2, "");
                    ctx.menuState = MenuState.SUBSCRIPTION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getSubscription(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите подписку:");
                    safeExecute(editMessage);
                }
            }

            case NICK_OR_PHONE_MENU -> {
                if (List.of("BY-PHONE", "BY-NICK").contains(data)) {
                    ctx.subscriptionDetails.set(2, data);
                    ctx.menuState = MenuState.SUBSCRIBER_ID_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getSubscriberId(update);
                    String phoneOrNick = "BY-PHONE".equals(data) ? "Введите телефон подписчика" : "Введите никнейм подписчика";
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nСрок: " + ctx.subscriptionDetails.get(1) + "\n" + phoneOrNick);
                    telegramGateway.safeExecute(editMessage);
                } else if ("BACK-TO-DURATION".equals(data)) {
                    ctx.subscriptionDetails.set(2, "");
                    ctx.menuState = MenuState.DURATION_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getDuration(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nВыберите срок подписки:");
                    safeExecute(editMessage);
                }
            }

            case SUBSCRIBER_ID_MENU -> {
                if (!text.isEmpty() && !(text.startsWith("@") && text.length() == 1)) {
                    GroupUser groupUser;
                    String message = "";
                    String nickname = "";
                    String phone = "";
                    long telegramId = -1L;

                    boolean byNick = ctx.subscriptionDetails.get(2).equals("BY-NICK");
                    boolean byPhone = ctx.subscriptionDetails.get(2).equals("BY-PHONE");
                    if (byNick) {
                        nickname = text.trim();

                        if (text.startsWith("@")) {
                            nickname = text.substring(1);
                        }
                    } else if (byPhone) {
                        phone = phoneValidatorAndNormalizer.validateAndNormalize(text);
                        telegramId = supergroupService.getTelegramIdByPhone(phone);
                        if (telegramId < 1) {
                            deleteMenu(update, ctx.lastMessageId);
                            ctx.menuState = MenuState.SUBSCRIBER_ID_MENU;
                            InlineKeyboardMarkup markup = menuBuilder.getSubscriberId(update);
                            SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Пользователя с таким телефоном не существует.\nВведите номер телефона пользователя в формате +79261234567");
                            ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                            return;
                        }
                        nickname = supergroupService.getNicknameByTelegramId(telegramId);
                    }

                    boolean inSql = adminBotService.inSql(nickname);
                    boolean inSupergroup = supergroupService.inSupergroup(nickname);
                    TelegramUserStatus telegramUserStatus;

                    if (!inSql && !inSupergroup) {
                        groupUser = adminBotService.subscribeNewUser(nickname, telegramId, ctx.subscriptionDetails);

                        if (byNick) {
                            sendLink(update, chatId, ctx);
                        } else if (byPhone) {
                            supergroupService.addMemberToSupergroup(groupUser);
                            subscribeAndSendMessage(groupUser, ctx, update);
                        }

                    } else if (!inSql) {
                        groupUser = supergroupService.getGroupUserByNickname(nickname);

                        if (!supergroupService.isOrdinaryMember(groupUser.getTelegramId())) {
                            message = "Пользователь " + nickname + " не является обычным членом группы. Проверяйте.";
                            clearMenu(update, ctx.lastMessageId, message);
                            break;
                        }

                        telegramUserStatus = groupUser.getTelegramUserStatus();
                        if (telegramUserStatus == TelegramUserStatus.BANNED || telegramUserStatus == TelegramUserStatus.RESTRICTED) {
                            telegramGateway.restoreBannedOrRestrictedUser(groupUser);
                        } else if (telegramUserStatus == TelegramUserStatus.LEFT) {
                            supergroupService.addMemberToSupergroup(groupUser);
                        }

                        subscribeAndSendMessage(groupUser, ctx, update);

                    } else {
                        groupUser = adminBotService.getUser(nickname);

                        if (groupUser.isAdminOrCreator()) {
                            message = "Пользователь " + nickname + " не является обычным членом группы. Проверяйте.";
                            clearMenu(update, ctx.lastMessageId, message);
                            return;
                        }

                        // при бане админа он понижается в SQL до MEMBER
                        if (!groupUser.isMember())
                            groupUser.setTelegramUserStatus(TelegramUserStatus.MEMBER);

                        groupUser = subscribeAndSendMessage(groupUser, ctx, update);

                        if (!inSupergroup)
                            supergroupService.addMemberToSupergroup(groupUser);
                    }

                } else if (text.startsWith("@")) {
                    deleteMenu(update, ctx.lastMessageId);
                    ctx.menuState = MenuState.SUBSCRIBER_ID_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getSubscriberId(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Введите нормальный никнейм.");
                    ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
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
                if ("ADD".equals(data)) {
                    ctx.menuState = MenuState.APPOINTMENT_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, adminShow(data));
                    safeExecute(editMessage);
                } else if ("REMOVE".equals(data)) {
                    ctx.menuState = MenuState.DISMISSAL_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getDismissal(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, adminShow(data));
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    ctx.menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case APPOINTMENT_MENU -> {
                if (!text.trim().isEmpty()) {
                    String phone = phoneValidatorAndNormalizer.validateAndNormalize(text);
                    if (phone != null) {
                        GroupUser groupAdmin = supergroupService.addMemberToSupergroup(phone);
                        if (groupAdmin != null) {
                            promoteToAdmin(groupAdmin.getTelegramId());
                            groupAdmin.setSubscriptionType(Subscription.ADMIN);
                            groupAdmin.setSubscriptionDuration(5 * 12);
                            groupAdmin.setSubscriptionExpiration(LocalDate.now().plusYears(5));
                            adminBotService.saveGroupUser(groupAdmin);
                            String message = "Администратор: " + groupAdmin.getNickname() + ", Telegram Id: " + groupAdmin.getTelegramId() + ", добавлен.";
                            clearMenu(update, ctx.lastMessageId, message);
                        } else {
                            deleteMenu(update, ctx.lastMessageId);
                            ctx.menuState = MenuState.APPOINTMENT_MENU;
                            InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                            SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Пользователя с таким телефоном не существует.\nВведите номер телефона аккаунта нового администратора в формате +79261234567");
                            ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                        }
                    } else {
                        deleteMenu(update, ctx.lastMessageId);
                        ctx.menuState = MenuState.APPOINTMENT_MENU;
                        InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                        SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Неправильный номер.\nВведите номер телефона аккаунта нового администратора в формате +79261234567");
                        ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                    }
                } else if ("BACK-TO-ADMIN-ADD-REMOVE".equals(data)) {
                    ctx.menuState = MenuState.ADMIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Добавить или удалить администратора");
                    safeExecute(editMessage);
                }
            }

            case DISMISSAL_MENU -> {
                String nickname;
                if (!text.isEmpty()) {
                    nickname = text.trim();
                    if (text.startsWith("@")) {
                        nickname = text.substring(1);
                    }

                    if (supergroupService.banUser(nickname) && adminBotService.banUser(nickname)) {
                        clearMenu(update, ctx.lastMessageId, "Администратор " + nickname + " удалён");
                    } else {
                        ctx.menuState = MenuState.DISMISSAL_MENU;
                        InlineKeyboardMarkup markup = menuBuilder.getDismissal(update);
                        SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Нет такого nickname.\nВведите никнейм пользователя для удаления:");
                        ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                    }
                } else if ("BACK-TO-ADMIN-ADD-REMOVE".equals(data)) {
                    ctx.menuState = MenuState.ADMIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Добавить или удалить администратора");
                    safeExecute(editMessage);
                }
            }

            case BAN_MENU -> {
                String nickname;
                if (!text.isEmpty()) {
                    nickname = text.trim();
                    if (text.startsWith("@")) {
                        nickname = text.substring(1);
                    }

                    if (supergroupService.banUser(nickname) && adminBotService.banUser(nickname)) {
                        clearMenu(update, ctx.lastMessageId, "Пользователь " + nickname + " заблокирован");
                    } else {
                        ctx.menuState = MenuState.BAN_MENU;
                        InlineKeyboardMarkup markup = menuBuilder.getBanUser(update);
                        SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Нет такого пользователя.\nВведите никнейм пользователя для удаления:");
                        ctx.lastMessageId = safeExecute(sendMessage).getMessageId();
                    }
                } else if ("BACK-TO-MAIN".equals(data)) {

                    ctx.menuState = MenuState.MAIN_MENU;
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }
        }
    }

    private void sendLink(Update update, Long chatId, ConversationContext ctx) {
        ExportChatInviteLink exportChatInviteLink = ExportChatInviteLink.builder()
                .chatId(chatId)
                .build();
        String inviteLink = telegramGateway.safeExecute(exportChatInviteLink);

        SendMessage sendMessage = SendMessage.builder()
                .chatId(update.getMessage().getChatId())
                .text("Следующим сообщением будет ссылка, перешлите её подписчику.")
                .build();
        telegramGateway.safeExecute(sendMessage);

        clearMenu(update, ctx.lastMessageId, inviteLink);
    }


    private GroupUser subscribeAndSendMessage(GroupUser groupUser, ConversationContext ctx, Update update) {
        groupUser = adminBotService.subscribeExistedGroupUser(groupUser, ctx.subscriptionDetails);
        String message = "Пользователь: " + groupUser.getNickname() + " подписка: " + ctx.subscriptionDetails.get(0) + ", на срок: " + ctx.subscriptionDetails.get(1);
        clearMenu(update, ctx.lastMessageId, message);
        return groupUser;
    }

    private void promoteToAdmin(long userId) {
        if (!isUserAdmin(userId)) {
            PromoteChatMember promoteChatMember = PromoteChatMember.builder()
                    .chatId("-100" + supergroupId)
                    .userId(userId)
                    .canChangeInformation(true)
                    .canDeleteMessages(true)
                    .canInviteUsers(true)
                    .canPinMessages(true)
                    .canPromoteMembers(true)
                    .build();
            telegramGateway.safeExecute(promoteChatMember);
        }
    }

    public boolean isUserAdmin(long userId) {
        GetChatMember getChatMember = GetChatMember.builder()
                .chatId("-100" + supergroupId)
                .userId(userId)
                .build();
        ChatMember chatMember = telegramGateway.safeExecute(getChatMember);
        return TelegramUserStatus.is(chatMember.getStatus(), TelegramUserStatus.ADMINISTRATOR) || TelegramUserStatus.is(chatMember.getStatus(), TelegramUserStatus.CREATOR);
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

    private void deleteMenu(Update update, Integer messageId) {
        String chatId = update.hasMessage()
                ? update.getMessage().getChatId().toString()
                : update.getCallbackQuery().getMessage().getChatId().toString();

        // Строим запрос на удаление сообщения
        DeleteMessage deleteMessage = DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();

        // Выполняем безопасно, как у тебя в safeExecute
        telegramGateway.safeExecute(deleteMessage);
    }

    private void uploadContentConfirmation(String description, UploadDetails uploadDetails, Update update, Integer lastMessageId) {
        String trimmedDescription;
        if (description.length() > 10) {
            trimmedDescription = description.substring(0, 10) + "...";
        } else {
            trimmedDescription = description;
        }
        String upload = "Загружен контент: " + uploadDetails.getFileType() + ", описание: " + trimmedDescription;
        clearMenu(update, lastMessageId, upload);
    }

    public String adminShow(String addOrRemove) {
        String result = addOrRemove.equals("ADD") ? "Добавление администратора\n\n" : "Удаление администратора\n\n";
        List<GroupUser> admins = adminBotService.adminShow();
        String adminNickname;
        if (admins.isEmpty()) {
            adminNickname = "В БД нет администраторов.\n";
        } else {
            adminNickname = admins.stream()
                    .filter(GroupUser::isActive)
                    .map(GroupUser::getNickname)
                    .collect(Collectors.joining("\n"));
        }
        result += "Существующие администраторы:\n" + adminNickname + "\n\n";
        return addOrRemove.equals("ADD")
                ? result + "Введите номер телефона аккаунта нового администратора в формате +79261234567"
                : result + "Введите nickname удаляемого администратора";
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
        private final ArrayList<String> subscriptionDetails = new ArrayList<>(List.of("", "", ""));
        private UploadDetails uploadDetails = new UploadDetails();
        private MenuState menuState = MenuState.MAIN_MENU;
        private Integer lastMessageId = null;
        private Long chatId = null;
    }
}
