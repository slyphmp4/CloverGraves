package com.slyph.clovergraves.utils;

import com.slyph.clovergraves.AxGraves;
import com.slyph.clovergraves.config.CloverConfig;
import com.slyph.clovergraves.schedulers.CloverScheduler;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateNotifier implements Listener {
    private static final Pattern TAG_NAME = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static CloverConfig config;
    private static boolean onJoin;

    private final String current;
    private volatile String latest;
    private volatile boolean newest = true;

    public static void init(CloverConfig configuration) {
        config = configuration;
        reload();
    }

    public static void reload() {
        onJoin = config != null && config.getBoolean("update-notifier.on-join", true);
    }

    public UpdateNotifier() {
        current = AxGraves.getInstance().getDescription().getVersion();
        Bukkit.getPluginManager().registerEvents(this, AxGraves.getInstance());

        long period = 30L * 60L * 20L;
        CloverScheduler.get().runAsyncTimer(task -> {
            latest = readVersion();
            newest = latest == null || !isOutdated(latest, current);
            if (latest == null || newest) return;

            CloverScheduler.get().run(() -> AxGraves.MESSAGEUTILS.sendLang(Bukkit.getConsoleSender(), "update-notifier", Map.of(
                    "%current%", current,
                    "%latest%", latest
            )));
            task.cancel();
        }, 1L, period);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (latest == null || newest || !onJoin) return;
        if (!event.getPlayer().hasPermission("axgraves.update-notify")) return;
        AxGraves.MESSAGEUTILS.sendLang(event.getPlayer(), "update-notifier", Map.of(
                "%current%", current,
                "%latest%", latest
        ));
    }

    @Nullable
    private String readVersion() {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/slyphmp4/CloverGraves/releases/latest"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "CloverGraves/" + current)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            Matcher matcher = TAG_NAME.matcher(response.body());
            if (!matcher.find()) return null;
            String tag = matcher.group(1).trim();
            return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String getLatest() {
        return latest;
    }

    public boolean isOutdated(String current) {
        return latest != null && isOutdated(latest, current);
    }

    static boolean isOutdated(String latest, String current) {
        if (latest == null || current == null) return false;
        String[] newer = latest.split("\\.");
        String[] installed = current.split("\\.");
        if (newer.length < 3 || installed.length < 3) return false;

        for (int i = 0; i < 3; i++) {
            Integer a = parseComponent(newer[i]);
            Integer b = parseComponent(installed[i]);
            if (a == null || b == null) return false;
            if (a > b) return true;
            if (a < b) return false;
        }
        return false;
    }

    private static Integer parseComponent(String part) {
        try {
            return Integer.parseInt(part.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
