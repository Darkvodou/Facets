package fr.darkvodou.facets.pattern;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;
import fr.darkvodou.facets.Parsers;
import fr.darkvodou.facets.aim.AimPatternParser;
import fr.darkvodou.facets.hotbar.HotbarPatternParser;
import fr.darkvodou.facets.mask.Glass;
import fr.darkvodou.facets.mask.Panes;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.logging.Logger;

/**
 * Registers every Facets pattern in WorldEdit, except those another plugin already has.
 */
public final class Patterns {

    private Patterns() {
    }

    /**
     * @param aimRange the farthest block {@code #faim} can take (blocks)
     */
    public static void register(WorldEdit worldEdit, Logger logger, IntSupplier aimRange) {
        List<SimpleInputParser<Pattern>> parsers = List.of(
                new AimPatternParser(worldEdit, aimRange),
                new BlockSetPatternParser(worldEdit, "#panes", Panes::types),
                new BlockSetPatternParser(worldEdit, "#glass", Glass::types),
                new HotbarPatternParser(worldEdit));
        Parsers.register(worldEdit.getPatternFactory(), parsers, "patterns", logger);
    }
}
