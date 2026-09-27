package dev.kwlew.haven.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/** Bridges our private Adventure types to Bukkit without depending on the server's Adventure version. */
final class ChatComponents {

    private ChatComponents() {}

    static BaseComponent[] toBukkit(Component component) {
        return ComponentSerializer.parse(GsonComponentSerializer.gson().serialize(component));
    }
}
