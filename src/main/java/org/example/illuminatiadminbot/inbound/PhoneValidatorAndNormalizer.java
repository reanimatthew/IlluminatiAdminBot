package org.example.illuminatiadminbot.inbound;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PhoneValidatorAndNormalizer {
    private final Pattern PHONE_PATTERN = Pattern.compile("^\\+7\\d{10}$");

    public String validateAndNormalize(String phone) {
        if (phone == null)
            return null;

        String s = phone.trim();
        if (PHONE_PATTERN.matcher(s).matches()) {
            return s;
        }

        String digits = s.replaceAll("\\D+", "");
        if (digits.length() == 11 && (digits.charAt(0) == '7' || digits.charAt(0) == '8')) {
            digits = "7" + digits.substring(1);
        } else if (digits.length() == 10) {
            digits = "7" + digits;
        } else {
            return null;
        }

        return "+" + digits;
    }
}
