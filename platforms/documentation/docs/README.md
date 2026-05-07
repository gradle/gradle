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

### Checking for Broken Links

Always run the following after making changes to ensure no internal links are broken:

```bash
./gradlew :docs:checkDeadInternalLinks
```

---

## Code Snippets and Testing Docs

The `docs:docsTest` task tests **code snippets** located in `src/snippets/`. Snippets are included inline in the user manual and are the standard way to add tested code examples.

To fully understand how to write and test code snippets in the Gradle documentation, see @platforms/documentation/docs/src/docs/rules/snippets.md

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

---

## Style Guides

All documentation contributions must follow these style guides:

- **User Manual** (`.adoc` files): Follow the [Microsoft Writing Style Guide](https://learn.microsoft.com/en-us/style-guide/welcome/).
- **Javadoc**: Follow the [Gradle Javadoc Style Guide](https://github.com/gradle/gradle/blob/master/contributing/JavadocStyleGuide.md).

Write one sentence per line in `.adoc` and `.md` files; this makes diffs cleaner and PRs easier to review.

Always leave a blank line after any heading (`=`, `==`, `===`, `====`) before the body text begins. This is required by AsciiDoc for the heading to be parsed correctly, and it keeps the source readable.

```asciidoc
=== Explanation

The root project defines the overall structure of a composite build.
```

Not:

```asciidoc
=== Explanation
The root project defines the overall structure of a composite build.
```

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
