package org.example.illuminatiadminbot.service;

import lombok.RequiredArgsConstructor;
import org.example.illuminatiadminbot.inbound.menu.Subscription;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final AdminBotService adminBotService;
    private final SupergroupService supergroupService;

//    public void correctSql() {
//        List<GroupUser> userFromSuperGroup = supergroupService.getAllGroupUsers();
//        Map<Long, GroupUser> mapFromSupergroup = userFromSuperGroup.stream()
//                .collect(Collectors.toMap(
//                        GroupUser::getTelegramId,
//                        Function.identity()
//                ));
//
//        AtomicLong tempId = new AtomicLong(-1);
//        List<GroupUser> usersFromSql = adminBotService.getAllUsers()
//                .stream()
//                .peek(user -> {
//                    if (user.getTelegramId() == null)
//                        user.setTelegramId(tempId.getAndDecrement());
//                })
//                .toList();
//
//        Map<Long, GroupUser> sqlById = usersFromSql.stream()
//                .filter(user -> user.getTelegramId() != null && user.getTelegramId() > 0)
//                .collect(Collectors.toMap(
//                        GroupUser::getTelegramId,
//                        Function.identity()
//                ));
//
//        Map<String, GroupUser> sqlByNickname = usersFromSql.stream()
//                .filter(user -> user.getTelegramId() != null
//                        && user.getTelegramId() < 0
//                        && user.getNickname() != null)
//                .collect(Collectors.toMap(
//                        GroupUser::getNickname,
//                        Function.identity()
//                ));
//
//        List<GroupUser> changed = new ArrayList<>();
//
//        for (Map.Entry<Long, GroupUser> entryFromSupergroup : mapFromSupergroup.entrySet()) {
//            boolean dirty = false;
//            GroupUser userFromSql = null;
//            if (sqlById.containsKey(entryFromSupergroup.getKey())) {
//                GroupUser userFromSupergroup = mapFromSupergroup.get(entryFromSupergroup.getKey());
//                userFromSql = sqlById.get(userFromSupergroup.getTelegramId());
//
//                if (!userFromSql.getNickname().equals(userFromSupergroup.getNickname())) {
//                    userFromSql.setNickname(userFromSupergroup.getNickname());
//                    dirty =  true;
//                }
//
//                // креатор
//                if (entryFromSupergroup.getValue().getTelegramUserStatus() == TelegramUserStatus.CREATOR
//                        && userFromSql.getTelegramUserStatus() != TelegramUserStatus.CREATOR) {
//                    userFromSql.setTelegramUserStatus(TelegramUserStatus.CREATOR);
//                    userFromSql.setSubscriptionType(Subscription.CREATOR);
//                    userFromSql.setSubscriptionDuration(12 * 100);
//                    userFromSql.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(100));
//                    dirty =  true;
//                }
//
//                // админы
//                if (entryFromSupergroup.getValue().getTelegramUserStatus() == TelegramUserStatus.ADMINISTRATOR
//                        && userFromSql.getTelegramUserStatus() != TelegramUserStatus.ADMINISTRATOR) {
//                    userFromSql.setTelegramUserStatus(TelegramUserStatus.ADMINISTRATOR);
//                    userFromSql.setSubscriptionType(Subscription.ADMIN);
//                    userFromSql.setSubscriptionDuration(12 * 5);
//                    userFromSql.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(5));
//                    dirty =  true;
//                }
//            }
//
//            // если у пользователя из SQL нет Telegram Id
//            if (!sqlByNickname.isEmpty()) {
//                for (Map.Entry<String, GroupUser> entryFromSql : sqlByNickname.entrySet()) {
//                    if (entryFromSql.getKey().equals(entryFromSupergroup.getValue().getNickname())) {
//                        userFromSql = entryFromSql.getValue();
//                        userFromSql.setTelegramId(entryFromSupergroup.getKey());
//                        dirty =  true;
//                    }
//                }
//            }
//
//            if (dirty)
//                changed.add(userFromSql);
//        }
//
//        changed = changed.stream().distinct().toList();
//        adminBotService.saveAll(changed);
//    }
//

    @Transactional
    public void correctSql() {
        // 1) Снимок из супергруппы: (id -> user), отфильтруем null
        List<GroupUser> fromSupergroupRaw = supergroupService.getAllGroupUsers();
        List<GroupUser> fromSupergroup = fromSupergroupRaw.stream()
                .filter(Objects::nonNull)
                .toList();
        Map<Long, GroupUser> sgById = fromSupergroup.stream()
                .filter(u -> u.getTelegramId() != null && u.getTelegramId() > 0)
                .collect(Collectors.toMap(GroupUser::getTelegramId, Function.identity(), (a,b)->a));

        // 2) Снимок из SQL
        AtomicLong tempId = new AtomicLong(-1);
        List<GroupUser> sqlUsers = adminBotService.getAllUsers().stream()
                .peek(u -> { if (u.getTelegramId() == null) u.setTelegramId(tempId.getAndDecrement()); })
                .toList();

        Map<Long, GroupUser> sqlById = sqlUsers.stream()
                .filter(u -> u.getTelegramId() != null && u.getTelegramId() > 0)
                .collect(Collectors.toMap(GroupUser::getTelegramId, Function.identity(), (a,b)->a));

        Map<String, GroupUser> sqlByNickMissingId = sqlUsers.stream()
                .filter(u -> u.getTelegramId() != null && u.getTelegramId() < 0 && u.getNickname() != null && !u.getNickname().isBlank())
                .collect(Collectors.toMap(u -> u.getNickname().toLowerCase(), Function.identity(), (a,b)->a));

        List<GroupUser> toSave = new ArrayList<>();

        // 3) Обновляем существующих по telegramId и подтягиваем роли/сроки
        for (Map.Entry<Long, GroupUser> e : sgById.entrySet()) {
            Long tgId = e.getKey();
            GroupUser sgUser = e.getValue();
            GroupUser sql = sqlById.get(tgId);
            if (sql == null) continue;

            boolean dirty = false;

            // ник (мог измениться)
            String sgNick = sgUser.getNickname() == null ? "" : sgUser.getNickname();
            String sqlNick = sql.getNickname() == null ? "" : sql.getNickname();
            if (!sqlNick.equals(sgNick)) {
                sql.setNickname(sgNick);
                dirty = true;
            }

            // выравнивание статуса и "служебной" подписки для CREATOR/ADMIN
            if (sgUser.getTelegramUserStatus() != sql.getTelegramUserStatus()) {
                sql.setTelegramUserStatus(sgUser.getTelegramUserStatus());
                switch (sgUser.getTelegramUserStatus()) {
                    case CREATOR -> {
                        sql.setSubscriptionType(Subscription.CREATOR);
                        sql.setSubscriptionDuration(12 * 20);
                        sql.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(20));
                    }
                    case ADMINISTRATOR -> {
                        sql.setSubscriptionType(Subscription.ADMIN);
                        sql.setSubscriptionDuration(12 * 5);
                        sql.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(5));
                    }
                    default -> { /* членство MEMBER/иные не трогаем тут */ }
                }
                dirty = true;
            }

            if (dirty) toSave.add(sql);
        }

        // 4) Привязываем записи без telegramId по нику (без регистра)
        Map<String, GroupUser> sgByNick = fromSupergroup.stream()
                .filter(u -> u.getNickname() != null && !u.getNickname().isBlank())
                .collect(Collectors.toMap(u -> u.getNickname().toLowerCase(), Function.identity(), (a,b)->a));

        for (Map.Entry<String, GroupUser> e : sqlByNickMissingId.entrySet()) {
            String nickLc = e.getKey();
            GroupUser sql = e.getValue();
            GroupUser sg = sgByNick.get(nickLc);
            if (sg == null || sg.getTelegramId() == null || sg.getTelegramId() <= 0) continue;

            // привязали id и базовые статусы
            sql.setTelegramId(sg.getTelegramId());
            if (sql.getTelegramUserStatus() == null || sql.getTelegramUserStatus() != sg.getTelegramUserStatus()) {
                sql.setTelegramUserStatus(sg.getTelegramUserStatus());
            }
            toSave.add(sql);
        }

        // 5) ДОБАВЛЯЕМ отсутствующих в SQL, но присутствующих в супергруппе
        Set<Long> presentIds = sqlById.keySet();
        List<GroupUser> toInsert = fromSupergroup.stream()
                .filter(u -> u.getTelegramId() != null && u.getTelegramId() > 0)
                .filter(u -> !presentIds.contains(u.getTelegramId()))
                .map(u -> {
                    GroupUser g = GroupUser.builder()
                            .telegramId(u.getTelegramId())
                            .nickname(u.getNickname())
                            .telegramUserStatus(u.getTelegramUserStatus())
                            .build();
                    // базовая “служебная” подписка по статусу
                    switch (u.getTelegramUserStatus()) {
                        case CREATOR -> {
                            g.setSubscriptionType(Subscription.CREATOR);
                            g.setSubscriptionDuration(12 * 20);
                            g.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(20));
                        }
                        case ADMINISTRATOR -> {
                            g.setSubscriptionType(Subscription.ADMIN);
                            g.setSubscriptionDuration(12 * 5);
                            g.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(5));
                        }
                        case MEMBER -> {
                            g.setSubscriptionType(Subscription.TEMP);
                            g.setSubscriptionDuration(1);
                            g.setSubscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusMonths(1));
                        }
                        default -> { /* остальные без подписки */ }
                    }
                    return g;
                })
                .toList();

        // 6) Сохраняем
        if (!toInsert.isEmpty()) {
            adminBotService.saveAll(toInsert);
        }
        if (!toSave.isEmpty()) {
            // уберём дубликаты по id
            Map<Long, GroupUser> uniq = toSave.stream()
                    .filter(u -> u.getId() != null) // у новых id может ещё не быть — их сохранили выше
                    .collect(Collectors.toMap(GroupUser::getId, Function.identity(), (a,b)->b));
            adminBotService.saveAll(new ArrayList<>(uniq.values()));
        }
    }



}
