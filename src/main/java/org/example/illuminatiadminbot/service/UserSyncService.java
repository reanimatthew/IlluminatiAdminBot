package org.example.illuminatiadminbot.service;

import lombok.RequiredArgsConstructor;
import org.example.illuminatiadminbot.inbound.menu.Subscription;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final AdminBotService adminBotService;
    private final SupergroupService supergroupService;

    public void correctSql() {
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

        Map<String, GroupUser> sqlByNickname = usersFromSql.stream()
                .filter(user -> user.getTelegramId() != null
                        && user.getTelegramId() < 0
                        && user.getNickname() != null)
                .collect(Collectors.toMap(
                        GroupUser::getNickname,
                        Function.identity()
                ));

        List<GroupUser> changed = new ArrayList<>();

        for (Map.Entry<Long, GroupUser> entryFromSupergroup : mapFromSupergroup.entrySet()) {
            boolean dirty = false;
            GroupUser userFromSql = null;
            if (sqlById.containsKey(entryFromSupergroup.getKey())) {
                GroupUser userFromSupergroup = mapFromSupergroup.get(entryFromSupergroup.getKey());
                userFromSql = sqlById.get(userFromSupergroup.getTelegramId());

                if (!userFromSql.getNickname().equals(userFromSupergroup.getNickname())) {
                    userFromSql.setNickname(userFromSupergroup.getNickname());
                    dirty =  true;
                }

                // креатор
                if (entryFromSupergroup.getValue().getTelegramUserStatus() == TelegramUserStatus.CREATOR
                        && userFromSql.getTelegramUserStatus() != TelegramUserStatus.CREATOR) {
                    userFromSql.setTelegramUserStatus(TelegramUserStatus.CREATOR);
                    userFromSql.setSubscriptionType(Subscription.CREATOR);
                    userFromSql.setSubscriptionDuration(12 * 100);
                    userFromSql.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(100));
                    dirty =  true;
                }

                // админы
                if (entryFromSupergroup.getValue().getTelegramUserStatus() == TelegramUserStatus.ADMINISTRATOR
                        && userFromSql.getTelegramUserStatus() != TelegramUserStatus.ADMINISTRATOR) {
                    userFromSql.setTelegramUserStatus(TelegramUserStatus.ADMINISTRATOR);
                    userFromSql.setSubscriptionType(Subscription.ADMIN);
                    userFromSql.setSubscriptionDuration(12 * 5);
                    userFromSql.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(5));
                    dirty =  true;
                }
            }

            // если у пользователя из SQL нет Telegram Id
            if (!sqlByNickname.isEmpty()) {
                for (Map.Entry<String, GroupUser> entryFromSql : sqlByNickname.entrySet()) {
                    if (entryFromSql.getKey().equals(entryFromSupergroup.getValue().getNickname())) {
                        userFromSql = entryFromSql.getValue();
                        userFromSql.setTelegramId(entryFromSupergroup.getKey());
                        dirty =  true;
                    }
                }
            }

            if (dirty)
                changed.add(userFromSql);
        }

        changed = changed.stream().distinct().toList();
        adminBotService.saveAll(changed);
    }

}
