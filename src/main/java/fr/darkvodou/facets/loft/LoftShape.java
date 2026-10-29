package fr.darkvodou.facets.loft;

import java.util.Locale;

/**
 * How the frames and the bands between them are drawn.
 */
public enum LoftShape {

    /** Splines everywhere. */
    SMOOTH("loft", "smooth", "Loft", ""),
    /** Straight segments: polyline frames, straight rails from frame to frame. */
    POLY("loftpoly", "poly", "Loft poly", "-poly"),
    /** Polyline frames, and flat triangles between them. */
    FACET("loftfacet", "facet", "Loft facet", "-facet");

    private final String selection;
    private final String subcommand;
    private final String typeName;
    private final String messageSuffix;

    LoftShape(String selection, String subcommand, String typeName, String messageSuffix) {
        this.selection = selection;
        this.subcommand = subcommand;
        this.typeName = typeName;
        this.messageSuffix = messageSuffix;
    }

    /**
     * Name after //sel.
     */
    public String selection() {
        return selection;
    }

    /**
     * Name after //loft, to switch to this shape.
     */
    public String subcommand() {
        return subcommand;
    }

    public String typeName() {
        return typeName;
    }

    /**
     * Key of a message that has a variant per shape: "loft.selected" → "loft.selected-poly".
     */
    public String message(String key) {
        return key + messageSuffix;
    }

    /**
     * Frames are polylines.
     */
    public boolean straight() {
        return this != SMOOTH;
    }

    /**
     * @param name a //sel name, or its short form (loftp, loftf)
     */
    public static LoftShape fromSelection(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (LoftShape shape : values()) {
            if (shape.selection.equals(lower) || (shape != SMOOTH && shape.selection.substring(0, 5).equals(lower))) {
                return shape;
            }
        }
        return null;
    }

    public static LoftShape fromSubcommand(String name) {
        for (LoftShape shape : values()) {
            if (shape.subcommand.equalsIgnoreCase(name)) {
                return shape;
            }
        }
        return null;
    }
}
