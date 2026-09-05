package com.slyph.clovergraves.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextFormatter {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Pattern HEX = Pattern.compile("(?i)&(?:#)?([0-9a-f]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)&x&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])");

    private TextFormatter() {
    }

    @NotNull
    public static Component format(@NotNull String input, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(toMiniMessage(input), resolvers);
    }

    @NotNull
    public static Component format(@NotNull String input, @NotNull Map<String, String> replacements) {
        return format(replace(input, replacements));
    }

    @NotNull
    public static String formatToString(@NotNull String input, TagResolver... resolvers) {
        return LEGACY.serialize(format(input, resolvers));
    }

    @NotNull
    public static String formatToString(@NotNull String input, @NotNull Map<String, String> replacements) {
        return LEGACY.serialize(format(input, replacements));
    }

    @NotNull
    public static String formatTime(long millis) {
        long total = Duration.ofMillis(Math.max(0L, millis)).getSeconds();
        long hours = total / 3600;
        long minutes = total % 3600 / 60;
        long seconds = total % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @NotNull
    public static String replace(@NotNull String input, @NotNull Map<String, String> replacements) {
        String result = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @NotNull
    private static String toMiniMessage(@NotNull String input) {
        String value = input.replace('§', '&');

        Matcher legacyHex = LEGACY_HEX.matcher(value);
        StringBuilder legacyHexBuffer = new StringBuilder();
        while (legacyHex.find()) {
            String color = legacyHex.group(1) + legacyHex.group(2) + legacyHex.group(3)
                    + legacyHex.group(4) + legacyHex.group(5) + legacyHex.group(6);
            legacyHex.appendReplacement(legacyHexBuffer, Matcher.quoteReplacement("<reset><#" + color + ">"));
        }
        legacyHex.appendTail(legacyHexBuffer);
        value = legacyHexBuffer.toString();

        Matcher hex = HEX.matcher(value);
        StringBuilder hexBuffer = new StringBuilder();
        while (hex.find()) {
            hex.appendReplacement(hexBuffer, Matcher.quoteReplacement("<reset><#" + hex.group(1) + ">"));
        }
        hex.appendTail(hexBuffer);
        value = hexBuffer.toString();

        Matcher legacy = LEGACY_CODE.matcher(value);
        StringBuilder buffer = new StringBuilder();
        while (legacy.find()) {
            legacy.appendReplacement(buffer, Matcher.quoteReplacement(tag(legacy.group(1).charAt(0))));
        }
        legacy.appendTail(buffer);
        return buffer.toString();
    }

    private static String tag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<reset><black>";
            case '1' -> "<reset><dark_blue>";
            case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>";
            case '4' -> "<reset><dark_red>";
            case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>";
            case '7' -> "<reset><gray>";
            case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>";
            case 'a' -> "<reset><green>";
            case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>";
            case 'd' -> "<reset><light_purple>";
            case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }
}
