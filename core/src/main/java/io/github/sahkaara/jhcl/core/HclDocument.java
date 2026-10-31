package io.github.sahkaara.jhcl.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A parsed HCL document.
 *
 * <p>A document is the root of the AST: it holds top-level attributes
 * (assignments outside any block), top-level blocks, and any parsing
 * errors discovered while building it.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * HclDocument doc = Jhcl.parse(Path.of("config.hcl"));
 * if (doc.hasErrors()) {
 *     doc.errors().forEach(System.err::println);
 * }
 *
 * Optional<String> region = doc.getString("variable.region.default");
 * }</pre>
 *
 * <p>The path syntax accepted by {@link #get(String)} is dot-separated:
 * <ul>
 *   <li>{@code "key"} — a top-level attribute;</li>
 *   <li>{@code "block.label1.label2.key"} — navigate into a block
 *       whose type is {@code block} and whose labels match, then read
 *       the attribute {@code key} inside it.</li>
 * </ul>
 *
 * <p>A document is immutable. Both {@code attributes}, {@code blocks}
 * and {@code errors} are defensively copied in the constructor.
 */
public record HclDocument(
        Map<String, HclValue> attributes,
        List<HclBlock> blocks,
        List<Diagnostic> errors
) {

    /** Compact constructor: validate invariants and copy collections. */
    public HclDocument {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(blocks,     "blocks");
        Objects.requireNonNull(errors,     "errors");
        attributes = Map.copyOf(attributes);
        blocks     = List.copyOf(blocks);
        errors     = List.copyOf(errors);
    }

    /* ---------- factories ---------- */

    /** A document with the given content and no errors. */
    public static HclDocument of(Map<String, HclValue> attributes, List<HclBlock> blocks) {
        return new HclDocument(attributes, blocks, List.of());
    }

    /** A document that failed to parse; all content is empty. */
    public static HclDocument failed(List<Diagnostic> errors) {
        return new HclDocument(Map.of(), List.of(), errors);
    }

    /* ---------- queries ---------- */

    /** True if parsing produced at least one error-level diagnostic. */
    public boolean hasErrors() {
        return errors.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    /** True if the document has no attributes, blocks, or errors. */
    public boolean isEmpty() {
        return attributes.isEmpty() && blocks.isEmpty() && errors.isEmpty();
    }

    /** Return all top-level blocks of the given type. */
    public List<HclBlock> blocksOfType(String type) {
        Objects.requireNonNull(type, "type");
        return blocks.stream().filter(b -> b.type().equals(type)).toList();
    }

    /**
     * Return the first block of the given type whose labels match
     * {@code labels} exactly, in order. If {@code labels} is empty,
     * the first block of that type is returned regardless of labels.
     */
    public Optional<HclBlock> blockOfType(String type, String... labels) {
        Objects.requireNonNull(type,   "type");
        Objects.requireNonNull(labels, "labels");
        for (HclBlock b : blocks) {
            if (!b.type().equals(type)) continue;
            if (b.labels().size() != labels.length) continue;
            boolean match = true;
            for (int i = 0; i < labels.length; i++) {
                if (!b.labels().get(i).equals(labels[i])) { match = false; break; }
            }
            if (match) return Optional.of(b);
        }
        return Optional.empty();
    }

    /**
     * Resolve a dot-separated path to an {@link HclValue}.
     *
     * <p>The first segment is matched against top-level attributes first;
     * if no attribute matches, it is interpreted as a block type and the
     * remaining segments as block labels followed by a final attribute name.
     */
    public Optional<HclValue> get(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) return Optional.empty();

        // 1) direct attribute hit ("key" or dotted key inside an object)
        HclValue direct = attributes.get(path);
        if (direct != null) return Optional.of(direct);

        // 2) navigate into a block: "type.label1...labelN.attr"
        String[] segments = path.split("\\.");
        if (segments.length < 2) return Optional.empty();

        String type       = segments[0];
        int    labelCount = segments.length - 2;
        String attrName   = segments[segments.length - 1];

        for (HclBlock b : blocks) {
            if (!b.type().equals(type)) continue;
            if (b.labels().size() != labelCount) continue;
            boolean labelsMatch = true;
            for (int i = 0; i < labelCount; i++) {
                if (!b.labels().get(i).equals(segments[i + 1])) { labelsMatch = false; break; }
            }
            if (!labelsMatch) continue;
            Optional<HclValue> found = b.attribute(attrName);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /* ---------- typed accessors ---------- */

    public Optional<String> getString(String path) {
        return get(path).flatMap(v -> v instanceof HclValue.StringValue s
                ? Optional.of(s.value()) : Optional.empty());
    }

    public Optional<Double> getNumber(String path) {
        return get(path).flatMap(v -> v instanceof HclValue.NumberValue n
                ? Optional.of(n.value()) : Optional.empty());
    }

    public Optional<Integer> getInt(String path) {
        return getNumber(path).map(Double::intValue);
    }

    public Optional<Boolean> getBoolean(String path) {
        return get(path).flatMap(v -> v instanceof HclValue.BoolValue b
                ? Optional.of(b.value()) : Optional.empty());
    }
}
