package dev.kwlew.haven.sound;

import dev.kwlew.haven.config.HavenConfig;
import net.kyori.adventure.key.Key;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Plays Haven's configurable sounds.
 * <p>
 * Sounds are parsed once on load rather than per playback, so a malformed key is reported at
 * startup or on {@code /haven reload} instead of silently every time a player triggers it. That
 * mirrors how {@link dev.kwlew.haven.message.Messages} handles its file, and is why
 * {@link #reload()} exists - the parsed map is the cache, and reload is what refreshes it.
 * <p>
 * Any namespaced key works, including sounds supplied by a server resource pack. Leaving a
 * {@code key} blank switches that one sound off.
 */
public class Sounds {

    private final JavaPlugin plugin;
    private final HavenConfig config;

    private final Map<HavenSound, ConfiguredSound> parsed = new EnumMap<>(HavenSound.class);
    private boolean enabled = true;

    public Sounds(JavaPlugin plugin, HavenConfig config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        parsed.clear();
        enabled = config.soundsEnabled();

        for (HavenSound sound : HavenSound.values()) {
            ConfiguredSound resolved = parse(sound);

            if (resolved != null) {
                parsed.put(sound, resolved);
            }
        }
    }

    private ConfiguredSound parse(HavenSound sound) {
        String id = sound.path();
        String key = config.soundKey(id);

        if (key.isBlank()) {
            return null;
        }

        try {
            return new ConfiguredSound(
                    Key.key(key).asString(),
                    source(config.soundSource(id)),
                    (float) config.soundVolume(id),
                    (float) config.soundPitch(id)
            );
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Invalid sound '" + sound.path() + "' (key: " + key + "): "
                    + e.getMessage() + " - that sound is disabled.");
            return null;
        }
    }

    private SoundCategory source(String raw) {
        try {
            // Adventure's source names are singular; Bukkit's corresponding categories are plural.
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "record" -> SoundCategory.RECORDS;
                case "block" -> SoundCategory.BLOCKS;
                case "player" -> SoundCategory.PLAYERS;
                default -> SoundCategory.valueOf(raw.toUpperCase(Locale.ROOT));
            };
        } catch (IllegalArgumentException e) {
            return SoundCategory.MASTER;
        }
    }

    /**
     * Plays a sound to one player. Silently does nothing when sounds are off.
     */
    public void play(Player player, HavenSound sound) {
        if (!enabled) {
            return;
        }

        ConfiguredSound resolved = parsed.get(sound);

        if (resolved != null) {
            player.playSound(player.getLocation(), resolved.key(), resolved.category(),
                    resolved.volume(), resolved.pitch());
        }
    }

    private record ConfiguredSound(String key, SoundCategory category, float volume, float pitch) {}
}
