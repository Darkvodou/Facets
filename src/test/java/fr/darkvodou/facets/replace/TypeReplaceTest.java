package fr.darkvodou.facets.replace;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TypeReplaceTest {

    private static final Set<String> WOODS = Set.of("oak", "spruce", "crimson");

    @Test
    void replacesTheType() {
        assertEquals("spruce_stairs", TypeReplace.rename("oak_stairs", "oak", "spruce"));
        assertEquals("spruce_planks", TypeReplace.rename("oak_planks", "oak", "spruce"));
        assertEquals("stripped_spruce_log", TypeReplace.rename("stripped_oak_log", "oak", "spruce"));
        assertEquals("diorite_slab", TypeReplace.rename("oak_slab", "oak", "diorite"));
        assertEquals("polished_granite_stairs", TypeReplace.rename("polished_diorite_stairs", "diorite", "granite"));
    }

    @Test
    void otherTypesAreKept() {
        assertNull(TypeReplace.rename("dark_oak_stairs", "oak", "spruce"));
        assertNull(TypeReplace.rename("stone_brick_stairs", "stone", "diorite"));
        assertNull(TypeReplace.rename("spruce_stairs", "oak", "birch"));
    }

    @Test
    void bricksKeepTheirPlural() {
        assertEquals("deepslate_bricks", TypeReplace.rename("stone_bricks", "stone_brick", "deepslate_brick"));
        assertEquals("deepslate_brick_stairs",
                TypeReplace.rename("stone_brick_stairs", "stone_brick", "deepslate_brick"));
        assertEquals("deepslate_tiles", TypeReplace.rename("stone_bricks", "stone_brick", "deepslate_tile"));
        assertEquals("diorite", TypeReplace.rename("stone_bricks", "stone_brick", "diorite"));
        assertEquals("stone_bricks", TypeReplace.rename("diorite", "diorite", "stone_brick"));
        assertEquals("cracked_deepslate_bricks",
                TypeReplace.rename("cracked_stone_bricks", "stone_brick", "deepslate_brick"));
    }

    @Test
    void woodIsSplitInPlanksLogsAndLeaves() {
        assertEquals("spruce_planks", TypeReplace.type("spruce_planks", WOODS));
        assertEquals("spruce_planks", TypeReplace.type("spruce_stairs", WOODS));
        assertEquals("spruce_planks", TypeReplace.type("spruce_door", WOODS));
        assertEquals("spruce_log", TypeReplace.type("spruce_log", WOODS));
        assertEquals("spruce_log", TypeReplace.type("spruce_wood", WOODS));
        assertEquals("stripped_spruce_log", TypeReplace.type("stripped_spruce_wood", WOODS));
        assertEquals("spruce_leaves", TypeReplace.type("spruce_sapling", WOODS));
        assertEquals("crimson_stem", TypeReplace.type("crimson_hyphae", WOODS));
        assertEquals("stripped_crimson_stem", TypeReplace.type("stripped_crimson_hyphae", WOODS));
        assertEquals("diorite", TypeReplace.type("polished_diorite_slab", WOODS));
        assertEquals("spruce", TypeReplace.family("spruce_planks", WOODS));
        assertEquals("spruce", TypeReplace.family("stripped_spruce_log", WOODS));
        assertEquals("crimson", TypeReplace.family("crimson_stem", WOODS));
        assertEquals("stone_brick", TypeReplace.family("stone_brick", WOODS));
    }

    @Test
    void onlyTheBlocksOfTheTypeAreReplaced() {
        assertEquals(List.of("cobblestone_stairs"),
                TypeReplace.retype("spruce_stairs", "spruce_planks", "cobblestone", "cobblestone", WOODS));
        assertEquals(List.of(), TypeReplace.retype("spruce_log", "spruce_planks", "cobblestone", "cobblestone", WOODS));
        assertEquals(List.of(), TypeReplace.retype("spruce_stairs", "spruce_log", "cobblestone", "cobblestone", WOODS));
        assertEquals(List.of("oak_stairs"),
                TypeReplace.retype("spruce_stairs", "spruce_planks", "oak_planks", "oak_planks", WOODS));
        // spruce_stairs is not a log
        assertEquals(List.of(), TypeReplace.retype("cobblestone_stairs", "cobblestone", "spruce_log", "spruce_log",
                WOODS));
    }

    @Test
    void fullBlocksFallBackOnTheFullBlockOfTheNewType() {
        // cobblestone_log does not exist: the block becomes cobblestone
        assertEquals(List.of("cobblestone_log", "cobblestone"),
                TypeReplace.retype("spruce_log", "spruce_log", "cobblestone", "cobblestone", WOODS));
        assertEquals(List.of("stripped_cobblestone_wood", "cobblestone"),
                TypeReplace.retype("stripped_spruce_wood", "stripped_spruce_log", "cobblestone", "cobblestone",
                        WOODS));
        assertEquals(List.of("stripped_oak_wood", "stripped_oak_log"),
                TypeReplace.retype("stripped_spruce_wood", "stripped_spruce_log", "stripped_oak_log",
                        "stripped_oak_log", WOODS));
        // Logs and stripped logs are two types
        assertEquals(List.of(), TypeReplace.retype("stripped_spruce_log", "spruce_log", "oak_log", "oak_log", WOODS));
        assertEquals(List.of("stripped_spruce_log"),
                TypeReplace.retype("spruce_log", "spruce_log", "stripped_spruce_log", "stripped_spruce_log", WOODS));
        assertEquals(List.of("oak_log", "oak_log"),
                TypeReplace.retype("spruce_log", "spruce_log", "oak_log", "oak_log", WOODS));
        assertEquals(List.of("spruce_planks"),
                TypeReplace.retype("spruce_log", "spruce_log", "spruce_planks", "spruce_planks", WOODS));
        assertEquals(List.of("spruce_log"),
                TypeReplace.retype("cobblestone", "cobblestone", "spruce_log", "spruce_log", WOODS));
    }

    @Test
    void theBaseIsTheBlockNamedAfterTheType() {
        assertEquals("cobblestone", TypeReplace.base("cobblestone",
                List.of(Material.MOSSY_COBBLESTONE, Material.COBBLESTONE_STAIRS, Material.COBBLESTONE)));
        assertEquals("stone_bricks", TypeReplace.base("stone_brick",
                List.of(Material.STONE_BRICK_STAIRS, Material.CRACKED_STONE_BRICKS, Material.STONE_BRICKS)));
        assertEquals("spruce_planks", TypeReplace.base("spruce",
                List.of(Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS)));
        assertEquals("quartz_block", TypeReplace.base("quartz",
                List.of(Material.QUARTZ_STAIRS, Material.QUARTZ_PILLAR, Material.QUARTZ_BLOCK)));
        assertNull(TypeReplace.base("acacia", List.of(Material.ACACIA_STAIRS, Material.ACACIA_DOOR)));
    }
}
