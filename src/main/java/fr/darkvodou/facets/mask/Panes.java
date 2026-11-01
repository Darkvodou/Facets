package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.world.block.BlockType;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every glass pane (glass_pane and all *_stained_glass_pane), for the {@code #panes} mask and pattern.
 */
public final class Panes {

    private static volatile Set<BlockType> types;

    private Panes() {
    }

    public static Set<BlockType> types() {
        // Computed on first use, once the block registry is fully populated.
        if (types == null) {
            types = BlockType.REGISTRY.values().stream()
                    .filter(type -> type.id().endsWith("_pane"))
                    .collect(Collectors.toUnmodifiableSet());
        }
        return types;
    }
}
