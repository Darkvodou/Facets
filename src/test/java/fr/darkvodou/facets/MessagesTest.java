package fr.darkvodou.facets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessagesTest {

    private YamlConfiguration messages;

    @BeforeEach
    void load() throws InvalidConfigurationException {
        messages = new YamlConfiguration();
        messages.loadFromString("""
                prefix: ""
                colors:
                  info: "&7"
                  error: "&c"
                done: "{info}Operation completed ({count})."
                failed: "{error}An error occurred: {message}"
                muted: ""
                """);
        Messages.load(messages);
    }

    private static String render(String key, Object... placeholders) {
        Component message = Messages.get(key, placeholders);
        return message == null ? null : LegacyComponentSerializer.legacyAmpersand().serialize(message);
    }

    @Test
    void replacesColorsAndPlaceholders() {
        assertEquals("&7Operation completed (42).", render("done", "count", 42));
    }

    @Test
    void addsThePrefix() {
        messages.set("prefix", "&8(&4&lFAWE&8)&7 ");
        assertEquals("&8(&4&lFAWE&8)&7 Operation completed (42).", render("done", "count", 42));
    }

    @Test
    void valuesAreNeverTakenForColors() {
        assertEquals("&cAn error occurred: boom {info}", render("failed", "message", "boom {info}"));
    }

    @Test
    void emptyMessagesAreNotSent() {
        assertNull(Messages.get("muted"));
    }

    @Test
    void unknownKeysShowTheKey() {
        assertEquals("missing.key", render("missing.key"));
    }
}
