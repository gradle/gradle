# Snippets Guide

Rules and patterns for creating tested code snippets in the Gradle documentation. This is the authoritative reference for AI agents working on documentation snippets.

## Quick Reference (Rules)

- Both **Groovy and Kotlin DSL** variants are always required.
- Shared files (TOML catalogs, `.properties`, Java sources) go in `common/`.
- Code >5 lines in `.adoc` **must** use `include::sample[]` from `/snippets`. This applies primarily to **Groovy, Kotlin, and Java** source blocks.
- Inline code in `.adoc` is only for genuinely untestable snippets of 5 lines or fewer.
- Blocks using `.xml`, `.toml`, `.text`, `.bash`, `.json`, `.properties`, and similar non-DSL languages **typically do not need to be snippetized** — these are usually configuration samples, command output, directory trees, or illustrative text that cannot be meaningfully tested.
- **`upgrading_*.adoc` files are exempt** from snippet requirements. Upgrading guides frequently show deprecated patterns, removed APIs, and "before" code that intentionally won't compile — snippetizing them is impractical and would produce failing tests.
- **All `dsl-apis/*.adoc` files are exempt** from snippet requirements (for example, `kotlin_dsl.adoc` and `public_apis.adoc`). These are language-specific files that explain the different DSLs — snippetizing them is impractical and would produce failing tests.
- **`glossary*.adoc` file is exempt** from snippet requirements. This is a generic glossary — snippetizing this is impractical and would produce failing tests.
- Every `.sample.conf` should have a `.out` file — a test without output verification only proves the build doesn't crash.
- The `dir` path in `include::sample[]` is relative to `src/snippets/`.
- Before renaming or restructuring a snippet, search for `@UsesSample` and `new Sample` references in integration tests.

## Directory Structure

Every snippet lives under `src/snippets/` and follows this layout:

```
src/snippets/<category>/<snippetName>/
├── common/                    # Shared between Groovy and Kotlin DSLs
│   └── gradle/
│       └── libs.versions.toml
├── groovy/
│   ├── build.gradle
│   └── settings.gradle
├── kotlin/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
└── tests/
    ├── <testName>.sample.conf # Exemplar test configuration (HOCON format)
    └── <testName>.out         # Expected output for verification
```

| Directory | Contains                                                                                                   |
|-----------|------------------------------------------------------------------------------------------------------------|
| `common/` | Files shared between both DSLs: `gradle.properties`, version catalogs (`.toml`), Java sources, XML configs |
| `groovy/` | Groovy DSL files (`.gradle`)                                                                               |
| `kotlin/` | Kotlin DSL files (`.gradle.kts`)                                                                           |
| `tests/`  | `.sample.conf` test configs and `.out` expected output files                                               |

## Tag Syntax

Use `// tag::name[]` / `// end::name[]` to mark regions in Kotlin, Groovy, and Java files. Tags can be nested, and multiple tags can exist in the same file. The tag name must match what is referenced in the `include::sample[]` directive.

```kotlin
// tag::my_tag[]
dependencies {
    implementation(libs.groovy.core)
}
// end::my_tag[]
```

**TOML is the exception** — TOML only supports `#` comments, so tags must be wrapped:

```toml
# // tag::my_tag[]
[libraries]
groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
# // end::my_tag[]
```

> Plain `// tag::name[]` in a TOML file is a syntax error. Always prefix with `#`.

## Including Snippets in AsciiDoc

### Option 1: `include::sample[]` (preferred)

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

### Option 2: Inline code (discouraged)

Only use if genuinely untestable and **5 lines or fewer**. Always provide both DSL variants using `[.multi-language-sample]` blocks:

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

Multiple files within a variant can be shown by stacking titled source blocks inside the same `=====` block:

```asciidoc
====
[.multi-language-sample]
=====
.settings.gradle.kts
[source, kotlin]
----
rootProject.name = "test"
----
.build.gradle.kts
[source, kotlin]
----
plugins {
    id("com.myorg.service-conventions") version "1.0"
}
----
=====
[.multi-language-sample]
=====
.settings.gradle
[source, groovy]
----
rootProject.name = 'test'
----
.build.gradle
[source, groovy]
----
plugins {
    id 'com.myorg.service-conventions' version '1.0'
}
----
=====
====
```

Use `[.multi-language-sample]` blocks for directory structures where only file extensions differ:

```asciidoc
====
[.multi-language-sample]
=====
.Project structure
[source, kotlin]
----
├── library-a
│   └── build.gradle.kts
└── settings.gradle.kts
----
=====
[.multi-language-sample]
=====
.Project structure
[source, groovy]
----
├── library-a
│   └── build.gradle
└── settings.gradle
----
=====
====
```

### Language-conditional text

Use `[.multi-language-text.lang-*]` blocks when prose needs to change based on the reader's DSL selection. This is distinct from `[.multi-language-sample]` which is for code blocks.

