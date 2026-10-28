package dev.kwlew.haven.managers.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/** Bridges our private Adventure types to Bukkit without depending on the server's Adventure version. */
final class ChatComponents {

    // Hex colours in the §x§r§r§g§g§b§b form, which every server since 1.16 understands.
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ChatComponents() {}

    static BaseComponent[] toBukkit(Component component) {
        return ComponentSerializer.parse(GsonComponentSerializer.gson().serialize(component));
    }

    /**
     * Our relocated Adventure types can't be handed to the server's Adventure, so a legacy string is the portable
     * form.
     */
    static String toLegacy(Component component) {
        return LEGACY.serialize(component);
    }

    /** Italics off unless the text asks for them; clients italicise custom names and lore by default. */
    static String toLegacyItemText(Component component) {
        // decorationIfAbsent does not exist in Adventure 4.10.
        boolean unset = component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET;

        return LEGACY.serialize(unset ? component.decoration(TextDecoration.ITALIC, false) : component);
    }
}
