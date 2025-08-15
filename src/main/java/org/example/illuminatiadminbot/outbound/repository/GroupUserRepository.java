package org.example.illuminatiadminbot.outbound.repository;

import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupUserRepository extends JpaRepository<GroupUser,Long> {
    void deleteByTelegramId(Long adminTelegramId);

    Optional<GroupUser> findByNickname(String nickname);

    List<GroupUser> findAllByTelegramUserStatus(TelegramUserStatus role);

    List<GroupUser> findAllByTelegramUserStatusAndChatIdIsNotNull(TelegramUserStatus role);

    Optional<GroupUser> findByTelegramId(long userId);

    boolean existsGroupUserByNickname(String nickname);

    Optional<List<GroupUser>> findAllByTelegramUserStatusNotInAndSubscriptionExpirationBetween(Collection<TelegramUserStatus> excludedStatuses, LocalDate start, LocalDate end);

    GroupUser findByTelegramUserStatus(TelegramUserStatus telegramUserStatus);

    Optional<List<GroupUser>> findAllByTelegramUserStatusInAndSubscriptionExpirationBetween(List<TelegramUserStatus> activeStatuses, LocalDate now, LocalDate localDate);

    List<GroupUser> findAllByTelegramUserStatusInAndSubscriptionExpirationBefore(List<TelegramUserStatus> activeStatuses, LocalDate now);
}
