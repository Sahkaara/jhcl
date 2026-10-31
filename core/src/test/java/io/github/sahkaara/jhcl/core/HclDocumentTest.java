package io.github.sahkaara.jhcl.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HclDocumentTest {

    private static HclDocument sample() {
        var regionAttrs = Map.of(
                "default", HclValue.of("eu-west-1"),
                "type",    HclValue.of("string"));
        var regionBlock = HclBlock.of("variable", List.of("region"),
                regionAttrs, List.of());
        var topAttr = Map.<String, HclValue>of("env", HclValue.of("prod"));
        return HclDocument.of(topAttr, List.of(regionBlock));
    }

    @Test
    void readsTopLevelAttribute() {
        assertEquals("prod", sample().getString("env").orElseThrow());
    }

    @Test
    void readsAttributeInsideBlock() {
        var doc = sample();
        assertEquals("eu-west-1", doc.getString("variable.region.default").orElseThrow());
        assertEquals("string",    doc.getString("variable.region.type").orElseThrow());
    }

    @Test
    void returnsEmptyOnMissingPath() {
        var doc = sample();
        assertTrue(doc.getString("nope").isEmpty());
        assertTrue(doc.getString("variable.region.nope").isEmpty());
        assertTrue(doc.getString("variable.wrongLabel.default").isEmpty());
    }

    @Test
    void typedAccessorsRespectValueKind() {
        var doc = HclDocument.of(
                Map.of("count", HclValue.of(3.0), 
                "on",    HclValue.of(true)),
                List.of());
        assertEquals(3,     doc.getInt("count").orElseThrow());
        assertEquals(3.0,   doc.getNumber("count").orElseThrow());
        assertEquals(true,  doc.getBoolean("on").orElseThrow());
        assertTrue(doc.getBoolean("count").isEmpty());
        assertTrue(doc.getInt("on").isEmpty());
    }

    @Test
    void hasErrorsRecognizesErrorSeverity() {
        assertFalse(HclDocument.of(Map.of(), List.of()).hasErrors());

        var withWarning = new HclDocument(Map.of(), List.of(),
                List.of(Diagnostic.warning(1, 1, "W001", "warn")));
        assertFalse(withWarning.hasErrors());

        var withError = HclDocument.failed(
                List.of(Diagnostic.error(2, 5, "E001", "boom")));
        assertTrue(withError.hasErrors());
    }

    @Test
    void blocksOfTypeAndBlockOfType() {
        var doc = sample();
        assertEquals(1, doc.blocksOfType("variable").size());
        assertTrue(doc.blocksOfType("resource").isEmpty());
        assertTrue(doc.blockOfType("variable", "region").isPresent());
        assertTrue(doc.blockOfType("variable", "other").isEmpty());
    }
}
