package fr.darkvodou.facets.mask;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;
import fr.darkvodou.facets.Messages;

import java.util.List;

/**
 * {@code #gcurrent} mask: the player's current global mask (//gmask).
 * {@code #fcurrent} mask: the mask of the brush in the player's hand (//mask).
 */
public final class CurrentMaskParser extends SimpleInputParser<Mask> {

    private static final String GLOBAL = "#gcurrent";
    private static final List<String> ALIASES = List.of(GLOBAL, "#fcurrent");

    public CurrentMaskParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public List<String> getMatchedAliases() {
        return ALIASES;
    }

    @Override
    public Mask parseFromSimpleInput(String input, ParserContext context) throws InputParseException {
        if (!(context.getActor() instanceof Player player)) {
            throw new InputParseException(Messages.worldEdit("current-mask.player-only", "mask", input));
        }
        LocalSession session = worldEdit.getSessionManager().get(player);
        Mask mask = input.equals(GLOBAL) ? session.getMask() : brushMask(session, player);
        if (mask == null) {
            throw new InputParseException(Messages.worldEdit(
                    input.equals(GLOBAL) ? "current-mask.no-global" : "current-mask.no-brush"));
        }
        // A copy, so that the mask built from it does not share its state
        return mask.copy();
    }

    private static Mask brushMask(LocalSession session, Player player) {
        Tool tool = session.getTool(player);
        return tool instanceof BrushTool brush ? brush.getMask() : null;
    }
}
