package io.github.sahkaara.jhcl.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static checks on a parsed HCL document.
 *
 * <p>Diagnostics follow the same conventions as the parser: they are
 * returned as a list of {@link Diagnostic}, each carrying severity,
 * line/column, a short code, and a message.
 *
 * <p>Implemented rules:
 * <table>
 *   <caption>Rules</caption>
 *   <tr><th>Code</th><th>Description</th></tr>
 *   <tr><td>W001</td><td>Duplicate block (same type and labels)</td></tr>
 *   <tr><td>W003</td><td>Missing labels for well-known block types</td></tr>
 *   <tr><td>I001</td><td>Non-canonical formatting (source required)</td></tr>
 * </table>
 *
 * <p>Rule W002 (duplicate attribute) is not implemented because
 * {@code hcl4j} collapses duplicates into a {@code Map} during parsing;
 * detecting it would require lexical analysis of the source, planned
 * for a later iteration.
 */
public final class Linter {

    private Linter() {
        // static-only
    }

    /**
     * Expected number of labels per well-known block type.
     * Types not listed here are unchecked.
     */
    private static final Map<String, Integer> EXPECTED_LABELS = Map.of(
            "resource", 2,
            "data",     2,
            "variable", 1,
            "output",   1,
            "module",   1,
            "provider", 1,
            "terraform", 0,
            "locals",    0
    );

    /**
     * Run AST-based checks only. Returns a (possibly empty) list of
     * diagnostics in source order.
     */
    public static List<Diagnostic> lint(HclDocument doc) {
        List<Diagnostic> out = new ArrayList<>();
        checkDuplicateBlocks(doc, out);
        checkLabelCounts(doc.blocks(), out);
        return out;
    }

    /**
     * Run all checks, including those that need the original source
     * (currently only I001: non-canonical formatting).
     *
     * @param doc    the parsed document
     * @param source the original source, or {@code null} to skip source-based rules
     */
    public static List<Diagnostic> lint(HclDocument doc, String source) {
        List<Diagnostic> out = lint(doc);
        if (source != null && !source.isEmpty()) {
            checkCanonicalFormatting(source, out);
        }
        return out;
    }

    /* ---------- W001: duplicate blocks ---------- */

    private static void checkDuplicateBlocks(HclDocument doc, List<Diagnostic> out) {
        checkDuplicateBlocks(doc.blocks(), out);
    }

    private static void checkDuplicateBlocks(List<HclBlock> blocks, List<Diagnostic> out) {
        Set<String> seen = new HashSet<>();
        for (HclBlock b : blocks) {
            String key = b.type() + "|" + String.join("|", b.labels());
            if (!seen.add(key)) {
                out.add(Diagnostic.warning(
                        b.line(), b.column(), "W001",
                        "duplicate block: " + b.type() + describeLabels(b.labels())));
            }
            // Recurse into nested blocks (same level comparison only).
            checkDuplicateBlocks(b.blocks(), out);
        }
    }

    /* ---------- W003: label counts ---------- */

    private static void checkLabelCounts(List<HclBlock> blocks, List<Diagnostic> out) {
        for (HclBlock b : blocks) {
            Integer expected = EXPECTED_LABELS.get(b.type());
            if (expected != null && b.labels().size() != expected) {
                out.add(Diagnostic.warning(
                        b.line(), b.column(), "W003",
                        "block '" + b.type() + "' expects " + expected
                                + " label(s), found " + b.labels().size()));
            }
            checkLabelCounts(b.blocks(), out);
        }
    }

    /* ---------- I001: non-canonical formatting ---------- */

    private static void checkCanonicalFormatting(String source, List<Diagnostic> out) {
        String canonical = Formatter.format(source);
        if (!canonical.equals(source)) {
            out.add(Diagnostic.info(1, 1, "I001",
                    "file is not canonically formatted; run 'jhcl fmt' to fix"));
        }
    }

    /* ---------- helpers ---------- */

    private static String describeLabels(List<String> labels) {
        if (labels.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append('"').append(labels.get(i)).append('"');
        }
        return sb.toString();
    }
}
