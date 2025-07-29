package org.example.illuminatiadminbot.inbound.menu;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Service
public class MenuBuilder {

    public InlineKeyboardMarkup getMain(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("Подписка")
                .callbackData("SUBSCRIPTION")
                .build();

        InlineKeyboardButton btn2 = InlineKeyboardButton.builder()
                .text("Контент")
                .callbackData("UPLOAD")
                .build();

        InlineKeyboardButton btn3 = InlineKeyboardButton.builder()
                .text("Администраторы")
                .callbackData("ADMIN-ADD-REMOVE")
                .build();

        InlineKeyboardButton btn4 = InlineKeyboardButton.builder()
                .text("Блокировка пользователя")
                .callbackData("BAN-USER")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1, btn2, btn3, btn4);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .build();
    }

    public InlineKeyboardMarkup getSubscription(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("Basic")
                .callbackData("BASIC")
                .build();

        InlineKeyboardButton btn2 = InlineKeyboardButton.builder()
                .text("Premium")
                .callbackData("PREMIUM")
                .build();

        InlineKeyboardButton btn3 = InlineKeyboardButton.builder()
                .text("Gold")
                .callbackData("GOLD")
                .build();

        InlineKeyboardButton btn4 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-MAIN")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1, btn2, btn3);
        InlineKeyboardRow row2 = new InlineKeyboardRow(btn4);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .keyboardRow(row2)
                .build();
    }

    public InlineKeyboardMarkup getDuration(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("1 мес.")
                .callbackData("1M")
                .build();

        InlineKeyboardButton btn2 = InlineKeyboardButton.builder()
                .text("3 мес.")
                .callbackData("3M")
                .build();

        InlineKeyboardButton btn3 = InlineKeyboardButton.builder()
                .text("6 мес.")
                .callbackData("6M")
                .build();

        InlineKeyboardButton btn4 = InlineKeyboardButton.builder()
                .text("12 мес.")
                .callbackData("12M")
                .build();

        InlineKeyboardButton btn5 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-SUBSCRIPTION")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1, btn2, btn3, btn4);
        InlineKeyboardRow row2 = new InlineKeyboardRow(btn5);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .keyboardRow(row2)
                .build();
    }

    public InlineKeyboardMarkup getSubscriberId(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-DURATION")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .build();
    }

    public InlineKeyboardMarkup getTheme(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text(ThemeState.STRENGTH.getDisplayName())
                .callbackData(ThemeState.STRENGTH.name())
                .build();

        InlineKeyboardButton btn2 = InlineKeyboardButton.builder()
                .text(ThemeState.PERCEPTION.getDisplayName())
                .callbackData(ThemeState.PERCEPTION.name())
                .build();

        InlineKeyboardButton btn3 = InlineKeyboardButton.builder()
                .text(ThemeState.ENDURANCE.getDisplayName())
                .callbackData(ThemeState.ENDURANCE.name())
                .build();

        InlineKeyboardButton btn4 = InlineKeyboardButton.builder()
                .text(ThemeState.CHARISMA.getDisplayName())
                .callbackData(ThemeState.CHARISMA.name())
                .build();

        InlineKeyboardButton btn5 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-MAIN")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1, btn2, btn3, btn4);
        InlineKeyboardRow row2 = new InlineKeyboardRow(btn5);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .keyboardRow(row2)
                .build();
    }

    public InlineKeyboardMarkup getUpload(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("Текст")
                .callbackData("TEXT")
                .build();

        InlineKeyboardButton btn2 = InlineKeyboardButton.builder()
                .text("Аудио")
                .callbackData("AUDIO")
                .build();

        InlineKeyboardButton btn3 = InlineKeyboardButton.builder()
                .text("Видео")
                .callbackData("VIDEO")
                .build();

        InlineKeyboardButton btn4 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-MAIN")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1, btn2, btn3);
        InlineKeyboardRow row2 = new InlineKeyboardRow(btn4);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .keyboardRow(row2)
                .build();
    }

    public InlineKeyboardMarkup getFile(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-UPLOAD")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .build();
    }

    public InlineKeyboardMarkup getDescription(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-UPLOAD")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .build();
    }

    public InlineKeyboardMarkup getAdmin(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("Добавить администратора")
                .callbackData("ADD")
                .build();

        InlineKeyboardButton btn2 = InlineKeyboardButton.builder()
                .text("Удалить администратора")
                .callbackData("REMOVE")
                .build();

        InlineKeyboardButton btn3 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-MAIN")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1, btn2);
        InlineKeyboardRow row2 = new InlineKeyboardRow(btn3);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .keyboardRow(row2)
                .build();

    }

    public InlineKeyboardMarkup getAppointment(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-ADMIN-ADD-REMOVE")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .build();
    }

    public InlineKeyboardMarkup getBanUser(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-MAIN")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .build();
    }

    public InlineKeyboardMarkup getDismissal(Update update) {

        InlineKeyboardButton btn1 = InlineKeyboardButton.builder()
                .text("← Назад")
                .callbackData("BACK-TO-ADMIN-ADD-REMOVE")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow(btn1);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(row1)
                .build();
    }

    public InlineKeyboardMarkup getNickOrPhone(Update update) {
    }
}
