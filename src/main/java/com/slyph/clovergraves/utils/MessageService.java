package com.slyph.clovergraves.utils;

import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axapi.utils.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class MessageService {
    private final Config messages;
    private final Config prefixConfig;
    private final String prefixRoute;

    public MessageService(@NotNull Config messages, @NotNull String prefixRoute, @NotNull Config prefixConfig) {
        this.messages = messages;
        this.prefixRoute = prefixRoute;
        this.prefixConfig = prefixConfig;
    }

    public void sendLang(@NotNull CommandSender sender, @NotNull String route) {
        sendLang(sender, route, TagResolver.empty());
    }

    public void sendLang(@NotNull CommandSender sender, @NotNull String route, TagResolver... resolvers) {
        String message = messages.getString(route);
        if (message == null || message.isEmpty()) return;
        sendFormatted(sender, prefix() + message, resolvers);
    }

    public void sendLang(@NotNull CommandSender sender, @NotNull String route, @NotNull Map<String, String> replacements) {
        String message = messages.getString(route);
        if (message == null || message.isEmpty()) return;

        String formatted = prefix() + message;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            formatted = formatted.replace(entry.getKey(), entry.getValue());
        }
        sendFormatted(sender, formatted);
    }

    public void sendFormatted(@NotNull CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(StringUtils.formatToString(message));
    }

    public void sendFormatted(@NotNull CommandSender sender, String message, TagResolver... resolvers) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(StringUtils.formatToString(message, resolvers));
    }

    private String prefix() {
        String prefix = prefixConfig.getString(prefixRoute);
        return prefix == null ? "" : prefix;
    }
}
