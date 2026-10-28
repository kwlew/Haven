package dev.kwlew.haven.managers.home;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class HomeDisplay {

    private static final DateTimeFormatter CREATED_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

    private HomeDisplay() {}

    /** Unparsed, so a world name containing {@code <} can't inject tags. */
    public static TagResolver[] placeholders(Home home, String worldDisplay) {
        return new TagResolver[]{
                Placeholder.unparsed("name", home.name()),
                Placeholder.unparsed("world", worldDisplay),
                Placeholder.unparsed("x", Integer.toString((int) Math.floor(home.x()))),
                Placeholder.unparsed("y", Integer.toString((int) Math.floor(home.y()))),
                Placeholder.unparsed("z", Integer.toString((int) Math.floor(home.z()))),
                Placeholder.unparsed("created", created(home))
        };
    }

    static String created(Home home) {
        return home.created() <= 0
                ? "unknown"
                : CREATED_FORMAT.format(Instant.ofEpochMilli(home.created()));
    }
}
