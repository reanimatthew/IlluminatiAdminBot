package org.example.illuminatiadminbot.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.TelegramError;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.inbound.menu.Subscription;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.inbound.model.UserStatus;
import org.example.illuminatiadminbot.mapper.ChatMemberStatusMapper;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.outbound.repository.GroupUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupergroupService {

    private final SimpleTelegramClient simpleTelegramClient;
    private final GroupUserRepository groupUserRepository;
    private final ChatMemberStatusMapper chatMemberStatusMapper;

    @Value("${supergroup.id}")
    private Long supergroupId;

    @Value("${chat.id}")
    private Long chatId;

    public Map<Long, String> getAllMembers() {

        List<TdApi.ChatMember> allMembers = new ArrayList<>();
        int offset = 0;
        int limit = 200;

        while (true) {
            CompletableFuture<TdApi.ChatMembers> future = simpleTelegramClient.send(
                    new TdApi.GetSupergroupMembers(
                            supergroupId,
                            new TdApi.SupergroupMembersFilterRecent(),
                            offset,
                            limit
                    )
            );

            TdApi.ChatMembers chunk;
            try {
                chunk = future.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }

            Collections.addAll(allMembers, chunk.members);
            if (chunk.members.length < limit)
                break;
            offset += chunk.members.length;
        }

        Map<Long, String> existingUsers = new HashMap<>();
        for (TdApi.ChatMember chatMember : allMembers) {
            TdApi.MessageSenderUser messageSenderUser = (TdApi.MessageSenderUser) chatMember.memberId;
            Long telegramId = messageSenderUser.userId;

            TdApi.User user;
            try {
                user = simpleTelegramClient
                        .send(new TdApi.GetUser(messageSenderUser.userId))      // отправили запрос
                        .get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.warn("Не удалось получить info по пользователю {}", telegramId, e);
                continue;
            }

            String username = "";
            if (user.usernames != null && user.usernames.editableUsername != null) {
                username = user.usernames.editableUsername;
            }
            existingUsers.put(telegramId, username);
        }

        return existingUsers;
    }

    public GroupUser addUserToSupergroup(String phone) {

        long telegramId = getTelegramIdByPhone(phone);

        if (telegramId == 0L)
            return null;

        String nickname = getNicknameByTelegramId(telegramId);

        // проверяем, есть ли юзер в группе
        GroupUser existUser = getGroupUserById(telegramId);
        if (existUser != null)
            return existUser;

        TdApi.AddChatMember inviteRequest = new TdApi.AddChatMember(
                chatId,
                telegramId,
                0
        );

        try {
            simpleTelegramClient.send(inviteRequest).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        TdApi.RemoveContacts removeRequest = new TdApi.RemoveContacts(new long[]{telegramId});

        try {
            simpleTelegramClient.send(removeRequest).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        return GroupUser.createNewMember(telegramId, nickname);
    }

    private List<Long> getAllAdminIdsFromSupergroup() {

        List<Long> adminIds = new ArrayList<>();

        List<TdApi.ChatMember> allMembers = new ArrayList<>();
        int offset = 0;
        int limit = 200;

        while (true) {
            CompletableFuture<TdApi.ChatMembers> future = simpleTelegramClient.send(
                    new TdApi.GetSupergroupMembers(
                            supergroupId,
                            new TdApi.SupergroupMembersFilterRecent(),
                            offset,
                            limit
                    )
            );

            TdApi.ChatMembers chunk;
            try {
                chunk = future.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }

            Collections.addAll(allMembers, chunk.members);
            if (chunk.members.length < limit)
                break;
            offset += chunk.members.length;
        }

        for (TdApi.ChatMember chatMember : allMembers) {
            TdApi.MessageSenderUser messageSenderUser = (TdApi.MessageSenderUser) chatMember.memberId;
            adminIds.add(messageSenderUser.userId);
        }

        return adminIds;
    }

    public GroupUser getGroupUserById(long telegramId) {

        // проверяем наличие юзера в группе
        TdApi.ChatMember chatMember = null;
        try {
            chatMember = simpleTelegramClient.send(
                    new TdApi.GetChatMember(
                            chatId,
                            new TdApi.MessageSenderUser(telegramId)
                    )
            ).get();
        } catch (ExecutionException e) {
            // этот код ловит отсутствие юзера в группе
            Throwable cause = e.getCause();
            if (cause instanceof TelegramError telegramError) {
                TdApi.Error error = telegramError.getError();
                if (error.code == 400 && "Member not found".equals(error.message)) {
                    return null;
                }
            } else {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        String nickname = getNicknameByTelegramId(telegramId);

        TelegramUserStatus telegramUserStatus = chatMemberStatusMapper.simplifyStatus(chatMember.status);

        return switch (telegramUserStatus) {
            case CREATOR -> GroupUser.createNewCreator(telegramId, nickname);
            case ADMINISTRATOR ->  GroupUser.createNewAdministrator(telegramId, nickname);
            case MEMBER -> GroupUser.createNewMember(telegramId, nickname);
            default -> throw new RuntimeException("Unknown TelegramUserStatus: " + telegramUserStatus);
        };
    }

    public List<GroupUser> getAllGroupUsers() {
        List<GroupUser> groupUsers = new ArrayList<>();
        List<TdApi.ChatMember> allMembers = new ArrayList<>();
        int offset = 0;
        int limit = 200;
        while (true) {
            CompletableFuture<TdApi.ChatMembers> future = simpleTelegramClient.send(
                    new TdApi.GetSupergroupMembers(
                            supergroupId,
                            new TdApi.SupergroupMembersFilterRecent(),
                            offset,
                            limit
                    )
            );

            TdApi.ChatMembers chunk;
            try {
                chunk = future.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }

            Collections.addAll(allMembers, chunk.members);
            if (chunk.members.length < limit)
                break;
            offset += chunk.members.length;
        }

        GroupUser groupUser;
        for (TdApi.ChatMember chatMember : allMembers) {
            TdApi.MessageSenderUser messageSenderUser = (TdApi.MessageSenderUser) chatMember.memberId;
            groupUser = getGroupUserById(messageSenderUser.userId);
            groupUsers.add(groupUser);
        }

        return groupUsers;
    }

    public GroupUser activateGroupUserById(long userId, TelegramUserStatus telegramUserStatus) {

        // проверяем, есть ли такой пользователь в БД
        Optional<GroupUser> groupUserOptional = groupUserRepository.findByTelegramId(userId);
        if (groupUserOptional.isPresent()) {
            GroupUser groupUser = groupUserOptional.get();
            groupUser.setStatus(UserStatus.ACTIVE);
            groupUser.setTelegramUserStatus(telegramUserStatus);
            return groupUser;
        }

        TdApi.User user;

        try {
            user = simpleTelegramClient.send(new TdApi.GetUser(userId)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        String nickname = "";
        if (user.usernames != null && user.usernames.editableUsername != null) {
            nickname = user.usernames.editableUsername;
        }

        return GroupUser.builder()
                .telegramId(userId)
                .nickname(nickname)
                .telegramUserStatus(telegramUserStatus)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public boolean banUser(String nickname) {

        Long userId = getAllMembers().entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), nickname))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (userId == null)
            return false;

        TdApi.BanChatMember banRequest = new TdApi.BanChatMember(
                chatId,
                new TdApi.MessageSenderUser(userId),
                0,
                true
        );

        try {
            simpleTelegramClient.send(banRequest);
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    public String correctSql() {
        List<GroupUser> groupUsersNotInSql = findGroupUsersNotInSql();

        if (groupUsersNotInSql.isEmpty()) {
            return "Нет коррекций.";
        }

        for (GroupUser groupUser : groupUsersNotInSql) {
            if (groupUser.getTelegramUserStatus() == TelegramUserStatus.ADMINISTRATOR) {
                groupUser.setSubscriptionType(Subscription.ADMIN);
                groupUser.setSubscriptionDuration(5 * 12);
                groupUser.setSubscriptionExpiration(LocalDate.now().plusYears(5));
            } else if (groupUser.getTelegramUserStatus() == TelegramUserStatus.CREATOR) {
                groupUser.setSubscriptionType(Subscription.CREATOR);
                groupUser.setSubscriptionDuration(20 * 12);
                groupUser.setSubscriptionExpiration(LocalDate.now().plusYears(20));
            } else if (groupUser.getTelegramUserStatus() == TelegramUserStatus.MEMBER) {
                groupUser.setSubscriptionType(Subscription.TEMP);
                groupUser.setSubscriptionDuration(1);
                groupUser.setSubscriptionExpiration(LocalDate.now().plusMonths(1));
            }
        }

        groupUserRepository.saveAll(groupUsersNotInSql);

        StringBuilder result = new StringBuilder("=========\nСледующие пользователи были добавлены в БД:\n\n");
        for (GroupUser groupUser : groupUsersNotInSql) {
            log.info(groupUser.toStringSql());
            result.append(groupUser.toStringSql());
        }
        result.append("\nВНИМАНИЕ! Если есть подписка TEMP, её необходимо сменить на BASE/PREMIUM/GOLD!");

        return result.toString();
    }

    public String scanSupergroup() {
        List<GroupUser> groupUsersNotInSupergroup = findGroupUsersNotInSupergroup();

        StringBuilder result = new StringBuilder("=========\nПользователи, которые есть в БД и нет в супергруппе:\n\n");
        for (GroupUser groupUser : groupUsersNotInSupergroup) {
            if (groupUser.isActive()) {
                result.append(groupUser.toStringSql());
            }
        }
        result.append("\n");
        return result.toString();
    }

    public List<GroupUser> findGroupUsersNotInSql() {

        List<GroupUser> fromSupergroup = getAllGroupUsers();
        Set<Long> telegramIdsFromSql = groupUserRepository.findAll()
                .stream()
                .map(GroupUser::getTelegramId)
                .collect(Collectors.toSet());

        return fromSupergroup.stream()
                .filter(groupUser -> !telegramIdsFromSql.contains(groupUser.getTelegramId()))
                .toList();
    }

    public List<GroupUser> findGroupUsersNotInSupergroup() {

        List<GroupUser> fromSql = groupUserRepository.findAll();
        Set<Long> telegramIdsFromSupergroup = getAllGroupUsers()
                .stream()
                .map(GroupUser::getTelegramId)
                .collect(Collectors.toSet());

        return fromSql
                .stream()
                .filter(groupUser -> !telegramIdsFromSupergroup.contains(groupUser.getTelegramId()))
                .toList();

    }

    public String getNicknameByTelegramId(long telegramId) {

        TdApi.User user;

        try {
            user = simpleTelegramClient.send(new TdApi.GetUser(telegramId)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        String nickname = "";
        if (user.usernames != null && user.usernames.editableUsername != null) {
            nickname = user.usernames.editableUsername;
        }

        return nickname;
    }

    public long getTelegramIdByPhone(String phone) {
        // Создаём объект контакта для импорта в Telegram по переданному номеру
        TdApi.Contact contact = new TdApi.Contact(
                phone,
                "",
                "",
                "",
                0L
        );

        // Формируем запрос на импорт контактов в Telegram
        TdApi.ImportContacts importContacts = new TdApi.ImportContacts(new TdApi.Contact[]{contact});
        TdApi.ImportedContacts imported;
        try {
            imported = simpleTelegramClient.send(importContacts).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        log.info(imported.toString());

        // Берём ID первого (и единственного) импортированного пользователя
        return imported.userIds[0];
    }
}
