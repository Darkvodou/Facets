package fr.darkvodou.facets.aim;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.TypeApplyingPattern;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;
import com.sk89q.worldedit.world.block.BaseBlock;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * {@code #faim} pattern: the block the player is looking at, with its exact state (facing, half...).
 * {@code #faimtype} pattern: the type the player is looking at, keeping the state of each replaced block when it
 * has the same properties (like FAWE's {@code ^type}).
 */
public final class AimPatternParser extends SimpleInputParser<Pattern> {

    private static final String AIM_TYPE = "#faimtype";
    private static final List<String> ALIASES = List.of("#faim", AIM_TYPE);

    private final IntSupplier maxRange;

    public AimPatternParser(WorldEdit worldEdit, IntSupplier maxRange) {
        super(worldEdit);
        this.maxRange = maxRange;
    }

    @Override
    public List<String> getMatchedAliases() {
        return ALIASES;
    }

    @Override
    public Pattern parseFromSimpleInput(String input, ParserContext context) throws InputParseException {
        BaseBlock block = AimedBlock.find(context, maxRange);
        if (input.equals(AIM_TYPE)) {
            return new TypeApplyingPattern(context.requireExtent(), block.getBlockType().getDefaultState());
        }
        return block;
    }
}
