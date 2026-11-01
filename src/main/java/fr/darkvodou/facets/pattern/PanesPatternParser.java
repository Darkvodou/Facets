package fr.darkvodou.facets.pattern;

import com.fastasyncworldedit.core.math.random.TrueRandom;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.function.pattern.TypeApplyingPattern;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;
import com.sk89q.worldedit.world.block.BlockType;
import fr.darkvodou.facets.mask.Panes;

import java.util.List;

/**
 * {@code #panes} pattern: a random glass pane for each block. A replaced pane keeps its connections.
 */
public final class PanesPatternParser extends SimpleInputParser<Pattern> {

    private static final List<String> ALIASES = List.of("#panes");

    public PanesPatternParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public List<String> getMatchedAliases() {
        return ALIASES;
    }

    @Override
    public Pattern parseFromSimpleInput(String input, ParserContext context) {
        Extent extent = context.requireExtent();
        RandomPattern random = new RandomPattern(new TrueRandom());
        for (BlockType pane : Panes.types()) {
            random.add(new TypeApplyingPattern(extent, pane.getDefaultState()), 1);
        }
        return random;
    }
}
