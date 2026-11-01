package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;

import java.util.List;

/**
 * {@code #panes} mask: every glass pane (glass_pane and all *_stained_glass_pane).
 */
public final class PanesMaskParser extends SimpleInputParser<Mask> {

    private static final List<String> ALIASES = List.of("#panes");

    public PanesMaskParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public List<String> getMatchedAliases() {
        return ALIASES;
    }

    @Override
    public Mask parseFromSimpleInput(String input, ParserContext context) {
        return new BlockTypeMask(context.requireExtent(), Panes.types());
    }
}
