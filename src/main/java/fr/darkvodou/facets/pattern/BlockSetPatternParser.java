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

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A pattern setting a random type of a fixed set for each block: {@code #panes}, {@code #glass}. A replaced block
 * keeps the state it shares with the new type (a pane keeps its connections).
 */
public final class BlockSetPatternParser extends SimpleInputParser<Pattern> {

    private final List<String> aliases;
    private final Supplier<Set<BlockType>> types;

    public BlockSetPatternParser(WorldEdit worldEdit, String alias, Supplier<Set<BlockType>> types) {
        super(worldEdit);
        this.aliases = List.of(alias);
        this.types = types;
    }

    @Override
    public List<String> getMatchedAliases() {
        return aliases;
    }

    @Override
    public Pattern parseFromSimpleInput(String input, ParserContext context) {
        Extent extent = context.requireExtent();
        RandomPattern random = new RandomPattern(new TrueRandom());
        for (BlockType type : types.get()) {
            random.add(new TypeApplyingPattern(extent, type.getDefaultState()), 1);
        }
        return random;
    }
}
