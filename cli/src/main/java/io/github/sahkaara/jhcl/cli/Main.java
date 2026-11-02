package io.github.sahkaara.jhcl.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.sahkaara.jhcl.core.Diagnostic;
import io.github.sahkaara.jhcl.core.Formatter;
import io.github.sahkaara.jhcl.core.HclDocument;
import io.github.sahkaara.jhcl.core.Jhcl;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Entry point for the jhcl CLI.
 *
 * <p>Three subcommands mirror the core API:
 * <ul>
 *   <li>{@code fmt}    — format an HCL file (stdout, in-place, or check mode)</li>
 *   <li>{@code lint}   — run static checks and report diagnostics</li>
 *   <li>{@code to-json}— convert to JSON for tooling integration</li>
 * </ul>
 *
 * <p>Exit codes: 0 success, 1 I/O error, 2 analysis error.
 */
@Command(
        name = "jhcl",
        mixinStandardHelpOptions = true,
        version = "jhcl 0.1.0",
        description = "HCL toolkit for the JVM: formatter, linter, JSON converter.",
        subcommands = {
                Main.FmtCommand.class,
                Main.LintCommand.class,
                Main.ToJsonCommand.class
        }
)
public final class Main implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 1;
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new Main()).execute(args);
        System.exit(exit);
    }

    /* ---------- fmt ---------- */

    @Command(
            name = "fmt",
            mixinStandardHelpOptions = true,
            description = "Format an HCL file (stdout, -w in-place, or --check for CI)."
    )
    static final class FmtCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "HCL file to format")
        Path file;

        @Option(names = {"-w", "--write"},
                description = "Rewrite the file in place instead of printing to stdout")
        boolean inPlace;

        @Option(names = "--check",
                description = "Exit 1 if the file is not canonically formatted (no output written)")
        boolean check;

        @Override
        public Integer call() throws IOException {
            String source = Files.readString(file);
            String formatted = Formatter.format(source);

            if (check) {
                if (formatted.equals(source)) return 0;
                System.out.println(file);
                return 1;
            }

            if (inPlace) {
                Files.writeString(file, formatted);
            } else {
                System.out.print(formatted);
            }
            return 0;
        }
    }

    /* ---------- lint ---------- */

    @Command(
            name = "lint",
            mixinStandardHelpOptions = true,
            description = "Run static checks on an HCL file."
    )
    static final class LintCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "HCL file to lint")
        Path file;

        @Override
        public Integer call() throws IOException {
            String source = Files.readString(file);
            HclDocument doc = Jhcl.parse(source);

            if (doc.hasErrors()) {
                for (Diagnostic d : doc.errors()) {
                    System.err.printf("%s:%d:%d: %s: %s [%s]%n",
                            file, d.line(), d.column(),
                            d.severity().name().toLowerCase(),
                            d.message(), d.code());
                }
                return 2;
            }

            List<Diagnostic> diags = Jhcl.lint(doc, source);
            PrintWriter err = new PrintWriter(System.err, true);
            boolean anyError = false;
            for (Diagnostic d : diags) {
                err.printf("%s:%d:%d: %s: %s [%s]%n",
                        file, d.line(), d.column(),
                        d.severity().name().toLowerCase(),
                        d.message(), d.code());
                if (d.severity() == Diagnostic.Severity.ERROR) anyError = true;
            }
            return anyError ? 2 : 0;
        }
    }

    /* ---------- to-json ---------- */

    @Command(
            name = "to-json",
            mixinStandardHelpOptions = true,
            description = "Convert an HCL file to JSON on stdout."
    )
    static final class ToJsonCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "HCL file to convert")
        Path file;

        @Option(names = {"--pretty"},
                description = "Pretty-print the JSON output")
        boolean pretty;

        @Override
        public Integer call() throws IOException {
            String source = Files.readString(file);
            HclDocument doc = Jhcl.parse(source);

            if (doc.hasErrors()) {
                for (Diagnostic d : doc.errors()) {
                    System.err.printf("%s:%d:%d: error: %s [%s]%n",
                            file, d.line(), d.column(), d.message(), d.code());
                }
                return 2;
            }

            ObjectMapper mapper = new ObjectMapper();
            if (pretty) mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String json = mapper.writeValueAsString(doc);
            System.out.println(json);
            return 0;
        }
    }
}
