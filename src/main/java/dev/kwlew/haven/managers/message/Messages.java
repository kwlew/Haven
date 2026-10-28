package dev.kwlew.haven.managers.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Text from {@code messages.yml}, parsed with MiniMessage; the bundled copy is the defaults, so newly added keys still
 * resolve.
 */
public class Messages {

    private static final String FILE_NAME = "messages.yml";

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Parsed no-resolver templates by raw text; cleared on reload.
    private final Map<String, Component> parsed = new ConcurrentHashMap<>();

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
        this.parsed.clear();

        validate(loaded);
    }

    private void validate(YamlConfiguration loaded) {
        int broken = 0;

        for (String key : loaded.getKeys(true)) {
            if (key.equals("config-version")) {
                continue;
            }

            // A list (menu lore) is checked line by line.
            List<String> candidates = loaded.isList(key)
                    ? loaded.getStringList(key)
                    : Collections.singletonList(loaded.getString(key));

            for (String raw : candidates) {
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

    /** Null when the message is blank, which is how an admin silences it. */
    public Component render(String key, TagResolver... resolvers) {
        String raw = messages.getString(key);

        if (raw == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return Component.text("<missing message: " + key + ">");
        }

        if (raw.isEmpty()) {
            return null;
        }

        return renderRaw(key, raw, resolvers);
    }

    private Component renderRaw(String key, String raw, TagResolver... resolvers) {
        try {
            if (resolvers.length == 0) {
                Component cached = parsed.get(raw);

                if (cached == null) {
                    cached = miniMessage.deserialize(raw, withPrefix(resolvers));
                    parsed.put(raw, cached);
                }

                return cached;
            }

            return miniMessage.deserialize(raw, withPrefix(resolvers));
        } catch (RuntimeException e) {
            // An admin's typo in messages.yml must not propagate out of a command handler.
            plugin.getLogger().log(Level.WARNING, "Could not render message '" + key + "'", e);
            return Component.text(raw);
        }
    }

    /** For inventory titles; empty when blanked out. */
    public String legacy(String key, TagResolver... resolvers) {
        Component rendered = render(key, resolvers);

        return rendered == null ? "" : ChatComponents.toLegacy(rendered);
    }

    /** For item names; italics suppressed. */
    public String legacyItem(String key, TagResolver... resolvers) {
        Component rendered = render(key, resolvers);

        return rendered == null ? "" : ChatComponents.toLegacyItemText(rendered);
    }

    /** Blank lines are kept, since admins use them to space out lore. */
    public List<String> legacyItemLines(String key, TagResolver... resolvers) {
        List<String> lines = new ArrayList<>();

        for (String raw : rawList(key)) {
            lines.add(raw.isEmpty()
                    ? ""
                    : ChatComponents.toLegacyItemText(renderRaw(key, raw, resolvers)));
        }

        return lines;
    }

    /** Empty when absent. */
    public List<String> rawList(String path) {
        return messages.getStringList(path);
    }

    /** Null when absent or not a string; for data lookups such as {@code world-names}. */
    public String raw(String path) {
        return messages.getString(path);
    }

    public void send(CommandSender audience, String key, TagResolver... resolvers) {
        Component message = render(key, resolvers);

        if (message != null) {
            audience.spigot().sendMessage(ChatComponents.toBukkit(message));
        }
    }

    private TagResolver[] withPrefix(TagResolver[] resolvers) {
        TagResolver[] combined = new TagResolver[resolvers.length + 1];
        combined[0] = Placeholder.component("prefix", prefix);
        System.arraycopy(resolvers, 0, combined, 1, resolvers.length);

        return combined;
    }
}
