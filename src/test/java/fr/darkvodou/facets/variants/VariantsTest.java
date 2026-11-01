package fr.darkvodou.facets.variants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariantsTest {

    private static void assertKey(String family, String shape, String name) {
        assertEquals(new Variants.Key(family, shape), Variants.key(name), name);
    }

    @Test
    void woodShapesShareTheirFamily() {
        assertKey("oak", "stairs", "OAK_STAIRS");
        assertKey("oak", "planks", "OAK_PLANKS");
        assertKey("oak", "fence_gate", "OAK_FENCE_GATE");
        assertKey("oak", "trapdoor", "OAK_TRAPDOOR");
        assertKey("oak", "hanging_sign", "OAK_HANGING_SIGN");
        assertKey("oak", "log", "STRIPPED_OAK_LOG");
        assertKey("dark_oak", "stairs", "DARK_OAK_STAIRS");
        assertKey("crimson", "stem", "STRIPPED_CRIMSON_STEM");
        assertKey("bamboo", "stairs", "BAMBOO_MOSAIC_STAIRS");
    }

    @Test
    void bricksAndTilesAreOneFamily() {
        assertKey("stone_brick", "", "STONE_BRICKS");
        assertKey("stone_brick", "stairs", "STONE_BRICK_STAIRS");
        assertKey("stone_brick", "wall", "MOSSY_STONE_BRICK_WALL");
        assertKey("stone_brick", "", "CRACKED_STONE_BRICKS");
        assertKey("brick", "", "BRICKS");
        assertKey("brick", "slab", "BRICK_SLAB");
        assertKey("deepslate_tile", "", "DEEPSLATE_TILES");
        assertKey("deepslate_tile", "stairs", "DEEPSLATE_TILE_STAIRS");
    }

    @Test
    void modifiersKeepTheFamily() {
        assertKey("stone", "", "STONE");
        assertKey("stone", "slab", "SMOOTH_STONE_SLAB");
        assertKey("deepslate", "", "COBBLED_DEEPSLATE");
        assertKey("deepslate", "stairs", "POLISHED_DEEPSLATE_STAIRS");
        assertKey("sandstone", "", "CUT_SANDSTONE");
        assertKey("quartz", "block", "QUARTZ_BLOCK");
        assertKey("quartz", "pillar", "QUARTZ_PILLAR");
        assertKey("copper", "", "CUT_COPPER");
        assertKey("exposed_copper", "stairs", "WAXED_EXPOSED_CUT_COPPER_STAIRS");
    }

    @Test
    void colorsAreFamilies() {
        assertKey("white", "wool", "WHITE_WOOL");
        assertKey("white", "stained_glass_pane", "WHITE_STAINED_GLASS_PANE");
        assertKey("light_blue", "glazed_terracotta", "LIGHT_BLUE_GLAZED_TERRACOTTA");
        assertKey("glass", "pane", "GLASS_PANE");
        assertKey("glass", "stained_glass", "GLASS");
        assertKey("tinted_glass", "stained_glass", "TINTED_GLASS");
        assertKey("white", "stairs", "WHITE_WOOL_STAIRS");
        assertKey("light_gray", "slab", "LIGHT_GRAY_CONCRETE_SLAB");
    }
}
