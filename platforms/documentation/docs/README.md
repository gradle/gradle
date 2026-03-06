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

**Source file:** `src/docs/release/notes.md`

### Heading Schema

| Heading | Behavior |
|---|---|
| `h2` | Listed in the generated TOC |
| `h3` | Listed in TOC; content after the first element is collapsible until next `h3`/`h2` |
| `h4` | Content is collapsible until the next `h4`, `h3`, or `h2` |

To mark a feature as **incubating**, append `(i)` to the `h3` text:

```markdown
## New and Noteworthy

### Some Feature (i)

This feature is incubating.

#### Some Detail

This detail is collapsed by default.
```

### Generating Release Notes

```bash
./gradlew :docs:releaseNotes
```

---

## User Manual

**Source:** `src/docs/userguide/` — authored in [Asciidoctor](https://asciidoctor.org)

### Build Commands

| Goal | Command |
|---|---|
| Full preview (recommended) | `./gradlew stageDocs` |
| Full preview with continuous rebuild | `./gradlew stageDocs -t` |
| Fast iteration (no DSL ref, no single-page) | `./gradlew stageDocs -PquickDocs` |
| Fast iteration with continuous rebuild | `./gradlew stageDocs -PquickDocs -t` |
| Live reload at `http://localhost:8000` | `./gradlew serveDocs -PquickDocs` |
| User manual only (links may break) | `./gradlew :docs:userguide` |
| Multi-page HTML manual only | `./gradlew :docs:userguideMultiPage` |
| Single-page HTML manual only | `./gradlew :docs:userguideSinglePageHtml` |
| Javadoc only | `./gradlew :docs:javadocAll` |
| Run all snippet and sample tests | `./gradlew :docs:docsTest` |

The `-PquickDocs` flag skips slow tasks (DSL reference, single-page manual). Rebuild time in quick mode is approximately 30–40 seconds.

**Output locations:**
- Multi-page HTML: `build/working/usermanual/render-multi/` (one `.html` per `.adoc`)
- Single-page HTML: `build/working/usermanual/render-single-html/userguide_single.html`
- All staged docs: `build/docs/`

### AsciiDoc References

- [Syntax Quick Reference](https://asciidoctor.org/docs/asciidoc-syntax-quick-reference/)
- [Asciidoctor User Manual](https://asciidoctor.org/docs/user-manual/)
- [Asciidoctor Gradle Plugin Reference](https://asciidoctor.org/docs/asciidoctor-gradle-plugin/)

### Linking to DSL and API References

Whenever you reference a Gradle API class, method, or annotation in prose, link it to the relevant reference documentation. Three path attributes are available:

| Attribute | Points to |
|---|---|
| `{javadocPath}` | Javadoc (use for Java API classes and annotations) |
| `{groovyDslPath}` | Groovy DSL reference |
| `{kotlinDslPath}` | Kotlin DSL reference |

```asciidoc
link:{javadocPath}/org/gradle/process/CommandLineArgumentProvider.html[`CommandLineArgumentProvider`]
link:{javadocPath}/org/gradle/api/tasks/CacheableTask.html[`@CacheableTask`]
link:{groovyDslPath}/org.gradle.api.tasks.javadoc.Groovydoc.html[Groovydoc]
link:{groovyDslPath}/org.gradle.api.Project.html#org.gradle.api.Project:afterEvaluate(org.gradle.api.Action)[`Project.afterEvaluate()`]
link:{kotlinDslPath}/gradle/org.gradle.api.tasks/-task-container/index.html[register()]
link:{kotlinDslPath}/gradle/org.gradle.api/-project/get-project-dir.html[`Project.projectDir`]
```

Always wrap link text in backticks for any code identifier — classes, methods, properties, and annotations alike. This follows the Microsoft Style Guide convention that all code elements use code style.

### Images

Images live in `docs/src/userguide/img/`. Accepted formats are SVG, PNG, and JPEG. Keep file sizes small — avoid large uncompressed images.

To embed an image in an `.adoc` file:

```asciidoc
image::performance/performance-1.png[]
```

The path is relative to the `src/docs/img/` directory.

### Anchors

Every heading should have an anchor declared on the line immediately above it. This enables direct linking from other pages. Anchor IDs should use `snake_case`.

| Heading level | Anchor required? |
|---|---|
| `=` (page title) | Required |
| `==` (section) | Required |
| `===` (subsection) | Recommended |
| `====` (sub-subsection) | Recommended |

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

### Adding a New Chapter — Checklist

1. Create `<chapter>.adoc` in an appropriate subdirectory of `src/docs/userguide/`.
2. Add the license header and a chapter ID at the top. The ID should "closely" match the filename:
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

**Source:** `src/snippets/` — typically included in the user manual via `include::sample`.

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

| Directory | Contains                                                                    |
|-----------|-----------------------------------------------------------------------------|
| `groovy/` | Groovy DSL source files (`.gradle`)                                         |
| `kotlin/` | Kotlin DSL source files (`.gradle.kts`)                                     |
| `common/` | Files shared between both DSLs (e.g. `gradle.properties`, version catalogs) |
| `tests/`  | Testing instructions and expected outputs (e.g. `*.out`, `*sample.conf`)    |

**What must NOT be in these directories:**
- Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/`)
- Build output directories (`build/`)
- Any other Gradle infrastructure files

The only exceptions are `gradle.properties` and version catalog files (e.g. `libs.versions.toml`), which are allowed.

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

Or it can use a mix:

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
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("<path-to>/convention-plugins/build/repo")
        }
    }
}
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
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri('<path-to>/convention-plugins/build/repo')
        }
    }
}
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

The `docs:docsTest` task covers three sources of code:

1. **Generated samples** — Output of `gradle init` (via `generate-samples.gradle.kts`).
2. **Code samples** — Located in `src/samples/`. Published alongside the user manual.
3. **Code snippets** — Located in `src/snippets/`. Included inline in the user manual.

> **Terminology note:** "Sample" is overloaded. In this codebase, "code samples" refers to `src/samples/`, while "exemplar sample" refers to the unit of testing used by the [Exemplar framework](https://github.com/gradle/exemplar/).

### Running Specific Tests

```bash
# All sample tests
./gradlew :docs:docsTest --tests "org.gradle.docs.samples.*.*"

# A single sample (e.g., java/application)
./gradlew :docs:docsTest --tests "org.gradle.docs.samples.DependencyManagementSnippetsTest.java-application*"

# A specific snippet (both DSLs)
./gradlew :docs:docsTest --tests "*.snippet-java-toolchain-task_*"

# A specific snippet, Kotlin DSL only
./gradlew :docs:docsTest --tests "*.snippet-java-toolchain-task_kotlin_*"

# snippets/buildlifecycle/flowAction/
./gradlew :docs:docsTest --tests "*.snippet-buildlifecycle-flow-action_*"

# snippets/buildlifecycle/buildServices/
./gradlew :docs:docsTest --tests "*.snippet-buildlifecycle-build-service_*"
```

### Testing with Configuration Cache

```bash
./gradlew :docs:docsTest --tests "*.snippet-java-toolchain-task_*" -PenableConfigurationCacheForDocsTests=true
```

You can also set `enableConfigurationCacheForDocsTests=true` in `gradle.properties`.

---

## Style Guides

All documentation contributions must follow these style guides:

- **User Manual** (`.adoc` files): Follow the [Microsoft Writing Style Guide](https://learn.microsoft.com/en-us/style-guide/welcome/).
- **Javadoc**: Follow the [Gradle Javadoc Style Guide](https://github.com/gradle/gradle/blob/master/contributing/JavadocStyleGuide.md).

---

## Groovy DSL Reference

**Source:** `src/docs/dsl/` — authored in Docbook syntax. Much content is extracted from code doc comments.

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

```bash
./gradlew :docs:javadocAll
```

**Output:** `build/javadoc/`

---

## Build All Docs

```bash
./gradlew :docs:docs
```
