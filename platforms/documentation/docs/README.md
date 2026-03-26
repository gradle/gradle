# Gradle Documentation Contributor Guide

> **Purpose:** This guide is for contributors writing or editing documentation for the [gradle/gradle](https://github.com/gradle/gradle) repository. It covers structure, authoring conventions, code samples, and testing.
>
> **AI Usage:** This document is structured for both human contributors and AI assistants (e.g., Copilot, Cursor, Claude). It is intended to be ingested as a system prompt or context file to guide AI-assisted documentation contributions. All rules and conventions stated here should be treated as authoritative and followed strictly when generating or editing `.adoc` files, code snippets, or any other documentation artifacts in this repository.

---

## Overview

The `docs` project produces:
- [Release Notes](http://gradle.org/docs/current/release-notes)
- [User Manual](http://gradle.org/docs/current/userguide/userguide.html)
- [DSL Reference](http://gradle.org/docs/current/dsl/)
- [Javadoc](http://gradle.org/docs/current/javadoc/)

All file paths in this guide are relative to the `docs` project directory unless stated otherwise.

---

## Release Notes

**Source file:** `src/docs/release/notes.md` - authored in [Markdown](https://www.markdownguide.org/)

Follow the instructions in [notes.md](https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/src/docs/release/notes.md).

### Generating Release Notes

```bash
./gradlew :docs:releaseNotes
```

### Markdown References

- [Markdown Cheat Sheet](https://www.markdownguide.org/cheat-sheet/)

---

## User Manual

**Source:** `src/docs/userguide/` — authored in [Asciidoctor](https://asciidoctor.org)

### Build Commands

| Goal                                   | Command                                   |
|----------------------------------------|-------------------------------------------|
| Full preview (recommended)             | `./gradlew stageDocs`                     |
| Full preview with continuous rebuild   | `./gradlew stageDocs -t`                  |
| Full preview with fast iteration       | `./gradlew stageDocs -PquickDocs`         |
| Live reload at `http://localhost:8000` | `./gradlew serveDocs`                     |
| User manual only (links may break)     | `./gradlew :docs:userguide`               |
| Multi-page HTML manual only            | `./gradlew :docs:userguideMultiPage`      |
| Single-page HTML manual only           | `./gradlew :docs:userguideSinglePageHtml` |
| Javadoc only                           | `./gradlew :docs:javadocAll`              |
| Run all snippet and sample tests       | `./gradlew :docs:docsTest`                |

The `-PquickDocs` flag skips slow tasks (DSL reference, single-page manual). Rebuild time in quick mode is approximately 30–40 seconds.
The `t` and `-PquickDocs` flags can be used by the `serveDocs` task as well.

**Output locations:**
- Multi-page HTML: `build/working/usermanual/render-multi/` (one `.html` per `.adoc`)
- Single-page HTML: `build/working/usermanual/render-single-html/userguide_single.html`
- All staged docs: `build/docs/`

### AsciiDoc References

- [Syntax Quick Reference](https://asciidoctor.org/docs/asciidoc-syntax-quick-reference/)
- [Asciidoctor User Manual](https://asciidoctor.org/docs/user-manual/)
- [Asciidoctor Gradle Plugin Reference](https://asciidoctor.org/docs/asciidoctor-gradle-plugin/)

### Cross-References and Linking

Good cross-references help readers navigate, but too many become disruptive. 

Follow these guidelines:

- **Be selective.** Every link adds cognitive load and risks pulling readers away from the page. Only include links that are genuinely useful.
- **Provide context on the page.** When a term, concept, or brief set of steps can be explained in a few sentences, do so rather than linking out.
- **Avoid duplicate links.** Link to the same destination only once per page, unless sections are far apart or serve different entry points (e.g., a procedure and a troubleshooting section).
- **Link to the most relevant destination.** Target the most specific relevant page or heading. Don't provide multiple links that serve the same purpose.

**For third-party content**, avoid links unless absolutely necessary. If a brief explanation covers what readers need, provide it on the page. Reserve **third-party links** for cases where the full external resource is genuinely required.

### Linking to DSL and API References

Whenever you reference a Gradle API class, method, or annotation in prose, link it to the relevant reference documentation. Three path attributes are available:

| Attribute         | Points to                                          |
|-------------------|----------------------------------------------------|
| `{javadocPath}`   | Javadoc (use for Java API classes and annotations) |
| `{groovyDslPath}` | Groovy DSL reference                               |
| `{kotlinDslPath}` | Kotlin DSL reference                               |

```asciidoc
link:{javadocPath}/org/gradle/process/CommandLineArgumentProvider.html[`CommandLineArgumentProvider`]
link:{javadocPath}/org/gradle/api/tasks/CacheableTask.html[`@CacheableTask`]
link:{groovyDslPath}/org.gradle.api.tasks.javadoc.Groovydoc.html[`Groovydoc`]
link:{groovyDslPath}/org.gradle.api.Project.html#org.gradle.api.Project:afterEvaluate(org.gradle.api.Action)[`Project.afterEvaluate()`]
link:{kotlinDslPath}/gradle/org.gradle.api.tasks/-task-container/index.html[`register()`]
link:{kotlinDslPath}/gradle/org.gradle.api/-project/get-project-dir.html[`Project.projectDir`]
```

Always wrap link text in backticks for any code identifier — classes, methods, properties, and annotations alike.

### Images

Images live in `docs/src/userguide/img/`. Formats include GIF, GRAPHML, SVG, PNG, and JPEG. Smaller size files are preferred.

To embed an image in an `.adoc` file:

```asciidoc
image::performance/performance-1.png[]
```

The path is relative to the `src/docs/img/` directory.

Do not submit images as part of a PR to `gradle/gradle`. All images must be created and approved by the Gradle documentation team.

### Anchors

Every heading should have an anchor declared on the line immediately above it. This enables direct linking from other pages. Anchor IDs should use `snake_case`.

| Heading level           | Anchor required? |
|-------------------------|------------------|
| `=` (page title)        | Required         |
| `==` (section)          | Required         |
| `===` (subsection)      | As needed        |
| `====` (sub-subsection) | As needed        |

```asciidoc
[[incremental_build]]
= Incremental Build

[[sec:task_inputs_outputs]]
== Task Inputs and Outputs
```

To link to an anchor from another page:

```asciidoc
<<incremental_build.adoc#incremental_build,incremental build>>
<<incremental_build.adoc#sec:task_inputs_outputs,defined outputs>>
```

To link to an anchor on the **same page**, omit the filename:

```asciidoc
<<sec:task_inputs_outputs,defined outputs>>
<<#sec:task_inputs_outputs,defined outputs>>
```

### Renaming or Deleting a Chapter

When an `.adoc` file is renamed or deleted, you **must** add a redirect entry to the `/redirect` folder so that existing links to the old page continue to work.

### Adding a New Page

1. Create `<page-name>.adoc` in an appropriate subdirectory of `src/docs/userguide/`.
2. Add the license header at the top of every new `.adoc` file. See the [Gradle Contributing Guide](https://github.com/gradle/gradle/blob/master/CONTRIBUTING.md) for the exact license text to use.
   ```asciidoc
   [[toolchains]]
   = Toolchains for JVM Projects
   ```
3. Add the file to [`src/docs/userguide/userguide_single.adoc`](src/docs/userguide/userguide_single.adoc).
    ```asciidoc
    <<toolchains.adoc#toolchains,Toolchains for JVM Projects>>
    ```
4. Add a relative link to the chapter in [`src/main/resources/header.html`](src/main/resources/header.html).
    ```html
    <li><a href="../userguide/toolchains.html">Toolchains for JVM projects</a></li>
    ```

---

### Checking for Broken Links

Always run the following after making changes to ensure no internal links are broken:

```bash
./gradlew :docs:checkDeadInternalLinks
```

## Code Snippets

**Source:** `src/snippets/` — typically included in the user manual via `include::sample`

### Directory Structure Convention

Every snippet **should be written in both Groovy and Kotlin DSL**. If any files are shared between the two variants, place them in a `common/` directory. Tests are placed in the `tests/` directory.

```
src/snippets/
└── buildlifecycle/buildServices/
    ├── common/                   # Files shared between Groovy and Kotlin
    │   └── shared-config.properties
    ├── groovy/
    │   ├── build.gradle
    │   └── settings.gradle
    ├── kotlin/
    │   ├── build.gradle.kts
    │   └── settings.gradle.kts
    └── tests/
        ├── buildServices.out         # Shared expected output
        └── buildServices.sample.conf # Exemplar config (covers both DSLs)
```

**What belongs in each directory:**

| Directory  | Contains                                                                                                             |
|------------|----------------------------------------------------------------------------------------------------------------------|
| `groovy/`  | Groovy DSL source files (`.gradle`)                                                                                  |
| `kotlin/`  | Kotlin DSL source files (`.gradle.kts`)                                                                              |
| `common/`  | Files shared between both DSLs (e.g. `gradle.properties`, version catalogs, `xml` files, java source sets and tests) |
| `tests/`   | Testing instructions and expected outputs (e.g. `*.out`, `*.sample.conf`)                                            |

The only exceptions are `gradle.properties` and version catalog files (e.g. `libs.versions.toml`), which are allowed.

### Tagging Regions in Snippet Files

When you only want to include part of a source file in the docs (using `tags=...` in `include::sample[]`), wrap the relevant code in tag comments in the source file.

```kotlin
// tag::some-tag[]
abstract class MyTask : DefaultTask() {
    @TaskAction
    fun run() {
        logger.lifecycle(heavyWork()) // <1>
    }

    fun heavyWork(): String {
        logger.lifecycle("Start heavy work")
        Thread.sleep(5000)
        logger.lifecycle("Finish heavy work")
        return "Heavy computation result"
    }
}

tasks.register<MyTask>("myTask")
// end::some-tag[]
```

Use the same syntax for all languages with `//` comments. The tag name must match what is referenced in the `include::sample[]` directive:

```asciidoc
include::sample[dir="snippets/mySnippet/kotlin",files="build.gradle.kts[tags=some-tag]"]
```

You can define multiple tagged regions in the same file using different tag names. Regions can also be nested.

### Adding Code Blocks in AsciiDoc

Any code in an `.adoc` file must use a `[source,<language>]` block rather than raw code fences. Optionally add a title (filename or path) on the line before the `----` delimiter.

```asciidoc
[source,bash,subs="attributes"]
----
$ gradle wrapper --gradle-version {gradleVersion} --distribution-type all
----

[source,text]
----
include::{snippetsPath}/wrapper/simple/tests/wrapperCommandLine.out[]
----

[source,properties]
----
systemProp.gradle.wrapperUser=username
systemProp.gradle.wrapperPassword=password
----

[source,properties]
.gradle.properties
----
org.gradle.daemon=true
----

[source,java]
.buildSrc/src/main/java/org/example/ProcessTemplates.java
----
include::{snippetsPath}/tasks/incrementalBuild-customTaskClass/groovy/buildSrc/src/main/java/org/example/ProcessTemplates.java[tag=custom-task-class]
----
```

To show a Gradle task invocation alongside its expected output, pair a `bash` block with a `text` block that includes the corresponding `.out` file from the snippet's `tests/` directory:

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

The output can also use text only:

```asciidoc
[source,bash]
----
$ ./gradlew build
----
[source,text]
----
BUILD SUCCESSFUL
----
```

Or a mix of both:

```asciidoc
[source,bash]
----
$ ./gradlew processTemplates
----
[source,text]
----
include::{snippetsPath}/tasks/incrementalBuild-customTaskClass/tests/customTaskClassWithInputAnnotations.out[]
BUILD SUCCESSFUL
----
```

Common language identifiers: `bash`, `kotlin`, `groovy`, `java`, `properties`, `text`, `xml`, `toml`.
Use `subs="attributes"` when the block contains AsciiDoc attribute placeholders like `{gradleVersion}`.

### Including Code in AsciiDoc

There are two ways to include code in a `.adoc` file.

#### Option 1: Reference a tested snippet (preferred)

Always prefer this approach — it ensures the code is tested. Use `include::sample[]` inside an `====` block, with one entry per DSL:

```asciidoc
====
include::sample[dir="snippets/bestPractices/modularizeYourBuild-do/kotlin/app",files="build.gradle.kts[tags=do-this]"]
include::sample[dir="snippets/bestPractices/modularizeYourBuild-do/groovy/app",files="build.gradle[tags=do-this]"]
====
```

- The `dir` path is relative to `src/snippets/`.
- Use `tags=...` to include only a tagged region of the file (follows standard Asciidoctor tag syntax).
- Any code snippet longer than two lines in an `.adoc` file **must** use this format and be located in `/snippets`.

#### Option 2: Inline code (discouraged)

Only use inline code if the snippet is genuinely not testable, and keep it to **3 lines or less**. The most common legitimate use of `[.multi-language-sample]` is showcasing directory structures, where the only difference between variants is the file extensions (`.gradle` vs `.gradle.kts`):

```asciidoc
====
[.multi-language-sample]
=====
[source, kotlin]
----
abstract class SamplePlugin : Plugin<Project> {
}
----
=====
[.multi-language-sample]
=====
[source,groovy]
----
class SamplePlugin implements Plugin<Project> {
}
----
=====
====
```

Both Groovy and Kotlin variants must still be provided using `[.multi-language-sample]` blocks. Multiple files within a variant can be shown by stacking titled source blocks inside the same `=====` block:

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

It is preferred to use `[.multi-language-sample]` blocks to demonstrate directory structures:

```asciidoc
====
[.multi-language-sample]
=====
.Project structure
[source, kotlin]
----
├── internal-module
│   └── build.gradle.kts
├── library-a
│   ├── build.gradle.kts
│   └── README.md
├── library-b
│   ├── build.gradle.kts
│   └── README.md
└── settings.gradle.kts
----
=====
[.multi-language-sample]
=====
.Project structure
[source, groovy]
----
├── internal-module
│   └── build.gradle
├── library-a
│   ├── build.gradle
│   └── README.md
├── library-b
│   ├── build.gradle
│   └── README.md
└── settings.gradle
----
=====
====
```` 

#### Language-conditional text

If surrounding prose needs to change based on the reader's DSL selection (e.g., referencing a different filename or package path), use `[.multi-language-text.lang-*]` blocks:

```asciidoc
====
[.multi-language-text.lang-kotlin]
=====
Create a new file called `SlackTask.kt` in `src/main/kotlin/org/example/` and add the following code:
=====
[.multi-language-text.lang-groovy]
=====
Create a new file called `SlackTask.groovy` in `src/main/groovy/org/example/` and add the following code:
=====
====
```

Note the distinction from `[.multi-language-sample]`: use `multi-language-text` for **prose that varies by DSL**, and `multi-language-sample` for **code blocks or directory structures**.

## Testing Docs

### What Gets Tested

The `docs:docsTest` task tests **code snippets** located in `src/snippets/`. Snippets are included inline in the user manual and are the standard way to add tested code examples.

### `org.gradle.samples` plugin

The main build file for documentation, `platforms/documentation/docs/build.gradle.kts`, applies the `org.gradle.samples` plugin.

The source code of this plugin is [here](https://github.com/gradle/guides/blob/ba018cec535d90f75876bfcca29381d213a956cc/subprojects/gradle-guides-plugin/src/main/java/org/gradle/docs/samples/internal/LegacySamplesDocumentationPlugin.java#L9).
This plugin adds a [`Samples`](https://github.com/gradle/guides/blob/fa335417efb5656e202e95759ebf8a4e60843f10/subprojects/gradle-guides-plugin/src/main/java/org/gradle/docs/samples/Samples.java#L8) extension named `samples`.

This `samples` extension is configured in `platforms/documentation/docs/build.gradle.kts`. All snippets are auto-discovered and assembled into [`samples.publishedSamples`](https://github.com/gradle/guides/blob/fa335417efb5656e202e95759ebf8a4e60843f10/subprojects/gradle-guides-plugin/src/main/java/org/gradle/docs/samples/Samples.java#L41), as follows:

```
┌────────────────────────────────────┐
│ documentation/docs/build.gradle.kts│
│                                    │
│  samples {                         │    ┌─────────────────────────────────┐
│    ...                             │    │ code snippets in src/snippets   ├───┐
│    publishedSamples {  ────────────┼───►│                                 │   │
│      ...                           │    └─────────────────────────────────┘   │
└────────────────────────────────────┘                                          │
                                                                                │
                                        ┌───────────────────────────────────┐   │
                                        │ org.gradle.samples plugin         │   │
                                        │ ┌─────────────────────────────┐   │   │
┌─────────────┐   Install samples to    │ │ Samples.publishedSamples    │   │   │
│  Exemplar   │   local directory and   │ │                             │   │   │
│             │   test with exemplar    │ │                             │   │   │
│             │◄────────────────────────┤ │                             ◄───┼───┘
│             │                         │ │                             │   │
└─────────────┘                         │ │                             │   │
                                        │ └─────────────────────────────┘   │
                                        │                                   │
                                        └───────────────────────────────────┘
```

The elements in `samples.publishedSamples` container are installed into a local directory (by default [`docs/build/working/samples/install`](https://github.com/gradle/guides/blob/900650c6fd6c980ae7335d7aab6dea200a693aa0/subprojects/gradle-guides-plugin/src/main/java/org/gradle/docs/samples/internal/SamplesInternal.java#L46)) as Exemplar samples.

### Testing Snippets with Exemplar

[Exemplar](https://github.com/gradle/exemplar) discovers, executes, and verifies the output of each snippet by looking for `*.sample.conf` files inside the snippet's `tests/` directory.

Under the hood, a custom JUnit Platform test engine ([`SamplesTestEngine`](https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/src/docsTest/java/org/gradle/docs/samples/SamplesTestEngine.java)) handles discovery and execution. For each snippet, it assembles the `groovy/`, `kotlin/`, and `common/` subdirectories into a temporary working directory, then runs the Gradle commands declared in the `.sample.conf` file using Gradle's standard integration test infrastructure. The actual output is then compared against the corresponding `.out` file (if provided) — if they don't match, the test fails.
While a `.out` file is technically optional, it is **highly** recommended that every `.sample.conf` file be accompanied by a `.out` file.

Use the specific test commands below when testing a snippet locally during development.

### Configuring a snippet — `.sample.conf`

Every snippet's `tests/` directory must contain a `.sample.conf` file in [HOCON format](https://github.com/lightbend/config/blob/master/HOCON.md) that tells Exemplar how to run it.

**Basic single-command snippet:**
```
executable: gradle
args: copyLibs
```

**Single command with output verification and flags:**
```
executable: gradle
args: ":build-logic:check task1 task2"
flags: "--warning-mode=all"
expected-output-file: modularizeYourBuild-avoid.out
allow-additional-output: true
allow-disordered-output: true
```

**Single command with dependency insight args:**
```
executable: gradle
args: dependencyInsight --configuration runtimeClasspath --dependency "org:doesntexist:1.0.0"
```

**Multi-command snippet** (commands run in order, each with its own expected output):
```
commands: [{
    executable: gradle
    args: consumer
    expected-output-file: uniqueOutputs.1.out
},
{
    executable: gradle
    args: greeterB
    expected-output-file: uniqueOutputs.2.out
},
{
    executable: gradle
    args: consumer
    expected-output-file: uniqueOutputs.3.out
}]
```

The key fields are:

| Field                     | Required              | Description                                                                                         |
|---------------------------|-----------------------|-----------------------------------------------------------------------------------------------------|
| `executable`              | Yes (or `commands`)   | The executable to invoke, typically `gradle`                                                        |
| `args`                    | No                    | Arguments passed to the executable                                                                  |
| `flags`                   | No                    | CLI flags, separated from args for tools that require a specific order                              |
| `expected-output-file`    | No                    | Relative path to a `.out` file to compare actual output against. If omitted, output is not verified |
| `allow-additional-output` | No                    | Allow extra lines in actual output. Default: `false`                                                |
| `allow-disordered-output` | No                    | Allow output lines to appear in any order. Default: `false`                                         |
| `commands`                | Yes (or `executable`) | Array of commands to run in sequence, each with their own fields above                              |

### Expected output — `.out` files

A `.out` file is a plain text snapshot of the expected console output for a snippet command. Exemplar compares the actual output of running the snippet against this file to verify correctness.

A typical `.out` file looks like this:

```
> Task :myTask
Some result

BUILD SUCCESSFUL
```

The content should exactly match what Gradle prints to the console when the snippet runs successfully. If `allow-additional-output: true` is set in the `.sample.conf`, Exemplar will accept output that contains extra lines beyond what's in the `.out` file. If `allow-disordered-output: true` is set, the lines can appear in any order.

### Running Specific Tests

```bash
# All snippets
./gradlew :docs:docsTest

# A specific snippet (both DSLs)
./gradlew :docs:docsTest --tests "*.snippet-java-toolchain-task_*"

# A specific snippet, Kotlin DSL only
./gradlew :docs:docsTest --tests "*.snippet-java-toolchain-task_kotlin_*"

# snippets/buildlifecycle/flowAction/
./gradlew :docs:docsTest --tests "*.snippet-buildlifecycle-flow-action_*"

# snippets/buildlifecycle/buildServices/
./gradlew :docs:docsTest --tests "*.snippet-buildlifecycle-build-service_*"
```

The snippet folder path maps to a test filter string by replacing `/` separators with `-` and dropping the `snippets/` prefix — e.g. `snippets/buildlifecycle/flowAction/` becomes `snippet-buildlifecycle-flow-action_*`.

### Testing with Configuration Cache

```bash
./gradlew :docs:docsTest --tests "*.snippet-java-toolchain-task_*" -PenableConfigurationCacheForDocsTests=true
```

You can also set `enableConfigurationCacheForDocsTests=true` in the `gradle.properties` file in the root of the `gradle/gradle` repository.

### Snippets Used in Integration Tests

Some snippets in `src/snippets/` are also used as fixtures in Gradle's integration tests via the `@UsesSample`  or `@Rule Sample` annotation in the `internal-integ-testing` module. This means that renaming, moving, or modifying a snippet can break integration tests that you may not be aware of.

Most, if not all, tests that use documentation snippets can be found at `platforms/documentation/samples/src/integTest/`.

Before renaming or restructuring a snippet, search the codebase for any references to it via `@UsesSample`. For example:

```java
@UsesSample("java/application")
```

Or search for `new Sample`. For example:

```java
@Rule Sample sample = new Sample(temporaryFolder, 'java/application')
    
@Rule
Sample sampleProvider = new Sample(testDirectoryProvider, sampleName)
```

If you find any, coordinate with the engineering team before making changes.

---

## Style Guides

All documentation contributions must follow these style guides:

- **User Manual** (`.adoc` files): Follow the [Microsoft Writing Style Guide](https://learn.microsoft.com/en-us/style-guide/welcome/).
- **Javadoc**: Follow the [Gradle Javadoc Style Guide](https://github.com/gradle/gradle/blob/master/contributing/JavadocStyleGuide.md).

---

## Groovy DSL Reference

**Source:** `src/docs/dsl/` — authored in Docbook syntax. Much content is extracted from code doc comments

### Build

```bash
./gradlew :docs:dslHtml
```

**Output:** `build/working/dsl/`

### Useful Custom Tags

**`<apilink>`** — Links to the DSL reference or Javadoc for a class or method.

Link to a class:

```xml
You can use the <apilink class='org.gradle.api.Project' /> interface to do stuff.
```

Link to a method:

```xml
<apilink class='org.gradle.api.Project' method="apply(java.util.Map)" />
```

For the full list of standard Docbook tags, see the [Docbook reference](http://docbook.org/tdg/en/html/part2.html).

---

## Javadoc

**Source:** `gradle/*` — javadoc can be found in many java files in the Gradle codebase


```bash
./gradlew :docs:javadocAll
```

**Output:** `build/javadoc/`

---

## Build All Docs

```bash
./gradlew :docs:docs
```
