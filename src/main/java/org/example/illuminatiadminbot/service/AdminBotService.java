package org.example.illuminatiadminbot.service;

import lombok.AllArgsConstructor;
import org.example.illuminatiadminbot.inbound.model.UploadDetails;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.example.illuminatiadminbot.outbound.model.MinioFileDetail;
import org.example.illuminatiadminbot.outbound.model.MinioFileNameDetail;
import org.example.illuminatiadminbot.outbound.repository.BotMinioClient;
import org.example.illuminatiadminbot.outbound.repository.GroupUserRepository;
import org.example.illuminatiadminbot.outbound.repository.MinioFileDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

@Service
@AllArgsConstructor
public class AdminBotService {

    private final GroupUserRepository groupUserRepository;
    private final BotMinioClient botMinioClient;
    private final MinioFileDetailRepository minioFileDetailRepository;

    @Transactional
    public String adminAddOrRemove(Long adminTelegramId, String adminAction) {
        String nickname = "@nickname:" + adminTelegramId.toString();
        LocalDate expirationDate = LocalDate.now().plusDays(3000);
        if (adminAction.equals("ADD")) {
            GroupUser groupUser = GroupUser.builder()
                    .telegramId(adminTelegramId)
                    .nickname(nickname)
                    .role("admin")
                    .subscriptionType("Admin")
                    .subscriptionDuration(3000)
                    .subscriptionExpiration(expirationDate)
                    .build();
            groupUserRepository.save(groupUser);
            return "Администратор c id: " + adminTelegramId + " и nickname: " + nickname + " добавлен";
        } else if (adminAction.equals("REMOVE")) {
            groupUserRepository.deleteByTelegramId(adminTelegramId);
            return "Администратор c id: " + adminTelegramId + " и nickname: " + nickname + " удалён";
        } else {
            return "В ответе нет ни ADD, ни REMOVE";
        }
    }

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
                    .role("subscriber")
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

    public String adminShow(String addOrRemove) {
        String head = addOrRemove.equals("ADD") ? "Добавление администратора\n" : "Удаление администратора\n";
        if (addOrRemove.equals("ADD")) {

        }
        return head + "Список активных администраторов:\n@zaychik, id: 123456\n@belocka, id: 987654\nВведите id администратора";
    }

    public String loadUsers() {
        return null;
    }

    public String uploadFile(UploadDetails uploadDetails, String fileName, InputStream uploadFile) {
        MinioFileNameDetail detail = botMinioClient.uploadFileToMinio(uploadDetails.getFileType(), fileName, uploadFile);
        botMinioClient.getInputFileFromMinio(detail);
        return
    }

    @Transactional
    public MinioFileDetail uploadFileDescription(String fileDescription, UploadDetails uploadDetails) {
        MinioFileDetail minioFileDetail = MinioFileDetail.builder()
                .name(uploadDetails.getFileName())
                .path(uploadDetails.getFilePath())
                .type(uploadDetails.getFileType())
                .description(fileDescription)
                .build();

        minioFileDetailRepository.save(minioFileDetail);
        return minioFileDetail;
    }
}
