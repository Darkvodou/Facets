package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;
import fr.darkvodou.facets.Parsers;
import fr.darkvodou.facets.aim.AimMaskParser;
import fr.darkvodou.facets.hotbar.HotbarMaskParser;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.logging.Logger;

/**
 * Registers every Facets mask in WorldEdit, except those another plugin already has.
 */
public final class Masks {

    private Masks() {
    }

    /**
     * @param aimRange the farthest block {@code #faim} can take (blocks)
     */
    public static void register(WorldEdit worldEdit, Logger logger, IntSupplier aimRange) {
        List<SimpleInputParser<Mask>> parsers = List.of(
                new BlockSetMaskParser(worldEdit, "#panes", Panes::types),
                new BlockSetMaskParser(worldEdit, "#glass", Glass::types),
                new HotbarMaskParser(worldEdit),
                new OcclusionMaskParser(worldEdit),
                new AimMaskParser(worldEdit, aimRange),
                new CurrentMaskParser(worldEdit));
        Parsers.register(worldEdit.getMaskFactory(), parsers, "masks", logger);
    }
}
