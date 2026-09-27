package dev.kwlew.haven.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ChatComponentsTest {

    @Test
    void preservesGradientsHoverAndClickableCommands() {
        Component rich = MiniMessage.miniMessage().deserialize(
                "<gradient:#7aa2f7:#bb9af7><bold>Haven</bold></gradient> "
                        + "<hover:show_text:'Visit home'><click:run_command:'/home base'>base</click></hover>");
        var converted = ChatComponents.toBukkit(rich);
        assertEquals("Haven base", TextComponent.toPlainText(converted));
        String json = ComponentSerializer.toString(converted);
        assertEquals(colors(GsonComponentSerializer.gson().serialize(rich)), colors(json));
        assertTrue(colors(json).stream().distinct().count() > 1, "Gradient colors must survive conversion");
        assertTrue(json.contains("run_command"));
        assertTrue(json.contains("/home base"));
        assertTrue(json.contains("show_text"));
        assertTrue(json.contains("Visit home"));
    }

    @Test
    void preservesUpdateLinksAndDoesNotInterpretUnparsedValues() {
        String url = "https://modrinth.com/plugin/khaven";
        Component rich = MiniMessage.miniMessage().deserialize("<latest> <link>",
                Placeholder.unparsed("latest", "<red>1.1.0"),
                Placeholder.component("link", Component.text(url).clickEvent(ClickEvent.openUrl(url))));
        var converted = ChatComponents.toBukkit(rich);
        assertEquals("<red>1.1.0 " + url, TextComponent.toPlainText(converted));
        assertTrue(ComponentSerializer.toString(converted).contains("open_url"));
    }

    @Test
    void convertsEveryBundledMessageUsingTheOldestServerChatApi() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/messages.yml"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var messages = YamlConfiguration.loadConfiguration(reader);
            for (String key : messages.getKeys(true)) {
                if (!messages.isString(key)) continue;
                assertDoesNotThrow(() -> ChatComponents.toBukkit(
                        MiniMessage.miniMessage().deserialize(messages.getString(key))), key);
            }
        }
    }

    private static List<String> colors(String json) {
        return Pattern.compile("#[0-9a-fA-F]{6}").matcher(json).results()
                .map(match -> match.group().toLowerCase(Locale.ROOT)).toList();
    }
}
