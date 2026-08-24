package dev.kwlew.haven.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * All user-facing text, loaded from {@code messages.yml} and parsed with MiniMessage.
 * <p>
 * Loads eagerly in the constructor for the same reason as
 * {@link dev.kwlew.haven.config.HavenConfig}, and re-reads on {@link #reload()}.
 * <p>
 * The bundled copy inside the jar is installed as the defaults, so keys added in a later version
 * still resolve against an older on-disk file instead of coming back null.
 */
public class Messages {

    private static final String FILE_NAME = "messages.yml";

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private YamlConfiguration messages;
    private Component prefix = Component.empty();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        try (InputStream bundled = plugin.getResource(FILE_NAME)) {
            if (bundled != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + FILE_NAME, e);
        }

        this.messages = loaded;
        this.prefix = parseOrEmpty("prefix", loaded.getString("prefix", ""));

        validate(loaded);
    }

    /**
     * Deserialises every string up front so a mistyped tag is reported on load or {@code /haven
     * reload}, rather than the first time a player happens to trigger that one message.
     */
    private void validate(YamlConfiguration loaded) {
        int broken = 0;

        for (String key : loaded.getKeys(true)) {
            String raw = loaded.getString(key);

            if (raw == null || raw.isEmpty()) {
                continue;
            }

            try {
                miniMessage.deserialize(raw);
            } catch (RuntimeException e) {
                broken++;
                plugin.getLogger().warning("Message '" + key + "' is not valid MiniMessage: " + e.getMessage());
            }
        }

        if (broken > 0) {
            plugin.getLogger().warning(broken + " message(s) failed to parse and will render as plain text.");
        }
    }

    private Component parseOrEmpty(String key, String raw) {
        try {
            return miniMessage.deserialize(raw);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not parse message '" + key + "'", e);
            return Component.empty();
        }
    }

    /**
     * Renders a message. Returns {@code null} when the key resolves to an empty string, which is
     * how an admin silences an individual message.
     */
    public Component render(String key, TagResolver... resolvers) {
        String raw = messages.getString(key);

        if (raw == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return Component.text("<missing message: " + key + ">");
        }

        if (raw.isEmpty()) {
            return null;
        }

        try {
            return miniMessage.deserialize(raw, withPrefix(resolvers));
        } catch (RuntimeException e) {
            // An admin's typo in messages.yml must not propagate out of a command handler.
            plugin.getLogger().log(Level.WARNING, "Could not render message '" + key + "'", e);
            return Component.text(raw);
        }
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        Component message = render(key, resolvers);

        if (message != null) {
            audience.sendMessage(message);
        }
    }

    private TagResolver[] withPrefix(TagResolver[] resolvers) {
        TagResolver[] combined = new TagResolver[resolvers.length + 1];
        combined[0] = Placeholder.component("prefix", prefix);
        System.arraycopy(resolvers, 0, combined, 1, resolvers.length);

        return combined;
    }
}
