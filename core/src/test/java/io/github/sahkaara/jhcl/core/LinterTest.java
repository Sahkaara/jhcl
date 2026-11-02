package io.github.sahkaara.jhcl.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LinterTest {

    @Test
    void noDiagnosticsOnCleanDocument() {
        var block = HclBlock.of("variable", List.of("region"),
                Map.of("default", HclValue.of("eu-west-1")), List.of());
        var doc = HclDocument.of(Map.of(), List.of(block));
        assertTrue(Linter.lint(doc).isEmpty());
    }

    @Test
    void w001DetectsDuplicateBlock() {
        var b1 = HclBlock.of("variable", List.of("region"),
                Map.of("default", HclValue.of("a")), List.of());
        var b2 = HclBlock.of("variable", List.of("region"),
                Map.of("default", HclValue.of("b")), List.of());
        var doc = HclDocument.of(Map.of(), List.of(b1, b2));
        var diags = Linter.lint(doc);
        assertEquals(1, diags.size());
        assertEquals("W001", diags.get(0).code());
        assertEquals(Diagnostic.Severity.WARNING, diags.get(0).severity());
    }

    @Test
    void w003DetectsMissingLabels() {
        // `resource` without labels: expected 2, found 0
        var bad = HclBlock.of("resource", List.of(), Map.of(), List.of());
        var doc = HclDocument.of(Map.of(), List.of(bad));
        var diags = Linter.lint(doc);
        assertEquals(1, diags.size());
        assertEquals("W003", diags.get(0).code());
        assertTrue(diags.get(0).message().contains("expects 2"));
    }

    @Test
    void w003DetectsExtraLabels() {
        // `variable` with 2 labels: expected 1
        var bad = HclBlock.of("variable", List.of("a", "b"), Map.of(), List.of());
        var doc = HclDocument.of(Map.of(), List.of(bad));
        var diags = Linter.lint(doc);
        assertEquals(1, diags.size(), "exactly one diagnostic expected");
        assertEquals("W003", diags.get(0).code(), "should be W003");
    }

    @Test
    void w003RecursesIntoNestedBlocks() {
        var badInner  = HclBlock.of("resource", List.of("only_one"), Map.of(), List.of());
        var outer     = HclBlock.of("module", List.of("m"), Map.of(), List.of(badInner));
        var doc       = HclDocument.of(Map.of(), List.of(outer));
        var diags     = Linter.lint(doc);
        assertTrue(diags.stream().anyMatch(d -> d.code().equals("W003")));
    }

    @Test
    void i001DetectsNonCanonicalSource() {
        var doc = HclDocument.of(Map.of(), List.of());
        String messy = "a=1\n";
        var diags = Linter.lint(doc, messy);
        assertTrue(diags.stream().anyMatch(d -> d.code().equals("I001")));
    }

    @Test
    void i001DoesNotFireOnCanonicalSource() {
        var doc = HclDocument.of(Map.of(), List.of());
        String clean = "a = 1\n";
        var diags = Linter.lint(doc, clean);
        assertTrue(diags.stream().noneMatch(d -> d.code().equals("I001")));
    }

    @Test
    void lintThroughFacadeWorks() {
        String src = "variable \"region\" {\n  default = \"eu-west-1\"\n}\n";
        var doc = Jhcl.parse(src);
        assertTrue(Jhcl.lint(doc).isEmpty());
        // With source, I001 should fire if not canonical.
        var messy = "variable \"region\" {\ndefault=\"eu-west-1\"\n}\n";
        var doc2  = Jhcl.parse(messy);
        var diags = Jhcl.lint(doc2, messy);
        assertTrue(diags.stream().anyMatch(d -> d.code().equals("I001")));
    }
}
