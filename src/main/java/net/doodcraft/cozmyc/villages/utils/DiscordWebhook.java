package net.doodcraft.cozmyc.villages.utils;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {
    private final String url;
    private final JavaPlugin plugin;

    public DiscordWebhook(JavaPlugin plugin, String url) {
        this.plugin = plugin;
        this.url = url;
    }

    public void sendMessage(String title, String description, int color) {
        if (url == null || url.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject embed = new JsonObject();
                embed.addProperty("title", title);
                embed.addProperty("description", description);
                embed.addProperty("color", color);

                JsonObject payload = new JsonObject();
                payload.add("embeds", new com.google.gson.JsonArray());
                payload.getAsJsonArray("embeds").add(embed);

                URL webhookUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) webhookUrl.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != 204) {
                    plugin.getLogger().warning("Failed to send Discord webhook. Response code: " + responseCode);
                }

                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Error sending Discord webhook: " + e.getMessage());
            }
        });
    }
} 