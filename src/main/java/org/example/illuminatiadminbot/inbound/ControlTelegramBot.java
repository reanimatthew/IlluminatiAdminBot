package org.example.illuminatiadminbot.inbound;

// TODO бот для обновления БД пользователей и админов. Сделать с помощью MTProto.

import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.inbound.menu.Subscription;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.service.AdminBotService;
import org.example.illuminatiadminbot.service.SupergroupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Moscow")
    private void banUsersWithExpiringSubscriptions() {
        List<GroupUser> usersWithExpiringSubscriptions = adminBotService.getUsersWithExpiredSubscriptions();
        StringBuilder stringBuilder = new StringBuilder("Подписка истекла, следующие пользователи забанены:\n\n");
        for (GroupUser groupUser : usersWithExpiringSubscriptions) {
            supergroupService.banUser(groupUser.getNickname());
            adminBotService.banUser(groupUser.getNickname());
            stringBuilder.append(groupUser.toStringSql());
        }
        stringBuilder.append("=========");
        if (usersWithExpiringSubscriptions.isEmpty()) {
            stringBuilder = new StringBuilder("Нет забанненых из-за подписки пользователей");
        }
        showMessageAllAdmins(stringBuilder);
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Moscow")
    private void showSubscriptionReport() {
        //сканируем и выводим пользователей с истекающей подпиской
        List<GroupUser> usersWithExpiringSubscriptions = adminBotService.getUsersWithExpiringSubscriptions();
        StringBuilder stringBuilder = new StringBuilder("У следующих пользователей истекает подписка:\n\n");
        for (GroupUser expiringUsers : usersWithExpiringSubscriptions) {
            stringBuilder.append(expiringUsers.toStringSql());
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(ZoneId.of("Europe/Moscow")), expiringUsers.getSubscriptionExpiration());
            stringBuilder.append("осталось: ").append(daysRu(daysLeft)).append("\n\n");
        }
        stringBuilder.append("=========");
        if (usersWithExpiringSubscriptions.isEmpty()) {
            stringBuilder = new StringBuilder("Нет пользователей с истекающей подпиской");
        }
        showMessageAllAdmins(stringBuilder);
    }

    @Scheduled(initialDelay = 1000 * 60 * 15, fixedRate = 1000 * 60 * 60)
    private void correctSql() {
        List<GroupUser> userFromSuperGroup = supergroupService.getAllGroupUsers();
        Map<Long, GroupUser> mapFromSupergroup = userFromSuperGroup.stream()
                .collect(Collectors.toMap(
                        GroupUser::getTelegramId,
                        Function.identity()
                ));

        AtomicLong tempId = new AtomicLong(-1);
        List<GroupUser> usersFromSql = adminBotService.getAllUsers()
                .stream()
                .peek(user -> {
                    if (user.getTelegramId() == null)
                        user.setTelegramId(tempId.getAndDecrement());
                })
                .toList();

        Map<Long, GroupUser> sqlById = usersFromSql.stream()
                .filter(user -> user.getTelegramId() != null && user.getTelegramId() > 0)
                .collect(Collectors.toMap(
                        GroupUser::getTelegramId,
                        Function.identity()
                ));

        Map<String, >

        List<GroupUser> changed = new ArrayList<>();

        for (Map.Entry<Long, GroupUser> entryFromSupergroup : mapFromSupergroup.entrySet()) {
            if (sqlById.containsKey(entryFromSupergroup.getKey())) {
                GroupUser userFromSupergroup = mapFromSupergroup.get(entryFromSupergroup.getKey());
                GroupUser userFromSql = sqlById.get(userFromSupergroup.getTelegramId());

                if (!userFromSql.getNickname().equals(userFromSupergroup.getNickname())) {
                    userFromSql.setNickname(userFromSupergroup.getNickname());
                }

                //креатор
                if (entryFromSupergroup.getValue().getTelegramUserStatus() == TelegramUserStatus.CREATOR
                        && userFromSql.getTelegramUserStatus() != TelegramUserStatus.CREATOR) {
                    userFromSql.setTelegramUserStatus(TelegramUserStatus.CREATOR);
                    userFromSql.setSubscriptionType(Subscription.CREATOR);
                    userFromSql.setSubscriptionDuration(12 * 100);
                    userFromSql.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(100));
                    changed.add(userFromSql);
                }

                //админы
                if (entryFromSupergroup.getValue().getTelegramUserStatus() == TelegramUserStatus.ADMINISTRATOR
                        && userFromSql.getTelegramUserStatus() != TelegramUserStatus.ADMINISTRATOR) {
                    userFromSql.setTelegramUserStatus(TelegramUserStatus.ADMINISTRATOR);
                    userFromSql.setSubscriptionType(Subscription.ADMIN);
                    userFromSql.setSubscriptionDuration(12 * 5);
                    userFromSql.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(5));
                    changed.add(userFromSql);
                }
            }
        }

        adminBotService.saveAll(changed);
    }

    private void showMessageAllAdmins(StringBuilder message) {
        for (Long chatId : adminBotService.getAllAdminsChatId()) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(message.toString())
                    .build();
            telegramGateway.safeExecute(sendMessage);
        }
    }

    private static String daysRu(long d) {
        long n = Math.abs(d) % 100;
        long n1 = n % 10;
        if (n > 10 && n < 20) return d + " дней";
        if (n1 == 1) return d + " день";
        if (n1 >= 2 && n1 <= 4) return d + " дня";
        return d + " дней";
    }
}
