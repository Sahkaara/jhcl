package io.github.sahkaara.jhcl.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Token-based HCL formatter.
 *
 * <p>Unlike the parser, the formatter works directly on the character
 * stream. This means it succeeds even on files with syntax errors —
 * useful for pre-commit hooks and editor-on-save.
 *
 * <p>The output is idempotent: {@code format(format(x)).equals(format(x))}
 * for all {@code x}.
 */
public final class Formatter {

    private Formatter() {
        // static-only
    }

    /** Format {@code source} and return the canonical representation. */
    public static String format(String source) {
        if (source == null || source.isEmpty()) return "";

        List<Token> tokens = tokenize(source);
        String raw = emit(tokens);
        return alignEquals(raw);
    }

    /* ---------- tokenizer ---------- */

    private enum Kind {
        EOF, NEWLINE, IDENT, STRING, NUMBER, LBRACE, RBRACE,
        LBRACKET, RBRACKET, EQUALS, COMMA, COMMENT
    }

    private record Token(Kind kind, String text) {}

    private static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);

            if (c == ' ' || c == '\t' || c == '\r') { i++; continue; }
            if (c == '\n') { out.add(new Token(Kind.NEWLINE, "\n")); i++; continue; }
            if (c == '#' || (c == '/' && i + 1 < n && s.charAt(i + 1) == '/')) {
                int start = i;
                while (i < n && s.charAt(i) != '\n') i++;
                out.add(new Token(Kind.COMMENT, s.substring(start, i)));
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i = Math.min(i + 2, n);
                out.add(new Token(Kind.COMMENT, s.substring(start, i)));
                continue;
            }
            switch (c) {
                case '{' -> { out.add(new Token(Kind.LBRACE,   "{")); i++; continue; }
                case '}' -> { out.add(new Token(Kind.RBRACE,   "}")); i++; continue; }
                case '[' -> { out.add(new Token(Kind.LBRACKET, "[")); i++; continue; }
                case ']' -> { out.add(new Token(Kind.RBRACKET, "]")); i++; continue; }
                case '=' -> { out.add(new Token(Kind.EQUALS,   "=")); i++; continue; }
                case ',' -> { out.add(new Token(Kind.COMMA,    ",")); i++; continue; }
            }
            if (c == '"') {
                int start = i++;
                while (i < n && s.charAt(i) != '"') {
                    if (s.charAt(i) == '\\' && i + 1 < n) i += 2;
                    else i++;
                }
                if (i < n) i++;
                out.add(new Token(Kind.STRING, s.substring(start, i)));
                continue;
            }
            if (Character.isDigit(c) || (c == '-' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int start = i;
                if (s.charAt(i) == '-') i++;
                while (i < n && Character.isDigit(s.charAt(i))) i++;
                if (i < n && s.charAt(i) == '.') {
                    i++;
                    while (i < n && Character.isDigit(s.charAt(i))) i++;
                }
                out.add(new Token(Kind.NUMBER, s.substring(start, i)));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '-')) i++;
                out.add(new Token(Kind.IDENT, s.substring(start, i)));
                continue;
            }
            out.add(new Token(Kind.IDENT, String.valueOf(c)));
            i++;
        }
        out.add(new Token(Kind.EOF, ""));
        return out;
    }

    /* ---------- emitter ---------- */

    private static String emit(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean atBol = true;
        Token prev = new Token(Kind.EOF, "");

        for (int idx = 0; idx < tokens.size() - 1; idx++) {
            Token tok  = tokens.get(idx);
            Token next = tokens.get(idx + 1);

            switch (tok.kind()) {
                case NEWLINE -> {
                    sb.append('\n');
                    atBol = true;
                }
                case COMMENT -> {
                    if (atBol) sb.append("  ".repeat(indent));
                    else       sb.append("  ");   // <-- FIX: due spazi prima del commento inline
                    sb.append(tok.text());
                    atBol = false;
                }
                case LBRACE -> {
                    if (!atBol) sb.append(' ');
                    sb.append('{');
                    atBol = false;
                    indent++;
                    if (next.kind() != Kind.NEWLINE && next.kind() != Kind.RBRACE
                            && next.kind() != Kind.EOF) {
                        sb.append('\n');
                        atBol = true;
                    }
                }
                case RBRACE -> {
                    if (indent > 0) indent--;
                    if (!atBol && prev.kind() != Kind.LBRACE) {
                        sb.append('\n');
                        atBol = true;
                    }
                    if (atBol) sb.append("  ".repeat(indent));
                    sb.append('}');
                    atBol = false;
                    if (next.kind() != Kind.NEWLINE && next.kind() != Kind.RBRACE
                            && next.kind() != Kind.EOF && next.kind() != Kind.COMMA
                            && next.kind() != Kind.RBRACKET) {
                        sb.append('\n');
                        atBol = true;
                    }
                }
                default -> {
                    if (atBol) {
                        sb.append("  ".repeat(indent));
                        atBol = false;
                    } else if (needsSpace(prev, tok)) {
                        sb.append(' ');
                    }
                    sb.append(tok.text());
                }
            }
            prev = tok;
        }
        if (!atBol && sb.length() > 0) sb.append('\n');
        return sb.toString();
    }

    private static boolean needsSpace(Token prev, Token tok) {
        if (prev.kind() == Kind.EQUALS) return true;
        if (tok.kind()  == Kind.EQUALS) return true;
        if (prev.kind() == Kind.COMMA)  return true;
        if (tok.kind()  == Kind.COMMA || tok.kind() == Kind.RBRACKET) return false;
        if (prev.kind() == Kind.LBRACKET) return false;
        if (tok.kind()  == Kind.LBRACE)   return true;
        return isWord(prev.kind()) && isWord(tok.kind());
    }

    private static boolean isWord(Kind k) {
        return k == Kind.IDENT || k == Kind.STRING || k == Kind.NUMBER;
    }

    /* ---------- alignment ---------- */

    private static String alignEquals(String input) {
        String[] lines = input.split("\n", -1);
        int n = lines.length;

        int i = 0;
        while (i < n) {
            if (!isAttributeLine(lines[i])) { i++; continue; }
            int start = i;
            int maxKeyLen = 0;
            int indent = indentOf(lines[i]);
            while (i < n && isAttributeLine(lines[i]) && indentOf(lines[i]) == indent) {
                maxKeyLen = Math.max(maxKeyLen, keyLength(lines[i]));
                i++;
            }
            for (int k = start; k < i; k++) {
                lines[k] = realign(lines[k], maxKeyLen);
            }
        }
        return String.join("\n", lines);
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static boolean isAttributeLine(String line) {
        String t = line.stripLeading();
        if (t.isEmpty() || t.startsWith("#") || t.startsWith("//") || t.startsWith("/*")) return false;
        int eq = t.indexOf('=');
        if (eq <= 0) return false;
        // FIX: extract the key with trailing spaces stripped, then validate it
        String key = t.substring(0, eq).stripTrailing();
        if (key.isEmpty()) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) return false;
        }
        return eq + 1 < t.length() && t.charAt(eq + 1) != '=';
    }

    private static int keyLength(String line) {
        String t = line.stripLeading();
        int eq = t.indexOf('=');
        // FIX: return the length of the key, not the position of '='
        return t.substring(0, eq).stripTrailing().length();
    }

    private static String realign(String line, int maxKeyLen) {
        String t = line.stripLeading();
        int eq = t.indexOf('=');
        String key   = t.substring(0, eq).stripTrailing();
        String value = t.substring(eq + 1).stripLeading();
        int indent   = indentOf(line);
        return " ".repeat(indent) + key + " ".repeat(maxKeyLen - key.length())
                + " = " + value;
    }
}
