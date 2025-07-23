package org.example.illuminatiadminbot.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.mapper.GroupUserMapper;
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
}
