package org.example.illuminatiadminbot.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.inbound.model.UserStatus;
import org.example.illuminatiadminbot.mapper.ChatMemberStatusMapper;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.outbound.repository.GroupUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public GroupUser addUserToSupergroup(String phone) {
        TdApi.Contact contact = new TdApi.Contact(
                phone,
                "",
                "",
                "",
                0L
        );

        TdApi.ImportContacts importContacts = new TdApi.ImportContacts(new TdApi.Contact[]{contact});
        TdApi.ImportedContacts imported;
        try {
            imported = simpleTelegramClient.send(importContacts).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        log.info(imported.toString());

        long userId = imported.userIds[0];
        if (userId == 0L)
            return null;

        if (getAllAdminIdsFromSupergroup().contains(userId)) {
            Optional<GroupUser> groupUserOptional = groupUserRepository.findByTelegramId(userId);
            GroupUser groupUser = groupUserOptional.orElseGet(() -> activateGroupUserById(userId, TelegramUserStatus.ADMINISTRATOR));
            return groupUserRepository.save(groupUser);
        }

        TdApi.AddChatMember inviteRequest = new TdApi.AddChatMember(
                chatId,
                userId,
                0
        );

        try {
            simpleTelegramClient.send(inviteRequest).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        GroupUser groupUser = activateGroupUserById(userId, TelegramUserStatus.ADMINISTRATOR);

        TdApi.RemoveContacts removeRequest = new TdApi.RemoveContacts(new long[]{userId});

        try {
            simpleTelegramClient.send(removeRequest).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }


        return groupUser;
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

    public GroupUser getGroupUserById(long userId) {

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

        TdApi.ChatMember chatMember;
        try {
            chatMember = simpleTelegramClient.send(
                    new TdApi.GetChatMember(
                            chatId,
                            new TdApi.MessageSenderUser(userId)
                    )
            ).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        TelegramUserStatus telegramStatus = chatMemberStatusMapper.simplifyStatus(chatMember.status);

        return GroupUser.builder()
                .telegramId(userId)
                .nickname(nickname)
                .telegramUserStatus(chatMemberStatusMapper.simplifyStatus(chatMember.status))
                .build();
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
            groupUser.setStatus(UserStatus.ACTIVE.name());
            groupUser.setTelegramUserStatus(chatMemberStatusMapper.simplifyStatus(chatMember.status));
            groupUsers.add(groupUser);
        }

        return groupUsers;
    }

    public GroupUser activateGroupUserById(long userId, TelegramUserStatus telegramUserStatus) {

        // проверяем, есть ли такой пользователь в БД
        Optional<GroupUser> groupUserOptional = groupUserRepository.findByTelegramId(userId);
        if (groupUserOptional.isPresent()) {
            GroupUser groupUser = groupUserOptional.get();
            groupUser.setStatus(UserStatus.ACTIVE.name());
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
                .status(UserStatus.ACTIVE.name())
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
}
