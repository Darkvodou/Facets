package fr.darkvodou.facets.symmetry;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.function.Consumer;

/**
 * Symmetry settings, read from the "symmetry" section of config.yml.
 *
 * @param maxRange    distance from the point (along each axis) up to which blocks are copied
 * @param defaultType type of //sym set when none is given and the player had none
 * @param worldEdit   copies the edits of FAWE (commands, brushes...)
 * @param vanilla     copies the blocks placed and broken by hand
 * @param marker      shows the symmetry point with particles
 * @param confirmSeconds time to //confirm an edit going beyond the player's range
 */
public record SymmetryConfig(int maxRange, SymmetryType defaultType, boolean worldEdit, boolean vanilla, boolean marker,
                             int markerTicks, int confirmSeconds) {

    /**
     * @param warn receives a message for each invalid value, replaced by its default
     */
    public static SymmetryConfig load(FileConfiguration config, Consumer<String> warn) {
        ConfigurationSection section = config.getConfigurationSection("symmetry");
        if (section == null) {
            section = config.createSection("symmetry");
        }
        int maxRange = section.getInt("max-range", 128);
        if (maxRange < 1) {
            warn.accept("symmetry.max-range must be at least 1, using 128");
            maxRange = 128;
        }
        String typeName = section.getString("default-type", "cross2d");
        SymmetryType type = SymmetryType.fromId(typeName);
        if (type == null) {
            warn.accept("symmetry.default-type: unknown type '" + typeName + "', using cross2d");
            type = SymmetryType.CROSS2D;
        }
        return new SymmetryConfig(
                maxRange,
                type,
                section.getBoolean("worldedit", true),
                section.getBoolean("vanilla", true),
                section.getBoolean("marker.enabled", true),
                Math.max(1, section.getInt("marker.refresh-ticks", 10)),
                Math.max(1, section.getInt("confirm-seconds", 20)));
    }
}
