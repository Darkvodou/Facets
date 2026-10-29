package fr.darkvodou.moretools;

import com.sk89q.worldedit.extension.platform.Actor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Player messages from messages.yml, written like FAWE's: legacy &amp; color codes and {name} placeholders.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private static final String[] COLORS = {"info", "error", "warning"};

    private static volatile YamlConfiguration messages = new YamlConfiguration();

    private Messages() {
    }

    /**
     * @param messages the messages, with the bundled ones as defaults
     */
    static void load(YamlConfiguration messages) {
        Messages.messages = messages;
    }

    /**
     * @param placeholders alternating names and values: "count", 3
     * @return the message, or null if it is empty
     */
    @Nullable
    public static Component get(String key, Object... placeholders) {
        return render(key, true, placeholders);
    }

    /**
     * The message without prefix and without italics, for item names and lore.
     */
    public static Component plain(String key, Object... placeholders) {
        Component message = render(key, false, placeholders);
        return message == null ? Component.empty() : noItalic(message);
    }

    /**
     * The lines of a message (separated by \n), without prefix and without italics, for item lore.
     */
    public static List<Component> lines(String key, Object... placeholders) {
        String template = messages.getString(key, key);
        if (template.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        for (String line : template.split("\n", -1)) {
            lines.add(noItalic(fill(line, false, placeholders)));
        }
        return lines;
    }

    private static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    @Nullable
    private static Component render(String key, boolean prefixed, Object... placeholders) {
        String template = messages.getString(key, key);
        if (template.isEmpty()) {
            return null;
        }
        return fill(template, prefixed, placeholders);
    }

    private static Component fill(String template, boolean prefixed, Object... placeholders) {
        // Colors first, so that values (an error text...) are never taken for a color
        String text = colors((prefixed ? messages.getString("prefix", "") : "") + template);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return LEGACY.deserialize(text);
    }

    private static String colors(String text) {
        for (String color : COLORS) {
            text = text.replace("{" + color + "}", messages.getString("colors." + color, ""));
        }
        return text;
    }

    public static void send(CommandSender sender, String key, Object... placeholders) {
        Component message = get(key, placeholders);
        if (message != null) {
            sender.sendMessage(message);
        }
    }

    /**
     * Sends a message to a WorldEdit actor, through Bukkit when it is a player so hex colors work.
     */
    public static void send(Actor actor, String key, Object... placeholders) {
        Component message = get(key, placeholders);
        if (message == null) {
            return;
        }
        Player player = actor.isPlayer() ? Bukkit.getPlayer(actor.getUniqueId()) : null;
        if (player != null) {
            player.sendMessage(message);
        } else {
            actor.print(worldEdit(message));
        }
    }

    /**
     * The message as a WorldEdit component, without prefix, for what WorldEdit prints itself
     * (//size lines, errors, //sel list).
     */
    public static com.sk89q.worldedit.util.formatting.text.Component worldEdit(String key, Object... placeholders) {
        Component message = render(key, false, placeholders);
        return message == null ? com.sk89q.worldedit.util.formatting.text.TextComponent.empty() : worldEdit(message);
    }

    private static com.sk89q.worldedit.util.formatting.text.Component worldEdit(Component message) {
        // WorldEdit's text library cannot take hex colors: they become the closest named color
        String legacy = LegacyComponentSerializer.legacyAmpersand().serialize(message);
        return com.sk89q.worldedit.util.formatting.text.serializer.legacy.LegacyComponentSerializer.legacy()
                .deserialize(legacy, '&');
    }
}
