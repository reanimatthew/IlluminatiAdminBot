package org.example.illuminatiadminbot.inbound;

// TODO бот для обновления БД пользователей и админов. Сделать с помощью MTProto.

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.inbound.menu.Subscription;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.service.AdminBotService;
import org.example.illuminatiadminbot.service.SupergroupService;
import org.example.illuminatiadminbot.service.UserSyncService;
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
@RequiredArgsConstructor
public class ControlTelegramBot {

    private final AdminBotService adminBotService;
    private final SupergroupService supergroupService;
    private final UserSyncService  userSyncService;
    private final TelegramGateway telegramGateway;

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
    private void scheduledCorrectSql() {
        userSyncService.correctSql();
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
