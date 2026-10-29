package fr.darkvodou.moretools.loft;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Frames and helpers shared by the loft tests.
 */
final class LoftTesting {

    private LoftTesting() {
    }

    /**
     * @param xyz coordinates of the points, three by three
     */
    static List<BlockVector3> frame(int... xyz) {
        List<BlockVector3> points = new ArrayList<>();
        for (int i = 0; i + 2 < xyz.length; i += 3) {
            points.add(BlockVector3.at(xyz[i], xyz[i + 1], xyz[i + 2]));
        }
        return points;
    }

    /**
     * Two straight frames and a curved one in between, over a 20x20 square at y = 64.
     */
    static List<List<BlockVector3>> curvedSheet() {
        return List.of(
                frame(0, 64, 0, 20, 64, 0),
                frame(0, 64, 10, 10, 70, 10, 20, 64, 10),
                frame(0, 64, 20, 20, 64, 20));
    }

    /**
     * A straight frame, then a frame with corners: their bands are twisted unless cut into triangles.
     */
    static List<List<BlockVector3>> lineThenCorners() {
        return List.of(
                frame(0, 64, 0, 20, 64, 0),
                frame(0, 74, 15, 5, 82, 12, 12, 73, 20, 20, 80, 14));
    }

    static List<List<BlockVector3>> randomFrames(Random random) {
        List<List<BlockVector3>> frames = new ArrayList<>();
        int frameCount = 1 + random.nextInt(5);
        for (int f = 0; f < frameCount; f++) {
            List<BlockVector3> frame = new ArrayList<>();
            int pointCount = 1 + random.nextInt(4);
            for (int p = 0; p < pointCount; p++) {
                frame.add(BlockVector3.at(random.nextInt(30), random.nextInt(30), random.nextInt(30)));
            }
            frames.add(frame);
        }
        return frames;
    }

    /**
     * Number of (x, z) columns of the square [0, size]² that hold no block.
     */
    static int missingColumns(Set<BlockVector3> blocks, int size) {
        boolean[][] covered = new boolean[size + 1][size + 1];
        for (BlockVector3 block : blocks) {
            if (block.x() >= 0 && block.x() <= size && block.z() >= 0 && block.z() <= size) {
                covered[block.x()][block.z()] = true;
            }
        }
        int missing = 0;
        for (boolean[] row : covered) {
            for (boolean cell : row) {
                if (!cell) {
                    missing++;
                }
            }
        }
        return missing;
    }
}
