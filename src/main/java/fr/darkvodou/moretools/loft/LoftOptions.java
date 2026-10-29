package fr.darkvodou.moretools.loft;

/**
 * How a loft is turned into blocks.
 *
 * @param shape      splines, straight segments or flat triangles
 * @param closed     connect the last frame back to the first one
 * @param outline    draw the outline instead of the filled surface
 * @param count      number of lines drawn across the surface (0: none)
 * @param horizontal lines follow the frames instead of going from frame to frame
 * @param net        lines in both directions, as a net (overrides horizontal)
 * @param uniform    lines following the frames are evenly spaced by distance on the surface
 * @param down       stack every block down to the floor
 */
public record LoftOptions(LoftShape shape, boolean closed, boolean outline, int count, boolean horizontal, boolean net,
                          boolean uniform, boolean down) {

    public static final LoftOptions FILL = fill(LoftShape.SMOOTH);

    /**
     * The filled surface, as a loft selection of this shape holds it.
     */
    public static LoftOptions fill(LoftShape shape) {
        return new LoftOptions(shape, false, false, 0, false, false, false, false);
    }
}
