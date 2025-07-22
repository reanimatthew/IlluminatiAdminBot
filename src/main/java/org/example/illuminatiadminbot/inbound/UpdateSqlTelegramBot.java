package org.example.illuminatiadminbot.inbound;

// TODO бот для обновления БД пользователей и админов. Сделать с помощью MTProto.

import it.tdlight.jni.TdApi;
import org.example.illuminatiadminbot.mapper.GroupUserMapper;
import org.example.illuminatiadminbot.outbound.dto.GroupUserDto;
import org.example.illuminatiadminbot.outbound.model.ExistingUser;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.service.AdminBotService;
import org.example.illuminatiadminbot.service.SupergroupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Component
public class UpdateSqlTelegramBot {

    private final AdminBotService adminBotService;
    private final SupergroupService supergroupService;
    private final GroupUserMapper groupUserMapper;

    public UpdateSqlTelegramBot(AdminBotService adminBotService, SupergroupService supergroupService, GroupUserMapper groupUserMapper) {
        this.adminBotService = adminBotService;
        this.supergroupService = supergroupService;
        this.groupUserMapper = groupUserMapper;
    }

    @Scheduled(initialDelay = 10, fixedRate = 60000)
    public void scanAndCorrect() {
        Map<Long, String> existingUsers = supergroupService.getAllMembers();
        List<GroupUser> groupUsers = adminBotService.getAllUsers();
        for ()


    }

}
