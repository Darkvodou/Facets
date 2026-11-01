package fr.darkvodou.facets.aim;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;
import com.sk89q.worldedit.world.block.BaseBlock;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * {@code #faim} mask: the block the player is looking at, with its exact state (facing, half...).
 * {@code #faimtype} mask: every block of the type the player is looking at, whatever its state.
 */
public final class AimMaskParser extends SimpleInputParser<Mask> {

    private static final String AIM_TYPE = "#faimtype";
    private static final List<String> ALIASES = List.of("#faim", AIM_TYPE);

    private final IntSupplier maxRange;

    public AimMaskParser(WorldEdit worldEdit, IntSupplier maxRange) {
        super(worldEdit);
        this.maxRange = maxRange;
    }

    @Override
    public List<String> getMatchedAliases() {
        return ALIASES;
    }

    @Override
    public Mask parseFromSimpleInput(String input, ParserContext context) throws InputParseException {
        BaseBlock block = AimedBlock.find(context, maxRange);
        if (input.equals(AIM_TYPE)) {
            return new BlockTypeMask(context.requireExtent(), block.getBlockType());
        }
        return new BlockMask(context.requireExtent(), block);
    }
}
