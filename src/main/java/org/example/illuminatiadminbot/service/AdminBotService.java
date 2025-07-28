package org.example.illuminatiadminbot.service;

import lombok.AllArgsConstructor;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.inbound.model.UploadDetails;
import org.example.illuminatiadminbot.inbound.model.UserStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AdminBotService {

    private final GroupUserRepository groupUserRepository;
    private final BotMinioClient botMinioClient;
    private final MinioFileDetailRepository minioFileDetailRepository;

    @Transactional
    public String subscribeUser(String nickname, ArrayList<String> subscriptionDetails) {
        String subscriptionType = subscriptionDetails.get(0);
        String numOfMonth = subscriptionDetails.get(1);
        int subscribeDuration = Integer.parseInt(numOfMonth.substring(0, numOfMonth.length() - 1));
        LocalDate expirationDate = LocalDate.now().plusMonths(subscribeDuration);
        GroupUser groupUser;
        Optional<GroupUser> userOptional = groupUserRepository.findByNickname(nickname);
        if (userOptional.isEmpty()) {
            groupUser = GroupUser.builder()
                    .nickname(nickname)
                    .telegramId(-1L)
                    .subscriptionType(subscriptionType)
                    .subscriptionDuration(subscribeDuration)
                    .subscriptionExpiration(expirationDate)
                    .telegramUserStatus(TelegramUserStatus.ADMINISTRATOR)
                    .build();
            groupUserRepository.save(groupUser);
        } else {
            groupUser = userOptional.get();
            groupUser.setSubscriptionType(subscriptionType);
            groupUser.setSubscriptionDuration(subscribeDuration);
            groupUser.setSubscriptionExpiration(expirationDate);
            groupUserRepository.save(groupUser);
        }
        return "Подписчик: " + nickname + ", подписка: " + subscriptionDetails.get(0) + ", срок: " + subscribeDuration + " мес., до: " + expirationDate;
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
            groupUser.setStatus(UserStatus.BANNED.toString());
            groupUserRepository.save(groupUser);
        } catch (NoSuchElementException _) {
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
        return admins.stream().map(GroupUser::getChatId).collect(Collectors.toList());
    }

    @Transactional
    public void saveChatId(String nickname, Long ChatId) {
        Optional<GroupUser> groupUserOptional = groupUserRepository.findByNickname(nickname);
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
}
