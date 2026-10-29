package fr.darkvodou.facets.symmetry;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;

/**
 * A symmetry of the block grid around a point: each axis goes to an axis (x, y or z), possibly reversed.
 * These are the 48 rotations and mirrors that keep a cube on itself.
 */
public final class Transform {

    public static final Transform IDENTITY = new Transform(new int[]{0, 1, 2}, new int[]{1, 1, 1});
    /** x → -x, the mirror through the plane perpendicular to x */
    public static final Transform MIRROR_X = new Transform(new int[]{0, 1, 2}, new int[]{-1, 1, 1});
    public static final Transform MIRROR_Y = new Transform(new int[]{0, 1, 2}, new int[]{1, -1, 1});
    public static final Transform MIRROR_Z = new Transform(new int[]{0, 1, 2}, new int[]{1, 1, -1});
    /** Half turn around the vertical axis */
    public static final Transform HALF_TURN_Y = new Transform(new int[]{0, 1, 2}, new int[]{-1, 1, -1});
    /** Mirror through the diagonal plane x = z (45° from the x and z planes) */
    public static final Transform SWAP_XZ = new Transform(new int[]{2, 1, 0}, new int[]{1, 1, 1});
    /** Mirror through the diagonal plane x = y */
    public static final Transform SWAP_XY = new Transform(new int[]{1, 0, 2}, new int[]{1, 1, 1});

    private static final String[] AXES = {"x", "y", "z"};

    // Output axis i takes sign[i] * input axis axis[i]
    private final int[] axis;
    private final int[] sign;

    private Transform(int[] axis, int[] sign) {
        this.axis = axis;
        this.sign = sign;
    }

    /**
     * @return the vector (x, y, z) after this transform
     */
    public int[] apply(int x, int y, int z) {
        int[] v = {x, y, z};
        return new int[]{sign[0] * v[axis[0]], sign[1] * v[axis[1]], sign[2] * v[axis[2]]};
    }

    /**
     * @return this transform applied after {@code first}
     */
    public Transform after(Transform first) {
        int[] a = new int[3];
        int[] s = new int[3];
        for (int i = 0; i < 3; i++) {
            a[i] = first.axis[axis[i]];
            s[i] = sign[i] * first.sign[axis[i]];
        }
        return new Transform(a, s);
    }

    public Transform inverse() {
        int[] a = new int[3];
        int[] s = new int[3];
        for (int i = 0; i < 3; i++) {
            a[axis[i]] = i;
            s[axis[i]] = sign[i];
        }
        return new Transform(a, s);
    }

    public boolean isIdentity() {
        return equals(IDENTITY);
    }

    /**
     * @return 1 for a rotation, -1 for a mirror
     */
    public int determinant() {
        // Sign of the permutation: a single swap is odd, a cycle of three is even
        int swaps = (axis[0] == 0 ? 0 : 1) + (axis[1] == 1 ? 0 : 1) + (axis[2] == 2 ? 0 : 1);
        int parity = swaps == 2 ? -1 : 1;
        return parity * sign[0] * sign[1] * sign[2];
    }

    /**
     * @return true if up stays vertical (up or down)
     */
    public boolean keepsVertical() {
        return axis[1] == 1;
    }

    /**
     * @return true if up becomes down
     */
    public boolean flipsVertical() {
        return axis[1] == 1 && sign[1] < 0;
    }

    /**
     * Whether left and right are swapped as seen from above, which is what matters for stairs, doors and chests.
     */
    public boolean swapsHandedness() {
        // A vertical flip is a mirror that keeps the left and the right of what is seen from above
        return (keepsVertical() ? determinant() * sign[1] : determinant()) < 0;
    }

    /**
     * @return the new name of a direction (north, up...), or null if it is not one
     */
    @Nullable
    public String direction(String name) {
        int[] v = Directions.vector(name);
        return v == null ? null : Directions.name(apply(v[0], v[1], v[2]));
    }

    /**
     * @return the new name of an axis (x, y or z)
     */
    @Nullable
    public String axis(String name) {
        for (int i = 0; i < 3; i++) {
            if (AXES[i].equals(name)) {
                // The output axis that takes input axis i
                for (int j = 0; j < 3; j++) {
                    if (axis[j] == i) {
                        return AXES[j];
                    }
                }
            }
        }
        return null;
    }

    /**
     * @return a number from 0 to 47, different for each transform
     */
    public int code() {
        int permutation = axis[0] * 2 + (axis[1] > axis[2] ? 1 : 0);
        int signs = (sign[0] < 0 ? 1 : 0) | (sign[1] < 0 ? 2 : 0) | (sign[2] < 0 ? 4 : 0);
        return permutation * 8 + signs;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Transform other && Arrays.equals(axis, other.axis) && Arrays.equals(sign, other.sign);
    }

    @Override
    public int hashCode() {
        return code();
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < 3; i++) {
            text.append(i == 0 ? "" : ", ").append(sign[i] < 0 ? "-" : "").append(AXES[axis[i]]);
        }
        return text.append(")").toString();
    }

    /**
     * The six directions of block states, by name.
     */
    static final class Directions {

        private static final String[] NAMES = {"east", "west", "up", "down", "south", "north"};

        private Directions() {
        }

        @Nullable
        static int[] vector(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "east" -> new int[]{1, 0, 0};
                case "west" -> new int[]{-1, 0, 0};
                case "up" -> new int[]{0, 1, 0};
                case "down" -> new int[]{0, -1, 0};
                case "south" -> new int[]{0, 0, 1};
                case "north" -> new int[]{0, 0, -1};
                default -> null;
            };
        }

        static String name(int[] v) {
            for (int i = 0; i < 3; i++) {
                if (v[i] != 0) {
                    return NAMES[i * 2 + (v[i] > 0 ? 0 : 1)];
                }
            }
            throw new IllegalArgumentException("Not a direction: " + Arrays.toString(v));
        }

        static boolean isDirection(String name) {
            return vector(name) != null;
        }
    }
}
