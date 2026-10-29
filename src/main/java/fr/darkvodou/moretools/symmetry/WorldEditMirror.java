package fr.darkvodou.moretools.symmetry;

import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies {@link BlockStateMirror} to WorldEdit block states. Results are cached, a state always turning the same way.
 */
final class WorldEditMirror {

    private static final Map<Long, BlockState> CACHE = new ConcurrentHashMap<>();

    private WorldEditMirror() {
    }

    /**
     * @return the block turned by the transform, with its NBT data (sign text, banner patterns...)
     */
    static BlockStateHolder<?> transform(BlockStateHolder<?> block, Transform transform) {
        BlockState state = transform(block.toImmutableState(), transform);
        if (block instanceof BaseBlock base && base.hasNbtData()) {
            return state.toBaseBlock(base.getNbtReference());
        }
        return state;
    }

    static BlockState transform(BlockState state, Transform transform) {
        if (transform.isIdentity() || state.getStates().isEmpty()) {
            return state;
        }
        long key = ((long) state.getOrdinal() << 6) | transform.code();
        return CACHE.computeIfAbsent(key, k -> compute(state, transform));
    }

    @SuppressWarnings("unchecked")
    private static BlockState compute(BlockState state, Transform transform) {
        Map<String, Property<?>> properties = new HashMap<>();
        Map<String, String> values = new HashMap<>();
        Map<String, List<String>> allowed = new HashMap<>();
        for (Map.Entry<Property<?>, Object> entry : state.getStates().entrySet()) {
            Property<?> property = entry.getKey();
            properties.put(property.getName(), property);
            values.put(property.getName(), text(entry.getValue()));
            List<String> names = new ArrayList<>();
            for (Object value : property.getValues()) {
                names.add(text(value));
            }
            allowed.put(property.getName(), names);
        }

        Map<String, String> transformed = BlockStateMirror.transform(values, allowed, transform);
        BlockState result = state;
        for (Map.Entry<String, String> entry : transformed.entrySet()) {
            if (entry.getValue().equals(values.get(entry.getKey()))) {
                continue;
            }
            Property<Object> property = (Property<Object>) properties.get(entry.getKey());
            for (Object value : property.getValues()) {
                if (text(value).equals(entry.getValue())) {
                    result = result.with(property, value);
                    break;
                }
            }
        }
        return result;
    }

    // Values as written in Minecraft: directions are an enum in WorldEdit
    private static String text(Object value) {
        return value instanceof Direction direction ? direction.name().toLowerCase(Locale.ROOT)
                : String.valueOf(value).toLowerCase(Locale.ROOT);
    }
}
