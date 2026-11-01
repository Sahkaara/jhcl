package io.github.sahkaara.jhcl.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the Map-based output of {@code hcl4j} into jhcl's typed model.
 *
 * <p>hcl4j produces {@code Map<String, Object>}, where:
 * <ul>
 *   <li>a block with N labels becomes N+1 levels of nested maps;</li>
 *   <li>scalars are {@code String}, {@code Double}, {@code Boolean};</li>
 *   <li>lists are {@code ArrayList};</li>
 *   <li>object literals are {@code LinkedHashMap};</li>
 *   <li>bare identifiers such as {@code type = string} become
 *       {@code StringPrimitiveType:string} (a custom hcl4j type).</li>
 * </ul>
 *
 * <p>The adapter is package-private: users go through {@link Jhcl}.
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
                blocks.add(toBlock(key, mapValue));
            } else {
                attributes.put(key, toValue(value));
            }
        }
        return HclDocument.of(attributes, blocks);
    }

    private static HclBlock toBlock(String type, Map<?, ?> raw) {
        List<String> labels  = new ArrayList<>();
        Map<?, ?>    current = raw;

        // Descend through the label levels. Each level has exactly one
        // key whose value is another Map. We keep descending while that
        // value is not yet a body (i.e. while it does not contain any
        // non-Map value).
        while (current.size() == 1) {
            Map.Entry<?, ?> only = current.entrySet().iterator().next();
            if (!(only.getValue() instanceof Map<?, ?> next)) break;
            labels.add(String.valueOf(only.getKey()));
            current = next;
            if (isBody(current)) break;
        }

        Map<String, HclValue> attributes = new LinkedHashMap<>();
        List<HclBlock>        subBlocks  = new ArrayList<>();

        for (Map.Entry<?, ?> entry : current.entrySet()) {
            String key   = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> mapValue && !isBody(mapValue)) {
                subBlocks.add(toBlock(key, mapValue));
            } else {
                attributes.put(key, toValue(value));
            }
        }
        return HclBlock.of(type, labels, attributes, subBlocks);
    }

    /** A map is a "body" if at least one of its values is not a Map. */
    private static boolean isBody(Map<?, ?> map) {
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
