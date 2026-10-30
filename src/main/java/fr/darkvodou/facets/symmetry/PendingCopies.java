package fr.darkvodou.facets.symmetry;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The copies kept before writing them, by chunk section like FAWE's own chunks: two bytes per block.
 * The blocks with NBT (signs, banners...) are kept apart.
 */
final class PendingCopies {

    private static final int SECTION = 16 * 16 * 16;
    // Marks a block kept apart, with its NBT
    private static final char NBT = Character.MAX_VALUE;

    interface Writer {
        void write(int x, int y, int z, BlockStateHolder<?> block) throws WorldEditException;
    }

    interface Position {
        boolean test(int x, int y, int z);
    }

    private final int minY;
    private final int sections;
    // Block ordinal + 1 by chunk, then by section and position in the section; 0 when there is no copy
    private final Map<Long, char[][]> chunks = new HashMap<>();
    private final Map<BlockVector3, BaseBlock> withNbt = new HashMap<>();
    private int count;
    private int allocated;

    PendingCopies(int minY, int maxY) {
        this.minY = minY;
        this.sections = ((maxY - minY) >> 4) + 1;
    }

    boolean isEmpty() {
        return count == 0;
    }

    /**
     * @return about the memory used, in bytes
     */
    long bytes() {
        return allocated * (long) SECTION * Character.BYTES + withNbt.size() * 256L;
    }

    /**
     * Keeps the copy, in place of the one kept at the same position.
     */
    void put(int x, int y, int z, BlockStateHolder<?> block) {
        char[] section = section(x, y, z, true);
        int index = index(x, y, z);
        if (section[index] == 0) {
            count++;
        } else if (section[index] == NBT) {
            withNbt.remove(BlockVector3.at(x, y, z));
        }
        if (block instanceof BaseBlock base && base.getNbtReference() != null) {
            section[index] = NBT;
            withNbt.put(BlockVector3.at(x, y, z), base);
        } else {
            section[index] = (char) (block.getOrdinal() + 1);
        }
    }

    void remove(int x, int y, int z) {
        char[] section = section(x, y, z, false);
        if (section == null) {
            return;
        }
        int index = index(x, y, z);
        if (section[index] == NBT) {
            withNbt.remove(BlockVector3.at(x, y, z));
        }
        if (section[index] != 0) {
            section[index] = 0;
            count--;
        }
    }

    /**
     * Writes the copies chunk after chunk, and forgets them.
     *
     * @param keep the positions to keep for later, or null to write everything
     */
    void write(Writer writer, @Nullable Position keep) throws WorldEditException {
        for (Iterator<Map.Entry<Long, char[][]>> chunks = this.chunks.entrySet().iterator(); chunks.hasNext(); ) {
            Map.Entry<Long, char[][]> chunk = chunks.next();
            int chunkX = (int) (chunk.getKey() >> 32);
            int chunkZ = (int) (long) chunk.getKey();
            char[][] sections = chunk.getValue();
            boolean chunkKept = false;
            for (int s = 0; s < sections.length; s++) {
                char[] section = sections[s];
                if (section == null) {
                    continue;
                }
                boolean sectionKept = false;
                for (int i = 0; i < SECTION; i++) {
                    char ordinal = section[i];
                    if (ordinal == 0) {
                        continue;
                    }
                    int x = (chunkX << 4) | (i & 15);
                    int y = minY + (s << 4) + (i >> 8);
                    int z = (chunkZ << 4) | ((i >> 4) & 15);
                    if (keep != null && keep.test(x, y, z)) {
                        sectionKept = true;
                        continue;
                    }
                    BlockStateHolder<?> block = ordinal == NBT ? withNbt.remove(BlockVector3.at(x, y, z))
                            : BlockState.getFromOrdinal(ordinal - 1);
                    writer.write(x, y, z, block);
                    section[i] = 0;
                    count--;
                }
                if (sectionKept) {
                    chunkKept = true;
                } else {
                    sections[s] = null;
                    allocated--;
                }
            }
            if (!chunkKept) {
                chunks.remove();
            }
        }
    }

    @Nullable
    private char[] section(int x, int y, int z, boolean create) {
        long key = ((long) (x >> 4) << 32) | ((z >> 4) & 0xffffffffL);
        char[][] chunk = chunks.get(key);
        if (chunk == null) {
            if (!create) {
                return null;
            }
            chunk = new char[sections][];
            chunks.put(key, chunk);
        }
        int s = (y - minY) >> 4;
        if (chunk[s] == null && create) {
            chunk[s] = new char[SECTION];
            allocated++;
        }
        return chunk[s];
    }

    private int index(int x, int y, int z) {
        return (((y - minY) & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }
}
