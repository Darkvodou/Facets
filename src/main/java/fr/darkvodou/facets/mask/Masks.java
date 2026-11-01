package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Registers every Facets mask in WorldEdit.
 */
public final class Masks {

    private Masks() {
    }

    public static void register(WorldEdit worldEdit, Logger logger) {
        List<SimpleInputParser<Mask>> parsers = List.of(
                new PanesMaskParser(worldEdit),
                new OcclusionMaskParser(worldEdit));
        List<String> aliases = new ArrayList<>();
        for (SimpleInputParser<Mask> parser : parsers) {
            worldEdit.getMaskFactory().register(parser);
            aliases.addAll(parser.getMatchedAliases());
        }
        logger.info("Registered masks: " + String.join(", ", aliases) + ".");
    }
}
