package fr.darkvodou.moretools.menu;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Each player's favorite global masks, saved in favorites.yml, and the text of their current global mask.
 * WorldEdit keeps the mask, not what was typed, so the text is taken from the //gmask commands.
 */
public final class MaskFavorites {

    public static final int MAX = 18;

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, List<String>> favorites = new ConcurrentHashMap<>();
    private final Map<UUID, String> current = new ConcurrentHashMap<>();

    public MaskFavorites(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "favorites.yml");
        load();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                favorites.put(UUID.fromString(key), new ArrayList<>(yaml.getStringList(key + ".masks")));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("favorites.yml: not a player UUID: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        favorites.forEach((player, masks) -> {
            if (!masks.isEmpty()) {
                yaml.set(player + ".masks", masks);
            }
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save favorites.yml", e);
        }
    }

    public List<String> get(UUID player) {
        return List.copyOf(favorites.getOrDefault(player, List.of()));
    }

    /**
     * @return false if the mask was already a favorite or there are too many
     */
    public boolean add(UUID player, String mask) {
        List<String> masks = favorites.computeIfAbsent(player, k -> new ArrayList<>());
        if (masks.contains(mask) || masks.size() >= MAX) {
            return false;
        }
        masks.add(mask);
        save();
        return true;
    }

    public void remove(UUID player, String mask) {
        List<String> masks = favorites.get(player);
        if (masks != null && masks.remove(mask)) {
            save();
        }
    }

    /**
     * @return what the player typed for their current global mask, or null if unknown
     */
    @Nullable
    public String current(UUID player) {
        return current.get(player);
    }

    /**
     * @param mask the text of the mask, null or empty when it is removed
     */
    void setCurrent(UUID player, @Nullable String mask) {
        if (mask == null || mask.isBlank()) {
            current.remove(player);
        } else {
            current.put(player, mask.trim());
        }
    }
}
