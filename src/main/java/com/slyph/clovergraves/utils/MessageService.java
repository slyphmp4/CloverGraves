package com.slyph.clovergraves.utils;

import com.slyph.clovergraves.config.CloverConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class MessageService {
    private final CloverConfig messages;
    private final CloverConfig prefixConfig;
    private final String prefixRoute;

    public MessageService(@NotNull CloverConfig messages, @NotNull String prefixRoute, @NotNull CloverConfig prefixConfig) {
        this.messages = messages;
        this.prefixRoute = prefixRoute;
        this.prefixConfig = prefixConfig;
    }

    public void sendLang(@NotNull CommandSender sender, @NotNull String route) {
        sendLang(sender, route, Map.of());
    }

    public void sendLang(@NotNull CommandSender sender, @NotNull String route, TagResolver... resolvers) {
        List<String> lines = messages.getLines(route);
        for (String line : lines) {
            if (isSpacer(line)) {
                sender.sendMessage(Component.empty());
                continue;
            }
            sender.sendMessage(TextFormatter.format(prefix() + line, resolvers));
        }
    }

    public void sendLang(@NotNull CommandSender sender, @NotNull String route, @NotNull Map<String, String> replacements) {
        List<String> lines = messages.getLines(route);
        for (String line : lines) {
            if (isSpacer(line)) {
                sender.sendMessage(Component.empty());
                continue;
            }
            sender.sendMessage(TextFormatter.format(prefix() + TextFormatter.replace(line, replacements)));
        }
    }

    public void sendFormatted(@NotNull CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(TextFormatter.format(message));
    }

    public void sendFormatted(@NotNull CommandSender sender, String message, TagResolver... resolvers) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(TextFormatter.format(message, resolvers));
    }

    @NotNull
    public Component component(@NotNull String route, @NotNull Map<String, String> replacements) {
        String line = messages.getFirstLine(route, "");
        return TextFormatter.format(prefix() + TextFormatter.replace(line, replacements));
    }

    @NotNull
    public String raw(@NotNull String route, @NotNull String def) {
        return messages.getFirstLine(route, def);
    }

    private boolean isSpacer(String line) {
        return line.isBlank() || line.equalsIgnoreCase("&7");
    }

    private String prefix() {
        return prefixConfig.getString(prefixRoute, "");
    }
}
