package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.block.BlockType;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every glass, for the {@code #glass} mask and pattern: the glass of Minecraft's {@code #impermeable} tag
 * (glass, the stained glasses, tinted glass) and every glass pane ({@code #panes}), without the other blocks of the
 * tag.
 */
public final class Glass {

    private static volatile Set<BlockType> types;

    private Glass() {
    }

    public static Set<BlockType> types() {
        // Computed on first use, once the tags are loaded
        if (types == null) {
            Stream<BlockType> blocks = Tag.IMPERMEABLE.getValues().stream()
                    .filter(Glass::isGlass)
                    .map(BukkitAdapter::asBlockType)
                    .filter(Objects::nonNull);
            types = Stream.concat(blocks, Panes.types().stream()).collect(Collectors.toUnmodifiableSet());
        }
        return types;
    }

    static boolean isGlass(Material material) {
        return isGlass(material.getKey().getKey());
    }

    /**
     * @param id a block id of the tag, without namespace
     */
    static boolean isGlass(String id) {
        return id.contains("glass");
    }
}