```asciidoc
====
[.multi-language-text.lang-kotlin]
=====
Create a new file called `SlackTask.kt` in `src/main/kotlin/org/example/`:
=====
[.multi-language-text.lang-groovy]
=====
Create a new file called `SlackTask.groovy` in `src/main/groovy/org/example/`:
=====
====
```

### Callouts

Callout annotations (e.g., `<1>`, `<2>`) must appear on the line immediately after the closing `====` block of the snippet they reference. Do not insert blank lines or other content between the code block and its callouts.

```asciidoc
====
include::sample[dir="snippets/best-practices/noSourceInRoot-avoid/kotlin",files="build.gradle.kts[tags=avoid-this]"]
include::sample[dir="snippets/best-practices/noSourceInRoot-avoid/groovy",files="build.gradle[tags=avoid-this]"]
====
<1> The `java-library` plugin is applied to the root project.
```

Not:

```asciidoc
====
include::sample[dir="snippets/best-practices/noSourceInRoot-avoid/kotlin",files="build.gradle.kts[tags=avoid-this]"]
include::sample[dir="snippets/best-practices/noSourceInRoot-avoid/groovy",files="build.gradle[tags=avoid-this]"]
====

Some text in between.

<1> The `java-library` plugin is applied to the root project.
```

### Source blocks in AsciiDoc

