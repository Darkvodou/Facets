package fr.darkvodou.facets.hotbar;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;

import java.util.List;

/**
 * {@code #fhotbar} mask: every block of a type in the player's hotbar, whatever its state.
 */
public final class HotbarMaskParser extends SimpleInputParser<Mask> {

    private static final List<String> ALIASES = List.of("#fhotbar");

    public HotbarMaskParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public List<String> getMatchedAliases() {
        return ALIASES;
    }

    @Override
    public Mask parseFromSimpleInput(String input, ParserContext context) throws InputParseException {
        return new BlockTypeMask(context.requireExtent(), HotbarBlocks.find(context));
    }
}
