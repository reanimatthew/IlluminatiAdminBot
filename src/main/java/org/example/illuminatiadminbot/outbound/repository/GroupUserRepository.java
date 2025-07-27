package org.example.illuminatiadminbot.outbound.repository;

import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupUserRepository extends JpaRepository<GroupUser,Long> {
    void deleteByTelegramId(Long adminTelegramId);

    Optional<GroupUser> findByNickname(String nickname);

    List<GroupUser> findAllByTelegramUserStatus(String role);

    List<GroupUser> findAllByTelegramUserStatusAndChatIdIsNotNull(String role);

    Optional<GroupUser> findByTelegramId(long userId);
}
