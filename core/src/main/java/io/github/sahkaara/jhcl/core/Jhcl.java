package io.github.sahkaara.jhcl.core;

import com.bertramlabs.plugins.hcl4j.HCLParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Public facade of the jhcl core library.
 *
 * <pre>{@code
 * HclDocument doc = Jhcl.parse(Path.of("config.hcl"));
 * if (doc.hasErrors()) {
 *     doc.errors().forEach(System.err::println);
 *     return;
 * }
 * String region = doc.getString("variable.region.default").orElse("eu-west-1");
 * }</pre>
 */
public final class Jhcl {

    private Jhcl() {
        // static-only facade
    }

    /** Parse a file from disk. */
    public static HclDocument parse(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        return parse(Files.readString(file));
    }

    /** Parse an in-memory HCL source. Returns a failed document on parse errors. */
    public static HclDocument parse(String source) {
        Objects.requireNonNull(source, "source");
        try {
            Object raw = new HCLParser().parse(source);
            if (raw instanceof java.util.Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                var typed = (java.util.Map<String, Object>) map;
                return Hcl4jAdapter.toDocument(typed);
            }
            return HclDocument.failed(List.of(
                    Diagnostic.error(0, 0, "E001",
                            "unexpected parser output: " + raw.getClass())));
        } catch (Exception e) {
            return HclDocument.failed(List.of(
                    Diagnostic.error(0, 0, "E002",
                            "parse error: " + e.getMessage())));
        }
    }

    /**
     * Format an HCL source. Currently a passthrough stub — the real
     * formatter lands in a later iteration.
     */
    public static String format(String source) {
        Objects.requireNonNull(source, "source");
        return Formatter.format(source);
    }

    /**
     * Run static checks on a parsed document. Currently a stub that
     * returns an empty list — the real linter lands in a later iteration.
     */
    public static List<Diagnostic> lint(HclDocument document) {
        Objects.requireNonNull(document, "document");
        return List.of();
    }
}
