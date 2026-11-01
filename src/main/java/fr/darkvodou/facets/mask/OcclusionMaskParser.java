package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;

import java.util.List;

/**
 * {@code #noocclusion} mask: every block that lets the blocks behind it be seen (stairs, fences, trapdoors, glass...).
 * {@code #occluded} mask: every full block with a face seen through a {@code #noocclusion} neighbor.
 */
public final class OcclusionMaskParser extends SimpleInputParser<Mask> {

    private static final String OCCLUDED = "#occluded";
    private static final List<String> ALIASES = List.of("#noocclusion", OCCLUDED);

    public OcclusionMaskParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public List<String> getMatchedAliases() {
        return ALIASES;
    }

    @Override
    public Mask parseFromSimpleInput(String input, ParserContext context) {
        return new OcclusionMask(context.requireExtent(), OcclusionTable.get(), input.equals(OCCLUDED));
    }
}
