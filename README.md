# jhcl

**A modern HCL toolkit for the JVM: typed API, formatter, linter, and Spring Boot integration — built on top of hcl4j.**

[![CI](https://github.com/Sahkaara/jhcl/actions/workflows/ci.yml/badge.svg)](https://github.com/Sahkaara/jhcl/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-blue.svg)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-not%20yet-orange.svg)](#)

`jhcl` is a **toolkit** for working with [HCL](https://github.com/hashicorp/hcl) (HashiCorp Configuration Language) from the JVM. It provides:

- a **typed API** over `hcl4j` (records and sealed interfaces instead of `Map<String,Object>`);
- a **formatter** (`jhcl fmt`) with `=` alignment, à la `terraform fmt`;
- a **linter** (`jhcl lint`) with diagnostics in GNU format (`file:line:column`);
- a **JSON converter** (`jhcl to-json`) for tooling integration;
- **Spring Boot** auto-configuration for loading HCL into the application context.

It requires **JDK 25** (LTS), has **one runtime dependency** (`hcl4j` + its `slf4j-api`), and ships a **single runnable jar**.

---

## Table of contents

- [Why jhcl?](#why-jhcl)
- [Quick start](#quick-start)
- [CLI usage](#cli-usage)
- [Use as a library](#use-as-a-library)
- [Spring Boot integration](#spring-boot-integration)
- [Architecture](#architecture)
- [Building from source](#building-from-source)
- [Testing](#testing)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Why jhcl?

`hcl4j` is a solid HCL parser, but its output is `Map<String,Object>`. Every consumer ends up writing:

```java
var variable = (Map<String, Object>) root.get("variable");
var region   = (Map<String, Object>) variable.get("region");
var def      = (String) region.get("default");
```

`jhcl` turns that into:

```java
String def = doc.getString("variable.region.default").orElse("eu-west-1");
```

Plus three things `hcl4j` does not provide: a formatter, a linter, and Spring Boot integration. The goal is not to replace `hcl4j` — it's to build the **toolkit** that should sit on top of it.

---

## Quick start

Requires **JDK 25** and **Maven 3.8+**.

```bash
git clone https://github.com/Sahkaara/jhcl.git
cd jhcl
mvn -q clean package -DskipTests
java -jar cli/target/jhcl-cli-0.1.0-SNAPSHOT.jar --version
```

You should see:

```
jhcl 0.1.0
```

---

## CLI usage

```
jhcl <command> [options] <file>

Commands:
  fmt [-w] [--check] <file.hcl>   Format (or check) the file
  lint <file.hcl>                 Validate the syntax and run static checks
  to-json [--pretty] <file.hcl>   Convert HCL to JSON

Options:
  --help, -h      Show help
  --version       Print version
```

Exit codes follow the `gofmt` / `terraform fmt` convention:

| Command | 0 | 1 | 2 |
|---|---|---|---|
| `fmt` | success | I/O error | — |
| `fmt --check` | file is clean | I/O error or file needs formatting | — |
| `lint` | no ERROR diagnostics | I/O error | parse error or ERROR diagnostics |
| `to-json` | success | I/O error | parse error |

### `jhcl fmt`

Re-emits the file with canonical formatting: 2-space indentation, single spaces around `=`, aligned `=` within contiguous runs of attribute lines (mirroring `terraform fmt`).

```bash
# Print to stdout
jhcl fmt examples/sample.hcl

# Rewrite in place
jhcl fmt -w examples/sample.hcl
```

### `jhcl fmt --check`

The CI mode. Behaves like `gofmt -l`:

- exit **0** if the file is already canonically formatted;
- exit **1** and print the filename if formatting would change it.

```bash
find . -name '*.hcl' -print0 | xargs -0 -n1 jhcl fmt --check
```

### `jhcl lint`

Runs static checks on the parsed document and prints diagnostics in GNU format. Silent and exit **0** on success.

```bash
jhcl lint examples/sample.hcl
# examples/sample.hcl:5:1: warning: block 'resource' expects 2 label(s), found 1 [W003]
```

**Implemented rules:**

| Code | Severity | Description |
|---|---|---|
| `W001` | Warning | Duplicate block (same type and labels) |
| `W003` | Warning | Missing or extra labels for well-known block types |
| `I001` | Info | File is not canonically formatted (suggests `jhcl fmt`) |

Rule `W002` (duplicate attribute) is not implemented: `hcl4j` collapses duplicate keys into a `Map` during parsing, so detecting duplicates requires lexical analysis of the source. It is on the roadmap.

### `jhcl to-json`

Serializes the parsed document to JSON for tooling integration.

```bash
jhcl to-json --pretty examples/sample.hcl
```

---

## Use as a library

Add `jhcl-core` as a dependency (once published to Maven Central — for now, install locally with `mvn install` from the project root):

```xml
<dependency>
    <groupId>io.github.sahkaara.jhcl</groupId>
    <artifactId>jhcl-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Then:

```java
import io.github.sahkaara.jhcl.core.*;

HclDocument doc = Jhcl.parse(Path.of("config.hcl"));

if (doc.hasErrors()) {
    doc.errors().forEach(System.err::println);
    return;
}

// Typed accessors (dotted paths)
String region  = doc.getString("variable.region.default").orElse("eu-west-1");
int    count   = doc.getInt   ("resource.aws_instance.web.count").orElse(1);
boolean enable = doc.getBoolean("resource.aws_instance.web.enabled").orElse(true);

// Navigate the AST directly
for (HclBlock block : doc.blocksOfType("resource")) {
    System.out.println(block.type() + " " + block.labels());
}

// Format
String formatted = Jhcl.format(source);

// Lint
List<Diagnostic> diags = Jhcl.lint(doc, source);
```

### Memory and immutability

`HclDocument`, `HclBlock`, and every `HclValue` variant are **immutable records**. All collections are defensively copied in the constructor, so mutating the source `Map` after creation has no effect. This makes them safe to cache, share across threads, and use as map keys.

`HclValue` is a **sealed interface** with six variants — `StringValue`, `NumberValue`, `BoolValue`, `NullValue`, `ListValue`, `ObjectValue`. The compiler enforces exhaustive pattern matching, so adding a new variant is a compile-time breaking change rather than a silent runtime surprise.

---

## Spring Boot integration

Add `jhcl-spring-boot` as a dependency, then set `jhcl.path` in `application.yml`:

```yaml
jhcl:
  path: config/app.hcl
```

The auto-configuration registers an `HclDocument` bean that your services can inject:

```java
@Service
public class ConfigService {
    private final HclDocument config;

    public ConfigService(HclDocument config) {
        this.config = config;
    }

    public String region() {
        return config.getString("variable.region.default").orElse("eu-west-1");
    }
}
```

If your application already provides an `HclDocument` bean, the auto-configuration **backs off** — user-defined beans always win.

---

## Architecture

`jhcl` is a Maven multi-module project:

```
jhcl/
├── core/          # Library: typed model, parser wrapper, formatter, linter
├── cli/           # CLI entry point (picocli), shaded into a runnable jar
└── spring-boot/   # Spring Boot auto-configuration
```

### `core` — the model

```
hcl4j (parser) ──▶ Hcl4jAdapter ──▶ HclDocument / HclBlock / HclValue
                                       │
                                       ├── Formatter  (token-based, source in/out)
                                       └── Linter     (AST + source checks)
```

- **`HclValue`** — a `sealed interface` with six variants: `StringValue`, `NumberValue`, `BoolValue`, `NullValue`, `ListValue`, `ObjectValue`.
- **`HclBlock`** — type, labels, attributes, nested blocks, source position.
- **`HclDocument`** — root attributes, top-level blocks, error diagnostics, dotted-path accessors.
- **`Hcl4jAdapter`** — package-private; converts `hcl4j`'s `Map<String,Object>` into the typed model. Handles multi-label blocks (2+ levels of nested maps), lists, object literals, and hcl4j custom types (`StringPrimitiveType` → `String`).
- **`Formatter`** — token-based (not AST-based), so it works on files with syntax errors. Idempotent: `format(format(x)).equals(format(x))`.
- **`Linter`** — AST-based rules (duplicate blocks, label counts) plus one source-based rule (non-canonical formatting).

### `cli` — picocli-based

Three subcommands (`fmt`, `lint`, `to-json`), shaded into a single runnable jar via `maven-shade-plugin`. The `Main-Class` is set in the shade configuration, so `java -jar jhcl-cli-*.jar` just works.

### `spring-boot` — auto-configuration

Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Spring Boot 3 convention). Activated only when `jhcl.path` is set; backs off if a user-defined `HclDocument` bean exists.

---

## Building from source

```bash
mvn clean package              # build + test all modules
mvn clean package -DskipTests  # build only
mvn test -pl core              # test a single module
```

### Requirements

- **JDK 25** (the `maven.compiler.release` is set to `25`)
- **Maven 3.8+**

To run on JDK 21 instead, change `<maven.compiler.release>` in the parent `pom.xml` to `21` and adjust the CI matrix accordingly.

---

## Testing

**33 unit and integration tests** across the three modules:

| Module | Test class | Tests |
|---|---|---|
| `core` | `HclBlockTest` | 4 |
| `core` | `HclDocumentTest` | 6 |
| `core` | `JhclTest` | 6 |
| `core` | `FormatterTest` | 6 |
| `core` | `LinterTest` | 8 |
| `spring-boot` | `JhclAutoConfigurationTest` | 3 |

Run all:

```bash
mvn test
```

---

## Roadmap

- [x] Typed model (`HclDocument`, `HclBlock`, `HclValue`)
- [x] Parser adapter over `hcl4j`
- [x] Formatter with `=` alignment
- [x] Linter (`W001`, `W003`, `I001`)
- [x] CLI (`fmt`, `lint`, `to-json`)
- [x] Spring Boot auto-configuration
- [ ] Rule `W002`: duplicate attribute (requires lexical analysis of source)
- [ ] Preserve blank lines and comments in `fmt` output
- [ ] `jhcl get <path>` — jq-style query on the AST
- [ ] GraalVM Native Image support
- [ ] Publish to Maven Central
- [ ] GitHub Releases with pre-built CLI jars

See the [issues](https://github.com/Sahkaara/jhcl/issues) for the live roadmap.

---

## Contributing

Contributions are very welcome — bug reports, feature requests, docs, tests, code.

1. **Open an issue first** for non-trivial changes.
2. **Fork, branch, PR.** One feature or fix per PR.
3. **Follow the existing style.** 4-space indent, `record` for data, `sealed interface` for closed hierarchies.
4. **Add a test.** If you're fixing a bug, add a test that fails before your fix.
5. **Run `mvn test` before pushing.**

Good first issues are labeled [`good first issue`](https://github.com/Sahkaara/jhcl/labels/good%20first%20issue).

---

## License

MIT — see [LICENSE](LICENSE).

---

## Acknowledgments

- [`hcl4j`](https://github.com/wondrify/hcl4j) — the HCL parser this project builds upon.
- [`terraform fmt`](https://developer.hashicorp.com/terraform/cli/commands/fmt) and [`gofmt`](https://pkg.go.dev/cmd/gofmt) — the inspiration for `jhcl fmt`.
- [picocli](https://picocli.info/) — the CLI framework.