package org.example.illuminatiadminbot.inbound;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.configuration.TopicProperties;
import org.example.illuminatiadminbot.inbound.handler.*;
import org.example.illuminatiadminbot.inbound.menu.*;
import org.example.illuminatiadminbot.inbound.model.EventType;
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
import org.telegram.telegrambots.meta.api.methods.groupadministration.CreateChatInviteLink;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.PromoteChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.ChatInviteLink;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
    private final HandlerRegistry handlerRegistry;
    private final ConversationContext ctx;

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${supergroup.id}")
    private long supergroupId;

    @Value("${chat.id}")
    private long supergroupChatId;

    @PostConstruct
    public void init() {
        handlerRegistry.register(new DurationMenuHandler(new MenuBuilder()));
        handlerRegistry.register(new SubscriptionMenuHandler(new MenuBuilder()));
        handlerRegistry.register(new NickOrPhoneHandler(new MenuBuilder()));
    }

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
        ConversationContext ctx = contextMap.computeIfAbsent(chatId, c -> new ConversationContext());

        EventType eventType = update.hasCallbackQuery() ? EventType.CALLBACK : EventType.TEXT;
        MenuState menuState = ctx.getMenuState();

        if (ctx.getChatId() == null)
            ctx.setChatId(chatId);

        // сохраняем chatId приватного чата
        if (update.hasMessage()) {
            if (chatId != supergroupChatId) {
                long telegramId = update.getMessage().getFrom().getId();
                adminBotService.saveChatId(telegramId, ctx.getChatId());
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
                ctx.clearContext();
                return;
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
                ctx.clearContext();
                return;
            }
        }

        if (update.hasCallbackQuery()) {
            data = update.getCallbackQuery().getData();
        }

        handlerRegistry.resolve(menuState, eventType)
                .ifPresent(handler -> {
                            HandlerResult result = handler.handle(update, ctx);
                            result.getActions().forEach(action -> action.execute(telegramGateway, update, ctx));
                            result.updateContext(ctx);
                            ctx.setMenuState(result.getNextMenuState());
                        }
                );

        if (update.hasMessage() && update.getMessage().hasText()) {
            text = update.getMessage().getText();
            if (text.equals("/menu") || text.equals("/cancel") || text.equals("/start")) {
                ctx.clearContext();
                ctx.setMenuState(MenuState.MAIN_MENU);
                InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                SendMessage menuMessage = messageBuilder.createMessage(update, markup, "Выберите действие:");
                Message message = telegramGateway.safeExecute(menuMessage);
                ctx.setLastMessageId(message.getMessageId());
                return;
            }
        }

        switch (ctx.getMenuState()) {
            case MAIN_MENU -> {
                if ("SUBSCRIPTION".equals(data)) {
                    ctx.getSubscriptionDetails().set(0, "");
                    ctx.getSubscriptionDetails().set(1, "");
                    ctx.getSubscriptionDetails().set(2, "");
                    ctx.setMenuState(MenuState.SUBSCRIPTION_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getSubscription(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Выберите подписку:");
                    safeExecute(editMessage);
                } else if ("UPLOAD".equals(data)) {
                    ctx.getUploadDetails().clearUploadDetails();
                    ctx.setMenuState(MenuState.THEME_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getTheme(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Выберите топик:");
                    safeExecute(editMessage);
                } else if ("ADMIN-ADD-REMOVE".equals(data)) {
                    ctx.setMenuState(MenuState.ADMIN_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Добавить или удалить администратора");
                    safeExecute(editMessage);
                } else if ("BAN-USER".equals(data)) {
                    ctx.setMenuState(MenuState.BAN_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getBanUser(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Введите никнейм пользователя для блокировки:");
                    safeExecute(editMessage);
                } else if ("SHOW-SQL".equals(data)) {
                    List<GroupUser> groupUsersFromSql = adminBotService.getAllUsers();
                    StringBuilder sqlUsers = new StringBuilder("Пользователи из базы данных:\n\n");
                    for (GroupUser groupUserFromSql : groupUsersFromSql) {
                        sqlUsers.append(groupUserFromSql.toStringSupergroup()).append("\n");
                    }
                    EditMessageText editMessageText = EditMessageText.builder()
                            .chatId(chatId)
                            .messageId(ctx.getLastMessageId())
                            .text(sqlUsers.toString())
                            .replyMarkup(new InlineKeyboardMarkup(Collections.emptyList()))
                            .build();
                    telegramGateway.safeExecute(editMessageText);
                    ctx.clearContext();
                } else if ("SHOW-SUPERGROUP".equals(data)) {
                    List<GroupUser> groupUsersFromSupergroup = supergroupService.getAllGroupUsers();
                    StringBuilder supergroupUsers = new StringBuilder("В супергруппу входят:\n\n");
                    for (GroupUser groupUserFromSupergroup : groupUsersFromSupergroup) {
                        supergroupUsers.append(groupUserFromSupergroup.toStringSupergroup()).append("\n");
                    }
                    EditMessageText editMessageText = EditMessageText.builder()
                            .chatId(chatId)
                            .messageId(ctx.getLastMessageId())
                            .text(supergroupUsers.toString())
                            .replyMarkup(new InlineKeyboardMarkup(Collections.emptyList()))
                            .build();
                    telegramGateway.safeExecute(editMessageText);
                    ctx.clearContext();
                }
            }

//            case SUBSCRIPTION_MENU -> {
//                if (Subscription.contains(data)) {
//                    ctx.subscriptionDetails.set(0, data);
//                    ctx.subscriptionDetails.set(1, "");
//                    ctx.subscriptionDetails.set(2, "");
//                    ctx.menuState = MenuState.DURATION_MENU;
//                    InlineKeyboardMarkup markup = menuBuilder.getDuration(update);
//                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nВыберите срок подписки:");
//                    safeExecute(editMessage);
//                } else if ("BACK-TO-MAIN".equals(data)) {
//                    ctx.subscriptionDetails.set(0, "");
//                    ctx.subscriptionDetails.set(1, "");
//                    ctx.subscriptionDetails.set(2, "");
//                    ctx.menuState = MenuState.MAIN_MENU;
//                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
//                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите действие:");
//                    safeExecute(editMessage);
//                }
//            }

//            case DURATION_MENU -> {
//                if (List.of("1M", "3M", "6M", "12M").contains(data)) {
//                    ctx.subscriptionDetails.set(1, data);
//                    ctx.subscriptionDetails.set(2, "");
//                    ctx.menuState = MenuState.NICK_OR_PHONE_MENU;
//                    InlineKeyboardMarkup markup = menuBuilder.getNickOrPhone(update);
//                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nCрок: " + ctx.subscriptionDetails.get(1) + "\nБудете вводить nickname или телефон подписчика?");
//                    safeExecute(editMessage);
//                } else if ("BACK-TO-SUBSCRIPTION".equals(data)) {
//                    ctx.subscriptionDetails.set(0, "");
//                    ctx.subscriptionDetails.set(1, "");
//                    ctx.subscriptionDetails.set(2, "");
//                    ctx.menuState = MenuState.SUBSCRIPTION_MENU;
//                    InlineKeyboardMarkup markup = menuBuilder.getSubscription(update);
//                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Выберите подписку:");
//                    safeExecute(editMessage);
//                }
//            }

//            case NICK_OR_PHONE_MENU -> {
//                if (List.of("BY-PHONE", "BY-NICK").contains(data)) {
//                    ctx.subscriptionDetails.set(2, data);
//                    ctx.menuState = MenuState.SUBSCRIBER_ID_MENU;
//                    InlineKeyboardMarkup markup = menuBuilder.getSubscriberId(update);
//                    String phoneOrNick = "BY-PHONE".equals(data) ? "Введите телефон подписчика" : "Введите никнейм подписчика";
//                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nСрок: " + ctx.subscriptionDetails.get(1) + "\n" + phoneOrNick);
//                    telegramGateway.safeExecute(editMessage);
//                } else if ("BACK-TO-DURATION".equals(data)) {
//                    ctx.subscriptionDetails.set(2, "");
//                    ctx.menuState = MenuState.DURATION_MENU;
//                    InlineKeyboardMarkup markup = menuBuilder.getDuration(update);
//                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.lastMessageId, markup, "Подписка: " + ctx.subscriptionDetails.get(0) + "\nВыберите срок подписки:");
//                    safeExecute(editMessage);
//                }
//            }

            case SUBSCRIBER_ID_MENU -> {
                if (update.hasCallbackQuery() && "BACK-TO-DURATION".equals(data)) {
                    ctx.getSubscriptionDetails().set(1, "");
                    ctx.setMenuState(MenuState.DURATION_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getDuration(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Подписка: " + ctx.getSubscriptionDetails().get(0) + "\nВыберите срок подписки:");
                    safeExecute(editMessage);
                } else if (!text.trim().isEmpty()) {
                    if (text.length() < 2) {
                        log.info("SUBSCRIBER_ID_MENU: " + Arrays.toString(text.toCharArray()) + ".");
                        showError(update, ctx, "Введите нормальный никнейм/номер телефона");
                    } else {
                        GroupUser groupUserFromSql;
                        String message = "";
                        String nickname = "";
                        String phone = "";
                        long telegramId = -1L;

                        boolean byNick = ctx.getSubscriptionDetails().get(2).equals("BY-NICK");
                        boolean byPhone = ctx.getSubscriptionDetails().get(2).equals("BY-PHONE");
                        if (byNick) {
                            nickname = text.trim();

                            if (text.startsWith("@")) {
                                nickname = text.substring(1);
                            }
                        } else if (byPhone) {
                            phone = phoneValidatorAndNormalizer.validateAndNormalize(text);
                            if (phone == null) {
                                showError(update, ctx, "Введите нормальный номер телефона");
                                return;
                            }

                            telegramId = supergroupService.getTelegramIdByPhone(phone);
                            if (telegramId < 1) {
                                showError(update, ctx, "Пользователя с таким телефоном не существует.\nВведите правильный номер телефона пользователя");
                                return;
                            }
                            nickname = supergroupService.getNicknameByTelegramId(telegramId);
                        }

                        boolean inSql = adminBotService.inSql(nickname);
                        boolean inSupergroup = supergroupService.inSupergroup(nickname);
                        TelegramUserStatus telegramUserStatusFromSql;

                        if (!inSql && !inSupergroup) {
                            log.info("if (!inSql && !inSupergroup)");
                            groupUserFromSql = adminBotService.subscribeNewUser(nickname, telegramId, ctx.getSubscriptionDetails());

                            if (byNick) {
                                sendLink(update, ctx);
                            } else if (byPhone) {
                                supergroupService.addMemberToSupergroup(groupUserFromSql);
                                subscribeAndSendMessage(groupUserFromSql, ctx, update);
                            }

                        } else if (!inSql) {
                            log.info("else if (!inSql)");
                            groupUserFromSql = supergroupService.getGroupUserByNickname(nickname);

                            if (groupUserFromSql.isAdminOrCreator()) {
                                message = "Пользователь " + nickname + " не является обычным членом группы. Проверяйте.";
                                clearMenu(update, ctx.getLastMessageId(), message);
                                break;
                            }

                            telegramUserStatusFromSql = groupUserFromSql.getTelegramUserStatus();
                            if (telegramUserStatusFromSql == TelegramUserStatus.BANNED) {
                                telegramGateway.clearBannedStatus(groupUserFromSql);
                            } else if (telegramUserStatusFromSql == TelegramUserStatus.RESTRICTED) {
                                log.info("else if (telegramUserStatusFromSql == TelegramUserStatus.RESTRICTED)");
                                telegramGateway.restoreRestrictedUser(groupUserFromSql);
                            } else if (telegramUserStatusFromSql == TelegramUserStatus.LEFT) {
                                log.info("else if (telegramUserStatusFromSql == TelegramUserStatus.LEFT)");
                                supergroupService.addMemberToSupergroup(groupUserFromSql);
                            }

                            subscribeAndSendMessage(groupUserFromSql, ctx, update);

                        } else {

                            groupUserFromSql = adminBotService.getUser(nickname);


                            if (groupUserFromSql.isAdminOrCreator()) {
                                message = "Пользователь " + nickname + " не является обычным членом группы. Проверяйте.";
                                clearMenu(update, ctx.getLastMessageId(), message);
                                return;
                            }

                            telegramUserStatusFromSql = groupUserFromSql.getTelegramUserStatus();
                            GroupUser groupUserFromSupergroup = supergroupService.getGroupUserById(groupUserFromSql.getTelegramId());
                            TelegramUserStatus telegramUserStatusFromSupergroup = groupUserFromSupergroup.getTelegramUserStatus();
                            if (telegramUserStatusFromSql == TelegramUserStatus.BANNED || telegramUserStatusFromSupergroup == TelegramUserStatus.BANNED) {
                                supergroupService.unbanMember(groupUserFromSql);
                            }

                            // при бане админа он понижается в SQL до MEMBER
                            if (!groupUserFromSql.isMember())
                                groupUserFromSql.setTelegramUserStatus(TelegramUserStatus.MEMBER);

                            groupUserFromSql = subscribeAndSendMessage(groupUserFromSql, ctx, update);

                            if (!inSupergroup) {
                                supergroupService.addMemberToSupergroup(groupUserFromSql);
                            }
                        }
                    }
                }


            }

            case THEME_MENU -> {
                if (ThemeState.contains(data)) {
                    ctx.getUploadDetails().setTheme(data);
                    ctx.setMenuState(MenuState.UPLOAD_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Загрузим файл в топик: " + ctx.getUploadDetails().getTheme() + "\nВыберите тип контента.");
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    ctx.getUploadDetails().clearUploadDetails();
                    ctx.setMenuState(MenuState.MAIN_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case UPLOAD_MENU -> {
                if (List.of("TEXT", "AUDIO", "VIDEO").contains(data)) {
                    ctx.getUploadDetails().setFileType(data);
                    ctx.setMenuState(MenuState.FILE_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getFile(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "В топик " + ctx.getUploadDetails().getTheme() + " грузим контент: " + ctx.getUploadDetails().getFileType() + "\nЗагрузите файл.");
                    safeExecute(editMessage);
                } else if ("BACK-TO-THEME".equals(data)) {
                    ctx.getUploadDetails().clearUploadDetails();
                    ctx.setMenuState(MenuState.MAIN_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }

            case FILE_MENU -> {
                if (update.hasMessage() && update.getMessage().hasDocument()) {
                    String fileId = update.getMessage().getDocument().getFileId();
                    String fileName = update.getMessage().getDocument().getFileName();
                    ctx.getUploadDetails().setFileName(fileName);

                    File uploadFile;
                    try {
                        uploadFile = telegramClient.execute(new GetFile(fileId));
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }

                    try (InputStream fileStream = telegramClient.downloadFileAsStream(uploadFile)) {
                        InputFile inputFile = adminBotService.uploadFile(ctx.getUploadDetails(), fileStream);
                        ctx.getUploadDetails().setInputFile(inputFile);
                    } catch (TelegramApiException | IOException e) {
                        throw new RuntimeException(e);
                    }

                    ctx.setMenuState(MenuState.DESCRIPTION_MENU);
                    clearMenu(update, ctx.getLastMessageId(), "Файл " + fileName + " загружен.");
                    InlineKeyboardMarkup markup = menuBuilder.getDescription(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Введите описание к файлу.");
                    ctx.setLastMessageId(safeExecute(sendMessage).getMessageId());
                } else if ("BACK-TO-UPLOAD".equals(data)) {
                    ctx.getUploadDetails().clearFileType();
                    ctx.setMenuState(MenuState.UPLOAD_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Выберите тип контента:");
                    safeExecute(editMessage);
                } else {
                    clearMenu(update, ctx.getLastMessageId(), "Вы что-то не то делаете. Давайте заново.");
                    ctx.getUploadDetails().clearFileType();
                    ctx.setMenuState(MenuState.UPLOAD_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Выберите тип контента:");
                    ctx.setLastMessageId(safeExecute(sendMessage).getMessageId());
                }
            }

            case DESCRIPTION_MENU -> {
                if (!text.isEmpty()) {
                    MinioFileDetail minioFileDetail = adminBotService.uploadFileDescription(text, ctx.getUploadDetails());

                    try {
                        SendDocument sendDocument = SendDocument.builder()
                                .document(ctx.getUploadDetails().getInputFile())
                                .chatId(supergroupChatId)
                                .build();
                        sendDocument.setCaption(text);

                        String topicName = ctx.getUploadDetails().getTheme().toLowerCase();
                        log.info(topicName);
                        Integer topicId = topicProperties.getMap().get(topicName).getId();
                        sendDocument.setMessageThreadId(topicId);

                        telegramClient.execute(sendDocument);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }

                    uploadContentConfirmation(minioFileDetail.getDescription(), ctx.getUploadDetails(), update, ctx.getLastMessageId());
                } else if ("BACK-TO-UPLOAD".equals(data)) {
                    ctx.getUploadDetails().clearUploadDetails();
                    ctx.setMenuState(MenuState.UPLOAD_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getUpload(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Выберите тип контента:");
                    safeExecute(editMessage);
                }
            }

            case ADMIN_MENU -> {
                if ("ADD".equals(data)) {
                    ctx.setMenuState(MenuState.APPOINTMENT_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, adminShow(data));
                    safeExecute(editMessage);
                } else if ("REMOVE".equals(data)) {
                    ctx.setMenuState(MenuState.DISMISSAL_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getDismissal(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, adminShow(data));
                    safeExecute(editMessage);
                } else if ("BACK-TO-MAIN".equals(data)) {
                    ctx.setMenuState(MenuState.MAIN_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Выберите действие:");
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
                            groupAdmin.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(5));
                            adminBotService.saveGroupUser(groupAdmin);
                            String message = "Администратор: " + groupAdmin.getNickname() + ", Telegram Id: " + groupAdmin.getTelegramId() + ", добавлен.";
                            clearMenu(update, ctx.getLastMessageId(), message);
                        } else {
                            deleteMenu(update, ctx.getLastMessageId());
                            ctx.setMenuState(MenuState.APPOINTMENT_MENU);
                            InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                            SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Пользователя с таким телефоном не существует.\nВведите номер телефона аккаунта нового администратора в формате +79261234567");
                            ctx.setLastMessageId(safeExecute(sendMessage).getMessageId());
                        }
                    } else {
                        deleteMenu(update, ctx.getLastMessageId());
                        ctx.setMenuState(MenuState.APPOINTMENT_MENU);
                        InlineKeyboardMarkup markup = menuBuilder.getAppointment(update);
                        SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Неправильный номер.\nВведите номер телефона аккаунта нового администратора в формате +79261234567");
                        ctx.setLastMessageId(safeExecute(sendMessage).getMessageId());
                    }
                } else if ("BACK-TO-ADMIN-ADD-REMOVE".equals(data)) {
                    ctx.setMenuState(MenuState.ADMIN_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Добавить или удалить администратора");
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
                        clearMenu(update, ctx.getLastMessageId(), "Администратор " + nickname + " удалён");
                    } else {
                        ctx.setMenuState(MenuState.DISMISSAL_MENU);
                        InlineKeyboardMarkup markup = menuBuilder.getDismissal(update);
                        SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Нет такого nickname.\nВведите никнейм пользователя для удаления:");
                        ctx.setLastMessageId(safeExecute(sendMessage).getMessageId());
                    }
                } else if ("BACK-TO-ADMIN-ADD-REMOVE".equals(data)) {
                    ctx.setMenuState(MenuState.ADMIN_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getAdmin(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Добавить или удалить администратора");
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
                        clearMenu(update, ctx.getLastMessageId(), "Пользователь " + nickname + " заблокирован");
                    } else {
                        ctx.setMenuState(MenuState.BAN_MENU);
                        InlineKeyboardMarkup markup = menuBuilder.getBanUser(update);
                        SendMessage sendMessage = messageBuilder.createMessage(update, markup, "Нет такого пользователя.\nВведите никнейм пользователя для удаления:");
                        ctx.setLastMessageId(safeExecute(sendMessage).getMessageId());
                    }
                } else if ("BACK-TO-MAIN".equals(data)) {

                    ctx.setMenuState(MenuState.MAIN_MENU);
                    InlineKeyboardMarkup markup = menuBuilder.getMain(update);
                    EditMessageText editMessage = messageBuilder.editMessage(update, ctx.getLastMessageId(), markup, "Выберите действие:");
                    safeExecute(editMessage);
                }
            }
        }
    }

    private void showError(Update update, ConversationContext ctx, String errorMessage) {
        deleteMenu(update, ctx.getLastMessageId());
        ctx.setMenuState(MenuState.SUBSCRIBER_ID_MENU);
        InlineKeyboardMarkup markup = menuBuilder.getSubscriberId(update);
        SendMessage sendMessage = messageBuilder.createMessage(update, markup, errorMessage);
        ctx.setLastMessageId(safeExecute(sendMessage).getMessageId());
    }

    private void sendLink(Update update, ConversationContext ctx) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(update.getMessage().getChatId())
                .text("Следующим сообщением будет одноразовая ссылка, перешлите её подписчику.")
                .build();
        telegramGateway.safeExecute(sendMessage);

        CreateChatInviteLink createChatInviteLink = CreateChatInviteLink.builder()
                .chatId(supergroupChatId)
                .memberLimit(1)
                .expireDate((int) Instant.now().plus(3, ChronoUnit.DAYS).getEpochSecond())
                .build();

        ChatInviteLink chatInviteLink = telegramGateway.safeExecute(createChatInviteLink);
        String inviteLink = chatInviteLink.getInviteLink();
        SendMessage inviteMessage = SendMessage.builder()
                .chatId(update.getMessage().getChatId())
                .text("Присоединяйтесь к группе Тестовый Просвещенный 3.0 по ссылке: " + inviteLink)
                .build();
        telegramGateway.safeExecute(inviteMessage);

        clearMenu(update, ctx.getLastMessageId());
    }

    private GroupUser subscribeAndSendMessage(GroupUser groupUser, ConversationContext ctx, Update update) {
        groupUser = adminBotService.subscribeExistedGroupUser(groupUser, ctx.getSubscriptionDetails());
        String message = "Пользователь: " + groupUser.getNickname() + " подписка: " + ctx.getSubscriptionDetails().get(0) + ", на срок: " + ctx.getSubscriptionDetails().get(1);
        clearMenu(update, ctx.getLastMessageId(), message);
        return groupUser;
    }

    private void promoteToAdmin(long userId) {
        if (!isUserAdmin(userId)) {
            PromoteChatMember promoteChatMember = PromoteChatMember.builder()
                    .chatId(supergroupChatId)
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
                .chatId(supergroupChatId)
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

    private void clearMenu(Update update, Integer messageId) {

        String chatId = update.hasMessage()
                ? update.getMessage().getChatId().toString()
                : update.getCallbackQuery().getMessage().getChatId().toString();

        EditMessageReplyMarkup clearMarkup = EditMessageReplyMarkup.builder()
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(new InlineKeyboardMarkup(Collections.emptyList()))
                .build();

        telegramGateway.safeExecute(clearMarkup);
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


}
