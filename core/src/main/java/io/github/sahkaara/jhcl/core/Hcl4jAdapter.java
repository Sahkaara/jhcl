package io.github.sahkaara.jhcl.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the Map-based output of {@code hcl4j} into jhcl's typed model.
 *
 * <p>hcl4j produces nested maps where each label level adds one depth.
 * For example:
 * <pre>
 * variable "region" { ... }        -&gt; {variable: {region: {attrs}}}
 * resource "aws_vpc" "main" {...}  -&gt; {resource: {aws_vpc: {main: {attrs}}}}
 * </pre>
 *
 * <p>When multiple blocks of the same type appear (e.g. two
 * {@code resource} blocks), the label level contains multiple siblings:
 * <pre>
 * resource "aws_vpc" "main" {...}
 * resource "aws_subnet" "pub" {...}
 *   -&gt; {resource: {aws_vpc: {main: {..}}, aws_subnet: {pub: {..}}}}
 * </pre>
 * The adapter expands each sibling into its own {@link HclBlock}, so the
 * typed model is faithful to the source.
 */
final class Hcl4jAdapter {

    private Hcl4jAdapter() {
        // static-only
    }

    static HclDocument toDocument(Map<String, Object> raw) {
        Map<String, HclValue> attributes = new LinkedHashMap<>();
        List<HclBlock> blocks = new ArrayList<>();

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key   = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> mapValue) {
                blocks.addAll(toBlocks(key, mapValue));
            } else {
                attributes.put(key, toValue(value));
            }
        }
        return HclDocument.of(attributes, blocks);
    }

    /**
     * Expand a Map into one or more blocks of the same type. A single Map
     * can describe multiple sibling blocks when it holds several keys at
     * the same label depth.
     */
    static List<HclBlock> toBlocks(String type, Map<?, ?> raw) {
        // Base case: this Map is a block body (at least one scalar value,
        // or empty). Return a single block with no labels.
        if (isBody(raw)) {
            return List.of(buildBlock(type, List.of(), raw));
        }

        // Recursive case: all values are Maps, so each entry is a label
        // branch. Expand each branch and prepend the key as a label.
        List<HclBlock> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String label = String.valueOf(entry.getKey());
            if (entry.getValue() instanceof Map<?, ?> next) {
                for (HclBlock b : toBlocks(type, next)) {
                    List<String> newLabels = new ArrayList<>();
                    newLabels.add(label);
                    newLabels.addAll(b.labels());
                    result.add(new HclBlock(type, newLabels,
                            b.attributes(), b.blocks(),
                            b.line(), b.column()));
                }
            }
        }
        return result;
    }

    private static HclBlock buildBlock(String type, List<String> labels, Map<?, ?> raw) {
        Map<String, HclValue> attributes = new LinkedHashMap<>();
        List<HclBlock>        subBlocks  = new ArrayList<>();

        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key   = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            // Object literals (tags = { ... }) and nested blocks both look
            // like Maps. We treat them as object literals (attributes) by
            // default. Nested blocks are a known limitation of the
            // hcl4j→typed adapter and are tracked on the roadmap.
            attributes.put(key, toValue(value));
        }
        return HclBlock.of(type, labels, attributes, subBlocks);
    }

    /** A Map is a "body" if at least one of its values is not a Map. */
    private static boolean isBody(Map<?, ?> map) {
        if (map.isEmpty()) return true;
        for (Object v : map.values()) {
            if (!(v instanceof Map)) return true;
        }
        return false;
    }

    private static HclValue toValue(Object raw) {
        if (raw == null)                    return HclValue.nullValue();
        if (raw instanceof String s)        return HclValue.of(s);
        if (raw instanceof Boolean b)       return HclValue.of(b);
        if (raw instanceof Number n)        return HclValue.of(n.doubleValue());
        if (raw instanceof List<?> list) {
            List<HclValue> items = new ArrayList<>(list.size());
            for (Object item : list) items.add(toValue(item));
            return HclValue.of(items);
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, HclValue> entries = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                entries.put(String.valueOf(e.getKey()), toValue(e.getValue()));
            }
            return HclValue.of(entries);
        }
        // hcl4j custom types: normalize to strings.
        String text = raw.toString();
        String prefix = "StringPrimitiveType:";
        if (text.startsWith(prefix)) {
            return HclValue.of(text.substring(prefix.length()));
        }
        return HclValue.of(text);
    }
}
