package fr.darkvodou.facets.symmetry;

import fr.darkvodou.facets.Trails;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shows each player their symmetry point with particles, in a shape and a color of its own for each type.
 * The shape follows the mirrors: a square for each plane, a star for the diagonals, a cube for megastar.
 * The colors of the planes are those of the axes in Minecraft (x red, y green, z blue). A paused symmetry is gray.
 * <p>
 * The particles are {@link Trails}, which last until the next refresh: dust fades sooner and blinks.
 * Servers without them (before 1.21.4) use dust.
 */
public final class SymmetryMarker implements Runnable {

    private static final double VIEW_DISTANCE = 128;
    // Half side of the shapes, past the block of the point so that they can be seen around a player
    private static final double SIZE = 0.8;
    private static final double STEP = 0.2;
    private static final float DUST_SIZE = 0.8f;
    private static final Color PAUSED = Color.fromRGB(0x808080);

    private record Look(Color color, List<Vector> points) {
    }

    private static final Map<SymmetryType, Look> LOOKS = new EnumMap<>(SymmetryType.class);

    static {
        look(SymmetryType.SIMPLE2D, 0xff8c00, circleY(), line(0, 1, 0));
        look(SymmetryType.PLANX, 0xff3030, square('x'));
        look(SymmetryType.PLANY, 0x30ff30, square('y'));
        look(SymmetryType.PLANZ, 0x3080ff, square('z'));
        look(SymmetryType.CROSS2D, 0xff40ff, square('x'), square('z'));
        look(SymmetryType.CROSS3D, 0xffffff, square('x'), square('y'), square('z'));
        look(SymmetryType.STAR2D, 0xffe000, star());
        look(SymmetryType.STAR3D, 0x00e5ff, star(), square('y'));
        look(SymmetryType.MEGASTAR, 0x9b30ff, cube(), star());
    }

    private final SymmetrySessions sessions;

    public SymmetryMarker(SymmetrySessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void run() {
        if (!sessions.config().marker()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Symmetry symmetry = sessions.get(player.getUniqueId());
            if (symmetry == null || !symmetry.world().equals(player.getWorld().getName())) {
                continue;
            }
            Location center = new Location(player.getWorld(), symmetry.center().x() + 0.5, symmetry.center().y() + 0.5,
                    symmetry.center().z() + 0.5);
            if (center.distanceSquared(player.getLocation()) > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }
            Look look = LOOKS.get(symmetry.type());
            Color color = symmetry.enabled() ? look.color() : PAUSED;
            Particle.DustOptions dust = new Particle.DustOptions(color, DUST_SIZE);
            for (Vector point : look.points()) {
                Location location = center.clone().add(point);
                if (Trails.SUPPORTED) {
                    player.spawnParticle(Particle.TRAIL, location, 1, 0, 0, 0, 0,
                            Trails.still(location, color, sessions.config().markerTicks()), true);
                } else {
                    player.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0, dust, true);
                }
            }
        }
    }

    @SafeVarargs
    private static void look(SymmetryType type, int color, List<Vector>... shapes) {
        List<Vector> points = new ArrayList<>();
        for (List<Vector> shape : shapes) {
            points.addAll(shape);
        }
        LOOKS.put(type, new Look(Color.fromRGB(color), List.copyOf(points)));
    }

    /**
     * The outline of a square in the plane across the axis.
     */
    private static List<Vector> square(char axis) {
        List<Vector> points = new ArrayList<>();
        for (double a = -SIZE; a < SIZE - 1e-9; a += STEP) {
            // The four sides, each going around the square
            double[][] sides = {{a, -SIZE}, {SIZE, a}, {-a, SIZE}, {-SIZE, -a}};
            for (double[] side : sides) {
                points.add(switch (axis) {
                    case 'x' -> new Vector(0, side[0], side[1]);
                    case 'y' -> new Vector(side[0], 0, side[1]);
                    default -> new Vector(side[0], side[1], 0);
                });
            }
        }
        return points;
    }

    /**
     * A line through the center along the direction.
     */
    private static List<Vector> line(double x, double y, double z) {
        Vector direction = new Vector(x, y, z).normalize();
        List<Vector> points = new ArrayList<>();
        for (double d = -SIZE; d <= SIZE + 1e-9; d += STEP) {
            points.add(direction.clone().multiply(d));
        }
        return points;
    }

    /**
     * A horizontal circle around the center.
     */
    private static List<Vector> circleY() {
        List<Vector> points = new ArrayList<>();
        int count = (int) Math.round(2 * Math.PI * SIZE / STEP);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            points.add(new Vector(Math.cos(angle) * SIZE, 0, Math.sin(angle) * SIZE));
        }
        return points;
    }

    /**
     * A horizontal star of eight arms: the axes and the diagonals.
     */
    private static List<Vector> star() {
        List<Vector> points = new ArrayList<>();
        points.addAll(line(1, 0, 0));
        points.addAll(line(0, 0, 1));
        points.addAll(line(1, 0, 1));
        points.addAll(line(1, 0, -1));
        return points;
    }

    /**
     * The edges of a cube around the center.
     */
    private static List<Vector> cube() {
        List<Vector> points = new ArrayList<>();
        for (double a = -SIZE; a <= SIZE + 1e-9; a += STEP) {
            for (double u : new double[]{-SIZE, SIZE}) {
                for (double v : new double[]{-SIZE, SIZE}) {
                    points.add(new Vector(a, u, v));
                    points.add(new Vector(u, a, v));
                    points.add(new Vector(u, v, a));
                }
            }
        }
        return points;
    }
}
