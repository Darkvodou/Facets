package fr.darkvodou.facets.replace;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TypeReplaceTest {

    @Test
    void theStemIsSharedByTheBlocksOfTheType() {
        assertEquals("oak", TypeReplace.stem("oak_planks"));
        assertEquals("quartz", TypeReplace.stem("quartz_block"));
        assertEquals("stone_brick", TypeReplace.stem("stone_bricks"));
        assertEquals("deepslate_tile", TypeReplace.stem("deepslate_tiles"));
        assertEquals("polished_granite", TypeReplace.stem("polished_granite"));
        assertEquals("white_wool", TypeReplace.stem("white_wool"));
    }

    @Test
    void blocksOfTheType() {
        assertEquals("", TypeReplace.shape("oak_planks", "oak_planks"));
        assertEquals("stairs", TypeReplace.shape("oak_stairs", "oak_planks"));
        assertEquals("fence_gate", TypeReplace.shape("oak_fence_gate", "oak_planks"));
        assertEquals("hanging_sign", TypeReplace.shape("oak_hanging_sign", "oak_planks"));
        assertEquals("wall", TypeReplace.shape("stone_brick_wall", "stone_bricks"));
        assertEquals("slab", TypeReplace.shape("white_wool_slab", "white_wool"));
        assertNull(TypeReplace.shape("dark_oak_stairs", "oak_planks"));
        assertNull(TypeReplace.shape("oak_log", "oak_planks"));
        assertNull(TypeReplace.shape("stone_brick_stairs", "stone"));
        assertNull(TypeReplace.shape("polished_granite_stairs", "granite"));
        assertNull(TypeReplace.shape("bamboo_mosaic", "bamboo_planks"));
    }

    @Test
    void replacesTheType() {
        assertEquals(List.of("spruce_stairs"), TypeReplace.retype("oak_stairs", "oak_planks", "spruce_planks"));
        assertEquals(List.of("spruce_planks"), TypeReplace.retype("oak_planks", "oak_planks", "spruce_planks"));
        assertEquals(List.of("diorite_slab"), TypeReplace.retype("oak_slab", "oak_planks", "diorite"));
        assertEquals(List.of("polished_granite_stairs"),
                TypeReplace.retype("granite_stairs", "granite", "polished_granite"));
        assertEquals(List.of("deepslate_brick_wall"),
                TypeReplace.retype("stone_brick_wall", "stone_bricks", "deepslate_bricks"));
        assertEquals(List.of("quartz_block"), TypeReplace.retype("stone_bricks", "stone_bricks", "quartz_block"));
        assertEquals(List.of("quartz_stairs"), TypeReplace.retype("stone_brick_stairs", "stone_bricks", "quartz_block"));
        assertEquals(List.of("red_wool_stairs"), TypeReplace.retype("white_wool_stairs", "white_wool", "red_wool"));
        assertEquals(List.of(), TypeReplace.retype("oak_log", "oak_planks", "spruce_planks"));
    }

    @Test
    void waxedCopperKeepsItsWax() {
        assertEquals(List.of("waxed_exposed_cut_copper_stairs", "exposed_cut_copper_stairs"),
                TypeReplace.retype("waxed_cut_copper_stairs", "cut_copper", "exposed_cut_copper"));
        assertEquals(List.of("exposed_cut_copper"),
                TypeReplace.retype("cut_copper", "cut_copper", "exposed_cut_copper"));
    }

    @Test
    void typesHaveDifferentStems() {
        assertEquals(TypeReplace.TYPES.size(), new HashSet<>(TypeReplace.TYPES.stream().map(TypeReplace::stem).toList()).size());
    }
}
