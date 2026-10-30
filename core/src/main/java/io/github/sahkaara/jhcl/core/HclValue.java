package io.github.sahkaara.jhcl.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A typed HCL value.
 *
 * <p>HCL distinguishes between a small set of value kinds: strings,
 * numbers, booleans, null, lists, and objects. {@code hcl4j} collapses
 * all of them into {@code Object}, forcing every consumer to write
 * {@code instanceof} chains. This sealed interface replaces that
 * pattern with exhaustive pattern matching:
 *
 * <pre>{@code
 * String description = switch (value) {
 *     case HclValue.StringValue s  -> "string " + s.value();
 *     case HclValue.NumberValue n  -> "number " + n.value();
 *     case HclValue.BoolValue b    -> "bool "   + b.value();
 *     case HclValue.NullValue v    -> "null";
 *     case HclValue.ListValue l    -> "list of " + l.items().size();
 *     case HclValue.ObjectValue o  -> "object with " + o.entries().size() + " keys";
 * };
 * }</pre>
 *
 * <p>The interface is {@code sealed}, so the compiler verifies that
 * the switch above is exhaustive — adding a new variant is a
 * compile-time breaking change, not a silent runtime surprise.
 */
public sealed interface HclValue
        permits HclValue.StringValue,
                HclValue.NumberValue,
                HclValue.BoolValue,
                HclValue.NullValue,
                HclValue.ListValue,
                HclValue.ObjectValue {

    /** A string literal, e.g. {@code "eu-west-1"}. */
    record StringValue(String value) implements HclValue {
        public StringValue {
            Objects.requireNonNull(value, "value");
        }
    }

    /** A numeric literal, e.g. {@code 3} or {@code 2.5}. */
    record NumberValue(double value) implements HclValue {}

    /** A boolean literal, {@code true} or {@code false}. */
    record BoolValue(boolean value) implements HclValue {}

    /** The {@code null} literal. */
    record NullValue() implements HclValue {}

    /** A list literal, e.g. {@code ["a", "b"]}. */
    record ListValue(List<HclValue> items) implements HclValue {
        public ListValue {
            Objects.requireNonNull(items, "items");
            items = List.copyOf(items);   // defensive copy, immutable
        }
    }

    /** An object literal, e.g. {@code { Name = "web", Env = "prod" }}. */
    record ObjectValue(Map<String, HclValue> entries) implements HclValue {
        public ObjectValue {
            Objects.requireNonNull(entries, "entries");
            entries = Map.copyOf(entries);   // defensive copy, immutable
        }
    }

    /* ---------- convenience factories ---------- */

    static HclValue of(String value)          { return new StringValue(value); }
    static HclValue of(double value)          { return new NumberValue(value); }
    static HclValue of(boolean value)         { return new BoolValue(value); }
    static HclValue nullValue()               { return new NullValue(); }
    static HclValue of(List<HclValue> items)  { return new ListValue(items); }
    static HclValue of(Map<String, HclValue> entries) { return new ObjectValue(entries); }
}