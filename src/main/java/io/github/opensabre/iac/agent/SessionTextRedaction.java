package io.github.opensabre.iac.agent;

import java.util.regex.Pattern;

/** Masks recognizable contact and credential formats in the management view of a conversation. */
final class SessionTextRedaction {
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9_./+=-]+");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)([\"']?(?:api[-_ ]?key|password|client[-_ ]?secret|access[-_ ]?token)[\"']?\\s*[:=]\\s*)"
            + "(?:\"[^\"\\r\\n]+\"|'[^'\\r\\n]+'|[^\\s,;]+)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[ -]?)?1[3-9]\\d{9}(?!\\d)");

    private SessionTextRedaction() { }

    static String mask(String text) {
        if (text == null) return null;
        String masked = BEARER.matcher(text).replaceAll("Bearer [已脱敏]");
        masked = SECRET.matcher(masked).replaceAll("$1[已脱敏]");
        masked = EMAIL.matcher(masked).replaceAll("[邮箱已脱敏]");
        return PHONE.matcher(masked).replaceAll("[手机号已脱敏]");
    }
}
