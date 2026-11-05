package fr.darkvodou.facets.hotbar;

import com.fastasyncworldedit.core.math.random.TrueRandom;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;
import com.sk89q.worldedit.world.block.BlockType;

import java.util.List;

/**
 * {@code #fhotbar} pattern: a random block of the player's hotbar for each block, each slot counting once
 * (a block in two slots comes twice as often).
 */
public final class HotbarPatternParser extends SimpleInputParser<Pattern> {

    private static final List<String> ALIASES = List.of("#fhotbar");

    public HotbarPatternParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public List<String> getMatchedAliases() {
        return ALIASES;
    }

    @Override
    public Pattern parseFromSimpleInput(String input, ParserContext context) throws InputParseException {
        List<BlockType> types = HotbarBlocks.find(context);
        if (types.size() == 1) {
            return types.getFirst().getDefaultState();
        }
        RandomPattern random = new RandomPattern(new TrueRandom());
        for (BlockType type : types) {
            random.add(type.getDefaultState(), 1);
        }
        return random;
    }
}
