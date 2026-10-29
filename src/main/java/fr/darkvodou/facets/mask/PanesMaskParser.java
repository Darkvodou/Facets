package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;
import com.sk89q.worldedit.world.block.BlockType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code #panes} mask: every glass pane (glass_pane and all *_stained_glass_pane).
 */
public final class PanesMaskParser extends SimpleInputParser<Mask> {

    private static final List<String> ALIASES = List.of("#panes");

    private Set<BlockType> panes;

    public PanesMaskParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public List<String> getMatchedAliases() {
        return ALIASES;
    }

    @Override
    public Mask parseFromSimpleInput(String input, ParserContext context) {
        return new BlockTypeMask(context.requireExtent(), getPanes());
    }

    private Set<BlockType> getPanes() {
        // Computed on first use, once the block registry is fully populated.
        if (panes == null) {
            panes = BlockType.REGISTRY.values().stream()
                    .filter(type -> isPane(type.id()))
                    .collect(Collectors.toUnmodifiableSet());
        }
        return panes;
    }

    private static boolean isPane(String id) {
        return id.endsWith("_pane");
    }
}
