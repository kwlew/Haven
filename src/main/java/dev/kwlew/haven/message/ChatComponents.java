package dev.kwlew.haven.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/** Bridges our private Adventure types to Bukkit without depending on the server's Adventure version. */
final class ChatComponents {

    /**
     * Hex colours in the {@code §x§r§r§g§g§b§b} form every server since 1.16 understands, so gradients
     * survive on 1.17.1 as well as on current Paper.
     */
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
     * Flattens a component to a legacy string for APIs that only take strings - inventory titles
     * and item meta. Our relocated Adventure types cannot be handed to the server's own Adventure
     * API, so a string is the one form that works across every supported version.
     */
    static String toLegacy(Component component) {
        return LEGACY.serialize(component);
    }

    /**
     * As {@link #toLegacy}, with italics switched off unless the text asks for them. The client
     * renders custom item names and lore in italics by default, which looks like a rename-tag
     * accident rather than a menu.
     */
    static String toLegacyItemText(Component component) {
        // decorationIfAbsent does not exist in Adventure 4.10.
        boolean unset = component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET;

        return LEGACY.serialize(unset ? component.decoration(TextDecoration.ITALIC, false) : component);
    }
}
