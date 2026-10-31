package io.github.sahkaara.jhcl.core;

import java.util.Objects;

/**
 * A single diagnostic emitted by the {@link Linter} or the parser.
 *
 * <p>Diagnostics follow the GNU error convention when printed:
 * {@code file:line:column: severity: message}. This format is recognized
 * by editors (VS Code, vim, emacs) and CI systems (GitHub Actions,
 * GitLab CI), which annotate the exact line of the offending code.
 *
 * @param severity the severity level; never {@code null}
 * @param line     1-based line number; 0 means "no position"
 * @param column   1-based column number; 0 means "no position"
 * @param code     a short machine-readable code, e.g. {@code "W001"}
 * @param message  a human-readable explanation; never {@code null}
 */
public record Diagnostic(
        Severity severity,
        int line,
        int column,
        String code,
        String message
) {

    /** Compact constructor: validate invariants before the record is built. */
    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code,     "code");
        Objects.requireNonNull(message,  "message");
        if (line < 0)   throw new IllegalArgumentException("line must be >= 0, got " + line);
        if (column < 0) throw new IllegalArgumentException("column must be >= 0, got " + column);
    }

    /** The three severity levels a diagnostic can carry. */
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    /**
     * Render in GNU format: {@code line:column: severity: message}.
     * If {@code line == 0}, the position is omitted.
     */
    public String format() {
        String level = severity.name().toLowerCase();
        if (line == 0) {
            return level + ": " + message;
        }
        return line + ":" + column + ": " + level + ": " + message;
    }

    /* ---------- convenience factories ---------- */

    public static Diagnostic error(int line, int column, String code, String message) {
        return new Diagnostic(Severity.ERROR, line, column, code, message);
    }

    public static Diagnostic warning(int line, int column, String code, String message) {
        return new Diagnostic(Severity.WARNING, line, column, code, message);
    }

    public static Diagnostic info(int line, int column, String code, String message) {
        return new Diagnostic(Severity.INFO, line, column, code, message);
    }
}
