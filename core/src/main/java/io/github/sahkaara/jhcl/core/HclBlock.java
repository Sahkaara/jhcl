package io.github.sahkaara.jhcl.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A single HCL block.
 *
 * <p>Every block has four components: a type, zero or more labels,
 * a map of attributes, and a list of nested blocks.
 */
public record HclBlock(
        String type,
        List<String> labels,
        Map<String, HclValue> attributes,
        List<HclBlock> blocks,
        int line,
        int column
) {

    /** Compact constructor: validate invariants and copy collections. */
    public HclBlock {
        Objects.requireNonNull(type,       "type");
        Objects.requireNonNull(labels,     "labels");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(blocks,     "blocks");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (line < 0)   throw new IllegalArgumentException("line must be >= 0, got " + line);
        if (column < 0) throw new IllegalArgumentException("column must be >= 0, got " + column);
        labels     = List.copyOf(labels);
        attributes = Map.copyOf(attributes);
        blocks     = List.copyOf(blocks);
    }

    /** Convenience factory for a block with no position info. */
    public static HclBlock of(String type,
                            List<String> labels,
                            Map<String, HclValue> attributes,
                            List<HclBlock> blocks) {
        return new HclBlock(type, labels, attributes, blocks, 0, 0);
    }

    /** Return the label at {@code index}, or empty if out of range. */
    public Optional<String> label(int index) {
        if (index < 0 || index >= labels.size()) return Optional.empty();
        return Optional.of(labels.get(index));
    }

    /** Return the attribute with the given name, or empty if absent. */
    public Optional<HclValue> attribute(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(attributes.get(name));
    }

    /** Return all nested blocks of the given type. */
    public List<HclBlock> blocksOfType(String blockType) {
        Objects.requireNonNull(blockType, "blockType");
        return blocks.stream()
                .filter(b -> b.type().equals(blockType))
                .toList();
    }

    /** True if this block has no attributes and no nested blocks. */
    public boolean isEmpty() {
        return attributes.isEmpty() && blocks.isEmpty();
    }
}
