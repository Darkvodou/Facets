package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;
import com.sk89q.worldedit.world.block.BlockType;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A mask taking every block of a fixed set of types, whatever their state: {@code #panes}, {@code #glass}.
 */
public final class BlockSetMaskParser extends SimpleInputParser<Mask> {

    private final List<String> aliases;
    private final Supplier<Set<BlockType>> types;

    public BlockSetMaskParser(WorldEdit worldEdit, String alias, Supplier<Set<BlockType>> types) {
        super(worldEdit);
        this.aliases = List.of(alias);
        this.types = types;
    }

    @Override
    public List<String> getMatchedAliases() {
        return aliases;
    }

    @Override
    public Mask parseFromSimpleInput(String input, ParserContext context) {
        return new BlockTypeMask(context.requireExtent(), types.get());
    }
}
