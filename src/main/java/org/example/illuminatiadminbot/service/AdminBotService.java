package org.example.illuminatiadminbot.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.inbound.menu.Subscription;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.inbound.model.UploadDetails;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.outbound.model.MinioFileDetail;
import org.example.illuminatiadminbot.outbound.model.MinioFileNameDetail;
import org.example.illuminatiadminbot.outbound.repository.BotMinioClient;
import org.example.illuminatiadminbot.outbound.repository.GroupUserRepository;
import org.example.illuminatiadminbot.outbound.repository.MinioFileDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class AdminBotService {

    private final GroupUserRepository groupUserRepository;
    private final BotMinioClient botMinioClient;
    private final MinioFileDetailRepository minioFileDetailRepository;

    @Transactional
    public GroupUser subscribeUser(String nickname, long telegramId, ArrayList<String> subscriptionDetails) {
        String subscriptionType = subscriptionDetails.get(0);
        String numOfMonth = subscriptionDetails.get(1);
        int subscribeDuration = Integer.parseInt(numOfMonth.substring(0, numOfMonth.length() - 1));
        LocalDate expirationDate = LocalDate.now(ZoneId.of("Europe/Moscow")).plusMonths(subscribeDuration);
        GroupUser groupUser;
        Optional<GroupUser> userOptional = groupUserRepository.findByNickname(nickname);
        if (userOptional.isEmpty()) {
            groupUser = GroupUser.builder()
                    .nickname(nickname)
                    .telegramId(telegramId)
                    .subscriptionType(Subscription.fromString(subscriptionType))
                    .subscriptionDuration(subscribeDuration)
                    .subscriptionExpiration(expirationDate)
                    .telegramUserStatus(TelegramUserStatus.MEMBER)
                    .build();
            groupUserRepository.save(groupUser);
        } else {
            groupUser = userOptional.get();
            if (groupUser.getTelegramUserStatus().equals(TelegramUserStatus.MEMBER)) {
                groupUser.setSubscriptionType(Subscription.fromString(subscriptionType));
                groupUser.setSubscriptionDuration(subscribeDuration);
                groupUser.setSubscriptionExpiration(expirationDate);
                groupUserRepository.save(groupUser);
            }
        }
        return groupUser;
    }

    @Transactional
    public GroupUser subscribeNewUser(String nickname, long telegramId, ArrayList<String> subscriptionDetails) {
        String subscriptionType = subscriptionDetails.get(0);
        String numOfMonth = subscriptionDetails.get(1);
        int subscribeDuration = Integer.parseInt(numOfMonth.substring(0, numOfMonth.length() - 1));
        LocalDate expirationDate = LocalDate.now(ZoneId.of("Europe/Moscow")).plusMonths(subscribeDuration);
        GroupUser groupUser = GroupUser.builder()
                .nickname(nickname)
                .telegramId(telegramId)
                .subscriptionType(Subscription.fromString(subscriptionType))
                .subscriptionDuration(subscribeDuration)
                .subscriptionExpiration(expirationDate)
                .telegramUserStatus(TelegramUserStatus.MEMBER)
                .build();
        groupUserRepository.save(groupUser);
        return groupUser;
    }

    public List<GroupUser> adminShow() {
        return groupUserRepository.findAllByTelegramUserStatus(TelegramUserStatus.ADMINISTRATOR);
    }

    public String loadUsers() {
        return null;
    }

    public InputFile uploadFile(UploadDetails uploadDetails, InputStream uploadFile) {
        MinioFileNameDetail detail = botMinioClient.uploadFileToMinio(uploadDetails, uploadFile);
        uploadDetails.setMinioFilePath(detail.getMinioFilePath());
        uploadDetails.setMinioFileName(detail.getOriginalFileName());
        return botMinioClient.getInputFileFromMinio(detail);
    }

    @Transactional
    public MinioFileDetail uploadFileDescription(String fileDescription, UploadDetails uploadDetails) {
        MinioFileDetail minioFileDetail = MinioFileDetail.builder()
                .name(uploadDetails.getFileName())
                .path(uploadDetails.getMinioFilePath())
                .type(uploadDetails.getFileType())
                .description(fileDescription)
                .build();

        minioFileDetailRepository.save(minioFileDetail);
        return minioFileDetail;
    }

    @Transactional
    public boolean banUser(String nickname) {
        try {
            Optional<GroupUser> groupUserOptional = groupUserRepository.findByNickname(nickname);
            if (groupUserOptional.isEmpty()) {
                throw new NoSuchElementException();
            }
            GroupUser groupUser = groupUserOptional.get();
            groupUser.setTelegramUserStatus(TelegramUserStatus.BANNED);
            groupUserRepository.save(groupUser);
        } catch (NoSuchElementException e) {
            return false;
        }
        return true;
    }

    @Transactional
    public List<GroupUser> getAllUsers() {
        return groupUserRepository.findAll();
    }

    @Transactional
    public void saveAll(List<GroupUser> realGroupUsers) {
        if (!realGroupUsers.isEmpty())
            groupUserRepository.saveAll(realGroupUsers);
    }

    public String getAllAdmins() {
        List<GroupUser> admins = groupUserRepository.findAllByTelegramUserStatus(TelegramUserStatus.ADMINISTRATOR);
        return "Существующие администраторы:\n" + admins.stream().map(GroupUser::getNickname).collect(Collectors.joining("\n"));
    }

    public List<Long> getAllAdminsChatId() {
        List<GroupUser> admins = groupUserRepository.findAllByTelegramUserStatusAndChatIdIsNotNull(TelegramUserStatus.ADMINISTRATOR);
        admins.add(groupUserRepository.findByTelegramUserStatus(TelegramUserStatus.CREATOR));
        return admins.stream().map(GroupUser::getChatId).toList();
    }

    @Transactional
    public void saveChatId(Long telegramId, Long ChatId) {
        Optional<GroupUser> groupUserOptional = groupUserRepository.findByTelegramId(telegramId);
        if (groupUserOptional.isPresent()) {
            GroupUser groupUser = groupUserOptional.get();
            groupUser.setChatId(ChatId);
            groupUserRepository.save(groupUser);
        }
    }

    @Transactional
    public void saveGroupUser(GroupUser groupAdmin) {
        groupUserRepository.save(groupAdmin);
    }

    public List<Long> getAllAdminOrCreatorIds() {
        List<GroupUser> allByTelegramUserStatus = groupUserRepository.findAllByTelegramUserStatus(TelegramUserStatus.ADMINISTRATOR);
        allByTelegramUserStatus.addAll(groupUserRepository.findAllByTelegramUserStatus(TelegramUserStatus.CREATOR));
        return allByTelegramUserStatus.stream().map(GroupUser::getTelegramId).collect(Collectors.toList());
    }

    public boolean inSql(String nickname) {
        return groupUserRepository.existsGroupUserByNickname(nickname);
    }

    @Transactional
    public GroupUser subscribeExistedGroupUser(GroupUser groupUser, ArrayList<String> subscriptionDetails) {
        String subscriptionType = subscriptionDetails.get(0);
        String numOfMonth = subscriptionDetails.get(1);
        int subscribeDuration = Integer.parseInt(numOfMonth.substring(0, numOfMonth.length() - 1));
        LocalDate expirationDate = LocalDate.now(ZoneId.of("Europe/Moscow")).plusMonths(subscribeDuration);
        groupUser.setSubscriptionType(Subscription.fromString(subscriptionType));
        groupUser.setSubscriptionDuration(subscribeDuration);
        groupUser.setSubscriptionExpiration(expirationDate);
        return groupUserRepository.save(groupUser);
    }

    public GroupUser getUser(String nickname) {
        Optional<GroupUser> groupUserOptional = groupUserRepository.findByNickname(nickname);
        return groupUserOptional.orElse(null);
    }

    public List<GroupUser> getUsersWithExpiringSubscriptions() {
        return groupUserRepository.findAllByTelegramUserStatusInAndSubscriptionExpirationBetween(TelegramUserStatus.getActiveStatuses(), LocalDate.now(ZoneId.of("Europe/Moscow")), LocalDate.now(ZoneId.of("Europe/Moscow")).plusWeeks(1L)).get();
    }

    public List<GroupUser> getUsersWithExpiredSubscriptions() {
        return groupUserRepository.findAllByTelegramUserStatusInAndSubscriptionExpirationBefore(TelegramUserStatus.getActiveStatuses(), LocalDate.now(ZoneId.of("Europe/Moscow")));
    }

}
