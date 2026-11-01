package fr.darkvodou.facets;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.internal.registry.AbstractFactory;
import com.sk89q.worldedit.internal.registry.SimpleInputParser;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Registers Facets masks or patterns in a WorldEdit factory, leaving out the aliases another plugin (or FAWE)
 * already registered: theirs keep working as before.
 */
public final class Parsers {

    private Parsers() {
    }

    /**
     * @param kind "masks" or "patterns", for the log
     */
    public static <E> void register(AbstractFactory<E> factory, List<SimpleInputParser<E>> parsers, String kind,
            Logger logger) {
        List<String> registered = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (SimpleInputParser<E> parser : parsers) {
            List<String> free = new ArrayList<>();
            for (String alias : parser.getMatchedAliases()) {
                (factory.containsAlias(alias) ? skipped : free).add(alias);
            }
            if (free.isEmpty()) {
                continue;
            }
            factory.register(free.size() == parser.getMatchedAliases().size() ? parser : new Restricted<>(parser, free));
            registered.addAll(free);
        }
        logger.info("Registered " + kind + ": " + (registered.isEmpty() ? "none" : String.join(", ", registered)) + ".");
        if (!skipped.isEmpty()) {
            logger.info("Already registered by another plugin, not replaced: " + String.join(", ", skipped) + ".");
        }
    }

    /**
     * A parser answering only some of its aliases.
     */
    private static final class Restricted<E> extends SimpleInputParser<E> {

        private final SimpleInputParser<E> parser;
        private final List<String> aliases;

        Restricted(SimpleInputParser<E> parser, List<String> aliases) {
            super(WorldEdit.getInstance());
            this.parser = parser;
            this.aliases = List.copyOf(aliases);
        }

        @Override
        public List<String> getMatchedAliases() {
            return aliases;
        }

        @Override
        public E parseFromSimpleInput(String input, ParserContext context) throws InputParseException {
            return parser.parseFromSimpleInput(input, context);
        }
    }
}