Any code in an `.adoc` file must use AsciiDoc `[source,<language>]` blocks with `----` delimiters. **Never use Markdown-style fenced code blocks** (`` ```kotlin ``, `` ```groovy ``, `` ```bash ``, etc.) in `.adoc` files — they are not valid AsciiDoc and will not render correctly.

Common language identifiers: `bash`, `kotlin`, `groovy`, `java`, `properties`, `text`, `xml`, `toml`. Use `subs="attributes"` when the block contains AsciiDoc attribute placeholders like `{gradleVersion}`.

Do this:

```asciidoc
[source,kotlin]
----
plugins {
    id("java-library")
}
----
```

Not this:

````asciidoc
```kotlin
plugins {
    id("java-library")
}
```
````

To show a task invocation with output:

```asciidoc
[source,bash]
----
$ ./gradlew processTemplates
----
[source,text]
----
include::{snippetsPath}/tasks/incrementalBuild-customTaskClass/tests/customTaskClassWithInputAnnotations.out[]
----
```

## Test Configuration (`.sample.conf`)

Test configs use [HOCON format](https://github.com/lightbend/config/blob/master/HOCON.md) and are run by the [Exemplar](https://github.com/gradle/exemplar) test framework via a custom JUnit Platform test engine (`SamplesTestEngine`). For each snippet, Exemplar assembles the `groovy/`, `kotlin/`, and `common/` subdirectories into a temporary working directory, runs the declared commands, and compares output against the `.out` file.

### Examples

**With output verification (recommended):**
```hocon
executable: gradle
args: "dependencies --configuration compileClasspath"
expected-output-file: mySnippet.out
allow-additional-output: true
allow-disordered-output: true
```

**With CLI flags:**
```hocon
executable: gradle
args: hello
flags: --quiet
expected-output-file: customPlugin.out
```

The `# tag::cli[]` block in a `.sample.conf` can be referenced from `.adoc` files to show command invocations:
```hocon
# tag::cli[]
# gradle --quiet hello
# end::cli[]
executable: gradle
args: hello
flags: --quiet
expected-output-file: customPlugin.out
```

**Expected failure:**
```hocon
executable: gradle
args: broken
flags: --quiet
expect-failure: true
expected-output-file: taskFailure.out
allow-additional-output: true
```

**Multi-command snippet** (commands run in order, each with its own expected output):
```hocon
commands: [{
    executable: gradle
    args: consumer
    expected-output-file: uniqueOutputs.1.out
}, {
    executable: gradle
    args: greeterB
    expected-output-file: uniqueOutputs.2.out
}]
```

**System properties:**
```hocon
executable: gradle
args: "-DsomeProperty=value someTask"
```

**Subdirectory execution:**
```hocon
execution-subdirectory: consumer
executable: gradle
args: "dependencies --configuration compileClasspath"
```

### Configuration attributes

| Field                     | Required              | Description                                                                     |
|---------------------------|-----------------------|---------------------------------------------------------------------------------|
| `executable`              | Yes (or `commands`)   | The executable to invoke, typically `gradle`                                    |
| `args`                    | No                    | Arguments passed to the executable                                              |
| `flags`                   | No                    | CLI flags, separated from args (`--quiet`, `--build-cache`, `-I init.gradle`)   |
| `expected-output-file`    | No                    | Relative path to `.out` file. If omitted, output is not verified                |
| `allow-additional-output` | No                    | Allow extra lines in actual output beyond the `.out` file. Default: `false`     |
| `allow-disordered-output` | No                    | Allow expected lines to appear in any order in actual output. Default: `false`  |
| `expect-failure`          | No                    | Expect the build to fail. Default: `false`                                      |
| `execution-subdirectory`  | No                    | Subdirectory within the snippet to run from                                     |
| `commands`                | Yes (or `executable`) | Array of command objects for multi-step tests, each with their own fields above |

## Expected Output (`.out` Files)

Every snippet test should include a `.out` file. A test without output verification only proves the build doesn't crash — it doesn't confirm the catalog, dependency, or plugin actually works.

### Verification modes

The Exemplar verifier does **exact line matching** — each expected line must appear verbatim in the actual output.

- **Strict (default)**: Expected lines must appear consecutively in order. `allow-additional-output: true` only permits extra lines *after* the expected block, not between expected lines.
- **Disordered** (`allow-disordered-output: true`): Each expected line must appear *somewhere* in the output, in any position. Combined with `allow-additional-output: true`, this is the most flexible mode.

### Best practices

- **Always use both flags** (`allow-additional-output: true` + `allow-disordered-output: true`) for dependency tree output — transitive dependencies insert unpredictable lines between expected entries.
- Include only the lines you care about verifying — typically direct dependencies, not transitives.
- `BUILD SUCCESSFUL` does not match `BUILD SUCCESSFUL in 1s` — verify task names instead (e.g., `> Task :publish`).
- The `java-library` plugin outputs `Compile classpath for source set 'main'.` while the Kotlin JVM plugin outputs `Compile classpath for 'main'.` — match the actual output for your snippet.
- For publish tasks, verify key task names like `> Task :generateCatalogAsToml` rather than `BUILD SUCCESSFUL`.

### Example `.out` file

For a snippet that declares `groovy:3.0.6` and `error_prone_core:2.28.0`:

```
compileClasspath - Compile classpath for source set 'main'.
+--- org.codehaus.groovy:groovy:3.0.6
\--- com.google.errorprone:error_prone_core:2.28.0
```

For a publish task:

```
> Task :generateCatalogAsToml
> Task :publish
```

## Running Tests

```bash
# All snippets
./gradlew :docs:docsTest

# A specific snippet (both DSLs)
./gradlew :docs:docsTest --tests '*catalogs-gradle-apis*'

# Kotlin DSL only
./gradlew :docs:docsTest --tests '*catalogs-gradle-apis_kotlin_*'
```

The snippet folder path maps to a test filter by replacing `/` with `-` and dropping the `snippets/` prefix:
`snippets/dependencyManagement/catalogs-gradleApis/` becomes `*catalogs-gradle-apis*`

### Testing with configuration cache

```bash
./gradlew :docs:docsTest --tests '*snippet-name*' -PenableConfigurationCacheForDocsTests=true
```

Or set `enableConfigurationCacheForDocsTests=true` in the root `gradle.properties`.

## Groovy vs Kotlin DSL Differences

| Kotlin DSL                             | Groovy DSL                                                   |
|----------------------------------------|--------------------------------------------------------------|
| `create("libs") { }`                   | `libs { }`                                                   |
| `listOf("a", "b")`                     | `["a", "b"]`                                                 |
| `requested is ModuleComponentSelector` | `requested instanceof ModuleComponentSelector`               |
| `val x = something`                    | `def x = something`                                          |
| String templates: `"${var}"`           | String interpolation: `"${var}"` (double quotes) or GStrings |
| Single quotes not valid for strings    | Single quotes for non-interpolated strings: `'text'`         |

## Common Pitfalls

### Version catalog auto-import conflicts

Gradle automatically imports `gradle/libs.versions.toml` as the `libs` catalog. If your snippet uses `create("libs") { from(files("gradle/libs.versions.toml")) }`, this triggers "you can only call the 'from' method a single time" because Gradle already called `from()` during auto-import.

**Fix:** Name the TOML file something other than `libs.versions.toml` (e.g., `catalog.versions.toml`).

### Unresolved version references in TOML

If a TOML file uses `version.ref = "kotlin"` but the version is injected programmatically via `version("kotlin", "2.3.20")` in settings, the `[versions]` block must **not** define `kotlin`. Gradle validates TOML files independently — missing refs cause errors during auto-import but work correctly when loaded via `from(files(...))`.

This is another reason to avoid auto-import with non-standard filenames.

### Kotlin smart cast limitations

Kotlin cannot smart-cast interface properties (the getter could return different values). Assign to a local `val` first:

```kotlin
// Won't compile — 'requested' is an interface property
if (requested is ModuleComponentSelector && requested.group == "org.example") { ... }

// Fix: assign to a local val
val selector = requested
if (selector is ModuleComponentSelector && selector.group == "org.example") { ... }
```

### Snippets used in integration tests

Some snippets are also used as fixtures in Gradle's integration tests via `@UsesSample` or `@Rule Sample` in the `internal-integ-testing` module. Most are found at `platforms/documentation/samples/src/integTest/`.

Before renaming or restructuring a snippet, search for references:

```bash
# Search for @UsesSample references
grep -r '@UsesSample("dependencyManagement/' platforms/

# Search for Sample rule references
grep -r 'new Sample' platforms/documentation/samples/src/integTest/
```

If you find any, coordinate with the engineering team before making changes.
