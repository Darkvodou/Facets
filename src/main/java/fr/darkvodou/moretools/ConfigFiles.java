package fr.darkvodou.moretools;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Keeps the configuration files of the data folder up to date with the bundled ones.
 */
final class ConfigFiles {

    private ConfigFiles() {
    }

    /**
     * Creates the file from the bundled one if it is missing, else adds the entries it lacks
     * (with their comments) and saves it. Values already in the file are never changed.
     *
     * @return the bundled configuration, to be used as defaults
     */
    static YamlConfiguration update(JavaPlugin plugin, String name) {
        YamlConfiguration bundled = bundled(plugin, name);
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
            return bundled;
        }

        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        List<String> added = merge(current, bundled);
        if (added.isEmpty()) {
            return bundled;
        }
        try {
            current.save(file);
            plugin.getLogger().info(name + ": added " + String.join(", ", added));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not add the new entries to " + name, e);
        }
        return bundled;
    }

    /**
     * Adds to {@code current} the entries of {@code bundled} it lacks, with their comments.
     *
     * @return the paths of the added values
     */
    static List<String> merge(YamlConfiguration current, YamlConfiguration bundled) {
        List<String> added = new ArrayList<>();
        // Parents come before their children, so new sections get created in order
        for (String path : bundled.getKeys(true)) {
            if (current.contains(path, true)) {
                continue;
            }
            if (bundled.isConfigurationSection(path)) {
                current.createSection(path);
            } else {
                current.set(path, bundled.get(path));
                added.add(path);
            }
            current.setComments(path, bundled.getComments(path));
            current.setInlineComments(path, bundled.getInlineComments(path));
        }
        return added;
    }

    private static YamlConfiguration bundled(JavaPlugin plugin, String name) {
        InputStream stream = plugin.getResource(name);
        if (stream == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
