package org.example.illuminatiadminbot.inbound;

// TODO бот для обновления БД пользователей и админов. Сделать с помощью MTProto.

import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.inbound.model.UserStatus;
import org.example.illuminatiadminbot.mapper.GroupUserMapper;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.service.AdminBotService;
import org.example.illuminatiadminbot.service.SupergroupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ControlTelegramBot {

    private final AdminBotService adminBotService;
    private final SupergroupService supergroupService;
    private final TelegramGateway telegramGateway;

    public ControlTelegramBot(AdminBotService adminBotService, SupergroupService supergroupService, TelegramGateway telegramGateway) {
        this.adminBotService = adminBotService;
        this.supergroupService = supergroupService;
        this.telegramGateway = telegramGateway;
    }

    @Scheduled(fixedRate = 1000 * 60 * 60)
    public void showDailyReport() {
        String messageForAdmins = scanAndCorrect();
        List<Long> allAdminsChatId = adminBotService.getAllAdminsChatId();
        if (allAdminsChatId.isEmpty()) {
            return;
        }

        log.info("showDailyReport started");
        for (Long chatId : allAdminsChatId) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(messageForAdmins)
                    .build();
            telegramGateway.safeExecute(sendMessage);
        }
    }

    public String scanAndCorrect() {

        StringBuilder result = new StringBuilder();

        // Мапа реальных пользователей из супергруппы, ключ - TelegramId
        SortedMap<Long, String> existingUsers = new TreeMap<>(supergroupService.getAllMembers());

        // Мапа пользователей из SQL БД
        SortedMap<Long, GroupUser> groupUsersMap = adminBotService.getAllUsers()
                .stream()
                .collect(
                        Collectors.toMap(
                                GroupUser::getId,
                                Function.identity(),
                                (firstUser, secondUser) -> firstUser,
                                TreeMap::new));

        SortedMap<Long, GroupUser> notInSupergroup = groupUsersMap.entrySet().stream()
                .filter(e -> !existingUsers.containsKey(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (firstUser, secondUser) -> firstUser,
                        TreeMap::new
                ));

        SortedMap<Long, String> notInSql = existingUsers.entrySet().stream()
                .filter(e -> !groupUsersMap.containsKey(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (firstUser, secondUser) -> firstUser,
                        TreeMap::new
                ));

        // проверяем соответствие nickname из супергруппы и из SQL, корректируем по необходимости
        if (notInSql.isEmpty() && notInSupergroup.isEmpty()) {
            List<GroupUser> realGroupUsers = new ArrayList<>();
            GroupUser currentGroupUser;
            for (Map.Entry<Long, String> entry : existingUsers.entrySet()) {
                currentGroupUser = groupUsersMap.get(entry.getKey());
                if (!currentGroupUser.getNickname().equals(entry.getValue())) {
                    result.append("Юзер ")
                            .append(currentGroupUser.getTelegramId())
                            .append(" сменил nickname c ")
                            .append(currentGroupUser.getNickname())
                            .append(" на ")
                            .append(entry.getValue())
                            .append("\n");
                    currentGroupUser.setNickname(entry.getValue());
                }
                result.append("---------\n");
                realGroupUsers.add(currentGroupUser);
            }
            adminBotService.saveAll(realGroupUsers);
        } else if (notInSupergroup.isEmpty()) {
            result.append("Есть в супергруппе, но нет в БД. Проверить подписки:\n");
            for (Map.Entry<Long, String> entry : notInSql.entrySet()) {
                result.append("id: ")
                        .append(entry.getKey())
                        .append(", nickname: ")
                        .append(entry.getValue())
                        .append("\n");
            }
        } else {
            result.append("Есть в БД, нет в супергруппе. Почему не заходят?\n");
            for (Map.Entry<Long, GroupUser> entry : notInSupergroup.entrySet()) {
                result.append("id: ")
                        .append(entry.getKey())
                        .append(", nickname: ")
                        .append(entry.getValue().getNickname())
                        .append("\n");
            }
            result.append("=========\n");
        }

        // проверяем истекающие подписки
        LocalDate now = LocalDate.now();
        LocalDate threshold = LocalDate.now().plusDays(3);
        StringBuilder expiringSubscription = new StringBuilder("Подписка истекает:\n");
        boolean expiringSubscriptionIsExist = false;

        for (GroupUser groupUser : adminBotService.getAllUsers()) {
            if (groupUser.getStatus().equals(UserStatus.BANNED.toString()))
                continue;

            if (groupUser.getSubscriptionExpiration().isBefore(now)) {
                telegramGateway.banUser(groupUser);
                adminBotService.banUser(groupUser.getNickname());
                continue;
            }

            if (groupUser.getSubscriptionExpiration().isBefore(threshold)) {
                expiringSubscriptionIsExist = true;
                expiringSubscription.append("id: ")
                        .append(groupUser.getTelegramId())
                        .append(", nickname: ")
                        .append(groupUser.getNickname())
                        .append(". истекает")
                        .append(groupUser.getSubscriptionExpiration())
                        .append("\n");
            }

            if (expiringSubscriptionIsExist) {
                result.append(expiringSubscription);
            }
        }

        return result.toString();
    }
}
