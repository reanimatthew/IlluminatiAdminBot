package org.example.illuminatiadminbot.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class SupergroupService {

    private final SimpleTelegramClient simpleTelegramClient;

    @Value("${supergroup.id}")
    private Long supergroupId;

    public String getAllMembers() throws ExecutionException, InterruptedException, TimeoutException {

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

            TdApi.ChatMembers chunk = future.get(30, TimeUnit.SECONDS);
            Collections.addAll(allMembers, chunk.members);
            if (chunk.members.length < limit)
                break;
            offset += chunk.members.length;
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (TdApi.ChatMember chatMember : allMembers) {
            TdApi.MessageSenderUser messageSenderUser = (TdApi.MessageSenderUser) chatMember.memberId;
            stringBuilder.append("ID: ").append(messageSenderUser.userId);
            TdApi.User user = simpleTelegramClient
                    .send(new TdApi.GetUser(messageSenderUser.userId))      // отправили запрос
                    .get(5, TimeUnit.SECONDS);

            if (user.usernames != null) {
                stringBuilder.append(", nick: ").append(user.usernames.editableUsername);
            }
            stringBuilder.append(", name: ").append(user.firstName);
            stringBuilder.append(", last name: ").append(user.lastName).append("\n");
        }

        return stringBuilder.toString();
    }
}
