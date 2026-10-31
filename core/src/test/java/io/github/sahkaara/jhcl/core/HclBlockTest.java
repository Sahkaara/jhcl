package io.github.sahkaara.jhcl.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HclBlockTest {

    @Test
    void buildsAndAnswersQueries() {
        var attrs = Map.of(
                "ami",   HclValue.of("ami-12345"),
                "count", HclValue.of(3.0));
        var block = HclBlock.of("resource",
                List.of("aws_instance", "web"),
                attrs,
                List.of());

        assertEquals("resource", block.type());
        assertEquals(List.of("aws_instance", "web"), block.labels());
        assertEquals("aws_instance", block.label(0).orElseThrow());
        assertEquals("ami-12345", ((HclValue.StringValue)
                block.attribute("ami").orElseThrow()).value());
        assertTrue(block.attribute("missing").isEmpty());
        assertFalse(block.isEmpty());
    }

    @Test
    void rejectsBlankType() {
        assertThrows(IllegalArgumentException.class,
                () -> HclBlock.of("  ", List.of(), Map.of(), List.of()));
    }

    @Test
    void copiesCollectionsDefensively() {
        var labels = new java.util.ArrayList<>(List.of("a"));
        var block = HclBlock.of("x", labels, Map.of(), List.of());
        labels.add("b");
        assertEquals(List.of("a"), block.labels());
    }

    @Test
    void blocksOfTypeFilters() {
        var inner = HclBlock.of("lifecycle", List.of(), Map.of(), List.of());
        var outer = HclBlock.of("resource", List.of("x", "y"),
                Map.of(), List.of(inner));

        assertEquals(1, outer.blocksOfType("lifecycle").size());
        assertTrue(outer.blocksOfType("nope").isEmpty());
    }
}
