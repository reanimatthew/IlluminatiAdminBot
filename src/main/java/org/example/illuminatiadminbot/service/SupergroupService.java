package org.example.illuminatiadminbot.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.mapper.GroupUserMapper;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.outbound.repository.GroupUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupergroupService {

    private final SimpleTelegramClient simpleTelegramClient;
    private final GroupUserMapper groupUserMapper;
    private final GroupUserRepository groupUserRepository;

    @Value("${supergroup.id}")
    private Long supergroupId;

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

        long userId = imported.userIds[0];
        if (userId == 0L)
            return null;

        if (getAllAdminIds().contains(userId))
            return groupUserRepository.findByTelegramId(userId);

        long supergroupIdLong = Long.parseLong("-100" + supergroupId);
        TdApi.AddChatMember inviteRequest = new TdApi.AddChatMember(
                supergroupIdLong,
                userId,
                0
        );

        try {
            simpleTelegramClient.send(inviteRequest).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        GroupUser groupUser = getGroupUserById(userId);

        TdApi.RemoveContacts removeRequest = new TdApi.RemoveContacts(new long[]{userId});

        try {
            simpleTelegramClient.send(removeRequest).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }


        return groupUser;
    }

    private List<Long> getAllAdminIds() {

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

        return GroupUser.builder()
                .telegramId(userId)
                .nickname(nickname)
                .role("member")
                .status("active")
                .build();
    }

    public boolean banUser(String nickname) {

        Long userId = getAllMembers().entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), nickname))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("SupergroupService.banUser: нет пользователя с ником: " + nickname));

        TdApi.BanChatMember banRequest = new TdApi.BanChatMember(
                Long.parseLong("-100" + supergroupId),
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
}
