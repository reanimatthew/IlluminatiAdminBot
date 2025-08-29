package org.example.illuminatiadminbot.inbound;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.Optional;

public class DataParser {

    private static final int TWO_DIGIT_YEAR_BASE = 2000;

    /**
     * Парсит строку вида dd[sep]MM[sep]yy|yyyy
     * Разделители: пробел, точка, запятая, тире, слэш.
     */
    public Optional<LocalDate> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String normalizedInput = input.trim()
                .replaceAll("[.,\\-/]+", " ")
                .replaceAll("\\s+", " ");

        String[] parts = normalizedInput.split(" ");
        if (parts.length != 3) {
            return Optional.empty();
        }

        String dStr = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
        String mStr = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
        String yStr = parts[2].length() == 2
                ? String.valueOf(TWO_DIGIT_YEAR_BASE + Integer.parseInt(parts[2]))
                : parts[2];

        try {
            int day = Integer.parseInt(dStr);
            int month = Integer.parseInt(mStr);
            int year = Integer.parseInt(yStr);
            return Optional.of(LocalDate.of(year, month, day));
        } catch (NumberFormatException | DateTimeException e) {
            return Optional.empty();
        }
    }

    public boolean isDate(String input) {
        return parse(input).isPresent();
    }
}