# Snippets Guide

A practical reference for creating and maintaining tested code snippets in the Gradle documentation.
See also: the [README.md](README.md) for the broader documentation contributor guide.

## Directory Structure

Every snippet lives under `src/snippets/` and follows this layout:

```
src/snippets/<category>/<snippetName>/
├── common/                    # Files shared between Groovy and Kotlin DSLs
│   └── gradle/
│       └── libs.versions.toml # Version catalogs, properties, Java sources, etc.
├── groovy/
│   ├── build.gradle
│   └── settings.gradle
├── kotlin/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
└── tests/
    ├── <testName>.sample.conf # Exemplar test configuration (HOCON format)
    └── <testName>.out         # Expected output (optional)
```

| Directory | Contains                                                                                                   |
|-----------|------------------------------------------------------------------------------------------------------------|
| `common/` | Files shared between both DSLs: `gradle.properties`, version catalogs (`.toml`), Java sources, XML configs |
| `groovy/` | Groovy DSL files (`.gradle`)                                                                               |
| `kotlin/` | Kotlin DSL files (`.gradle.kts`)                                                                           |
| `tests/`  | `.sample.conf` test configs and `.out` expected output files                                               |

Both Groovy **and** Kotlin DSL variants are always required.

## Tagging Regions

Use tags to include only specific parts of a file in the documentation.

**Kotlin / Groovy / Java:**
```kotlin
// tag::my_tag[]
dependencies {
    implementation(libs.groovy.core)
}
// end::my_tag[]
```

**TOML (version catalogs):**
```toml
# // tag::my_tag[]
[libraries]
groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
# // end::my_tag[]
```

> **Pitfall:** Plain `//` is not a valid TOML comment. Always prefix with `#` to make it `# // tag::name[]`.

Tags can be nested, and multiple tags can exist in the same file.

## Referencing Snippets in AsciiDoc

Use `include::sample[]` inside an `====` block, one entry per DSL:

```asciidoc
====
include::sample[dir="snippets/dependencyManagement/catalogs-gradleApis/kotlin",files="build.gradle.kts[tags=use_catalog_entries]"]
include::sample[dir="snippets/dependencyManagement/catalogs-gradleApis/groovy",files="build.gradle[tags=use_catalog_entries]"]
====
```

For shared files like TOML catalogs, reference the `common/` directory:

```asciidoc
====
include::sample[dir="snippets/dependencyManagement/catalogs-kotlinVersionSharing/common",files="gradle/catalog.versions.toml[tags=kotlin_catalog]"]
====
```

## Inline Code (Discouraged)

Only use inline code if the snippet is genuinely untestable and 3 lines or fewer.
Always provide both DSL variants using `[.multi-language-sample]` blocks:

```asciidoc
====
[.multi-language-sample]
=====
[source,kotlin]
----
create("libs") {
    from("com.mycompany:catalog:1.0")
}
----
=====
[.multi-language-sample]
=====
[source,groovy]
----
libs {
    from("com.mycompany:catalog:1.0")
}
----
=====
====
```

## Test Configuration (.sample.conf)

Test configs use HOCON format and are run by the Exemplar test framework.

### Minimal (just validate compilation)

```hocon
executable: gradle
args: tasks
```

### With expected output

```hocon
executable: gradle
args: hello
expected-output-file: antHello.out
```

### With CLI flags

```hocon
# tag::cli[]
# gradle --quiet hello
# end::cli[]
executable: gradle
args: hello
flags: --quiet
expected-output-file: customPlugin.out
```

The `# tag::cli[]` block can be referenced from `.adoc` files to show the command line invocation.

### Expected failure

```hocon
executable: gradle
args: broken
flags: --quiet
expect-failure: true
expected-output-file: taskFailure.out
allow-additional-output: true
```

### Multiple commands

```hocon
commands: [{
    execution-subdirectory: task
    executable: gradle
    args: check publish
}, {
    execution-subdirectory: consumer
    executable: gradle
    args: greeting
}]
```

### System properties

```hocon
executable: gradle
args: "-DsomeProperty=value someTask"
```

### Configuration attributes reference

| Attribute                 | Description                                                         |
|---------------------------|---------------------------------------------------------------------|
| `executable`              | Command to run (typically `gradle`)                                 |
| `args`                    | Task names and arguments                                            |
| `flags`                   | Additional CLI flags (`--quiet`, `--build-cache`, `-I init.gradle`) |
| `expected-output-file`    | Path to `.out` file for output verification                         |
| `allow-additional-output` | Allow extra output beyond what's in the `.out` file                 |
| `expect-failure`          | Expect the build to fail                                            |
| `execution-subdirectory`  | Subdirectory within the snippet to run from                         |
| `commands`                | Array of command objects for multi-step tests                       |

## Running Tests

Run a specific snippet test:

```bash
./gradlew :docs:docsTest --tests "*.snippet-name_*"
```

The test name is derived from the `.sample.conf` filename. For example, `tests/gradleApis.sample.conf` becomes `*.snippet-gradleApis_*`.

## Groovy vs Kotlin DSL Differences

When writing both variants, watch for these common differences:

| Kotlin DSL                             | Groovy DSL                                            |
|----------------------------------------|-------------------------------------------------------|
| `create("libs") { }`                   | `libs { }`                                            |
| `listOf("a", "b")`                     | `["a", "b"]`                                          |
| `requested is ModuleComponentSelector` | `requested instanceof ModuleComponentSelector`        |
| `val x = something`                    | `def x = something`                                   |
| String templates: `"${var}"`           | String interpolation: `"${var}"` (with double quotes) |
| Single quotes not valid for strings    | Single quotes for non-interpolated strings: `'text'`  |

## Common Pitfalls

### Version catalog auto-import conflicts

Gradle automatically imports `gradle/libs.versions.toml` as the `libs` catalog.
If your snippet uses `create("libs") { from(files("gradle/libs.versions.toml")) }`, this triggers a "you can only call the 'from' method a single time" error because Gradle already called `from()` during auto-import.

**Fix:** Name the TOML file something other than `libs.versions.toml` (e.g., `catalog.versions.toml`) to prevent auto-import.

### Kotlin smart cast limitations

Kotlin cannot smart-cast properties from interfaces (the getter could return different values on each call). If you need to check and use a property:

```kotlin
// Won't compile - 'requested' is an interface property
if (requested is ModuleComponentSelector && requested.group == "org.example") { ... }

// Fix: assign to a local val first
val componentSelector = requested
if (componentSelector is ModuleComponentSelector && componentSelector.group == "org.example") { ... }
```

### TOML tag comment syntax

TOML only supports `#` comments. The tag syntax `// tag::name[]` must be wrapped:

```toml
# Wrong - invalid TOML
// tag::my_tag[]

# Correct
# // tag::my_tag[]
```

### Unresolved version references in TOML

If a TOML file references a version (e.g., `version.ref = "kotlin"`) that is meant to be injected programmatically via `version("kotlin", "2.3.20")` in settings, the version must **not** appear in a `[versions]` block.
Gradle validates the TOML file independently of the programmatic additions, so missing `version.ref` targets will cause errors during auto-import but work correctly when loaded via `from(files(...))`.

This is another reason to avoid auto-import by naming the file something other than `libs.versions.toml`.
