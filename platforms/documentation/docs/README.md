The docs project produces the [user manual](http://gradle.org/docs/current/userguide/userguide.html), [DSL reference](http://gradle.org/docs/current/dsl/),
[javadoc](http://gradle.org/docs/current/javadoc/) and [release notes](http://gradle.org/docs/current/release-notes)
(as well as some other minor bits).

The following is some help for working with the docs, all file paths are relative to this directory unless specified otherwise.

## Release Notes

The release notes are generated from `src/docs/release/notes.md`.

### Schema

Every `h2` tag and `h3` will be listed in the generated TOC.

After every `h3` all content after the first element (usually a `p`) will be collapsed/expandable, up until the next `h3`, or `h2`.

After every `h4` all content will be collapsed/expandable, up until the next `h4`, `h3` or `h2`.

An `h3` may include an incubating marker `(i)` at the end of its text to indicate that the feature is incubating.

Here's an example:

    ## h2 New and Noteworthy

    ### h3 Some feature (i)

    This is some incubating feature.

    #### h4 Some detail

    This detail about the feature is collapsed. The reader can expand it if they are interested.

### Generating

Run the `:docs:releaseNotes` task to generate the release notes.

## User Manual

The source for the user manual lives @ `src/docs/userguide`, and is authored in [Asciidoctor](https://asciidoctor.org).

To generate the user manual for the final preview and see all changes, you normally want to run:

    ./gradlew stageDocs

That will generate all the docs in the `build/docs` directory.

For development and fast feedback you should use:

    ./gradlew stageDocs -PquickDocs

Alternatively, if you want to serve the docs in a built-in webserver, you can use:

    ./gradlew serveDocs -PquickDocs

The flag `-PquickDocs` disables some slow documentation tasks, like creating the DSL reference or the single page user manual PDF or HTML.

If you really want to generate just the user manual, you can run:

    ./gradlew :docs:userguide

But note that the generated documentation might not be fully functional (e.g. links will not work). This will generate:

 - A multi-page HTML manual in `build/working/usermanual/render-multi/` for each chapter. There is a 1-1 mapping from `.adoc` file to `.html` file.
 - A single-page HTML manual at `build/working/usermanual/render-single-html/userguide_single.html`
 - A PDF at `build/working/usermanual/render-single-pdf/userguide_single.pdf`

Note that PNG files in the source are generated from ".graphml" files in the same directory.  You can edit these files
with tools like [yEd](http://www.yworks.com/en/products_yed_about.html) and then generate the associated PNG.

### Authoring with AsciiDoc

To write a chapter in Asciidoctor format, simply place it in `src/docs/userguide` called `<chapter>.adoc`.

You will find these references useful when authoring AsciiDoc:

 - [AsciiDoc Syntax Quick Reference](https://asciidoctor.org/docs/asciidoc-syntax-quick-reference/)
 - [Asciidoctor User Manual](https://asciidoctor.org/docs/user-manual/)
 - [Asciidoctor Gradle Plugin Reference](https://asciidoctor.org/docs/asciidoctor-gradle-plugin/)

### Adding new chapters

When adding a new chapter to the manual do the following steps:
1. Create a file called `<chapter>.adoc` in a suitable subdirectory of `src/docs/userguide` and write the content there.
2. Add the license text to the top of the file and also add an ID for the chapter title.
This is required to be able to link directly to the chapter from other chapters, as opposed to linking to a section inside.\
The ID should preferably match the name of the `adoc` file. 
For instance, linking to `toolchains.adoc` is possible with `<<toolchains.adoc#toolchains,Text>>`, and the declaration looks like:
    ```asciidoc
    [[toolchains]]
    = Toolchains for JVM projects
    ```
3. Include the new chapter file in the [`userguide_single.adoc`](src/docs/userguide/userguide_single.adoc).
4. Include the relative link to the new chapter in the [`header.html`](src/main/resources/header.html)

### Code Snippets

Snippets and output belong under `src/snippets` and are typically included in the user manual. This is a typical example:

#### Example multi-language sample file listing
This shows Groovy and Kotlin sample projects under "sample-dir" which is defined as "$projectDir/src/snippets".

```
subprojects/docs/src/snippets/
└── initScripts/customLogger/
    ├── customLogger.out
    ├── customLogger.sample.conf
    ├── groovy
    │   ├── build.gradle
    │   ├── init.gradle
    │   └── settings.gradle
    └── kotlin
        ├── build.gradle.kts
        ├── customLogger.init.gradle.kts
        └── settings.gradle.kts
```

Note here that there are 2 sample projects under `initScripts/customLogger/`: one for the Groovy DSL and one for Kotlin DSL. Also note that there is only 1 `customLogger.sample.conf` file that tells Exemplar how to execute both groovy and kotlin samples, with 1 `customLogger.out` file proving the output is identical between the two.

#### Example Asciidoctor multi-language sample declaration

```asciidoc
.Customizing what Gradle logs
====
include::sample[dir="snippets/initScripts/customLogger/kotlin",files="customLogger.init.gradle.kts[]"]
include::sample[dir="snippets/initScripts/customLogger/groovy",files="init.gradle[]"]
====

[.multi-language-text.lang-kotlin]
----
$ gradle -I customLogger.init.gradle.kts build
include::{snippetsPath}/initScripts/customLogger/tests/customLogger.out[]
----
[.multi-language-text.lang-groovy]
----
$ gradle -I init.gradle build
include::{snippetsPath}/initScripts/customLogger/tests/customLogger.out[]
----
```

Let's break down this example to explain:

* Enclosing `====` around the sample includes groups these samples and collapses them
* `include::sample`: invokes the `SampleIncludeProcessor` asciidoctor extension, with a `dir` relative to `src/snippets/`, and a list of `files` separated by `;` (only 1 in this example), each with optional `tags=...` (like Asciidoctor's tags mechanism). We write this once for each DSL dialect. This notes to our front-end code to group these 2 samples and show them with selector tabs.
* `[.multi-language-text.lang-groovy]`: Most times the gradle command is identical between Groovy and Kotlin samples, but in this case we need to use `[.multi-language-text.lang-*]` that our CSS will collapse and switch for the DSL of choice. This is case-sensitive. You can use this construct for any 2 sibling blocks!

It is possible to embed sample sources, commands, and expected output directly in the Asciidoc (or a mixture of embedded and `include`d), but we don't use this for the user manual yet. See the [Exemplar documentation](https://github.com/gradle/exemplar/#configuring-embedded-samples) if you're interested in this.

## Testing docs 

Currently, `docs` is tested by `docs:docsTest`, which covers three kinds of code: 

- The code generated by [Build Init Plugin](https://docs.gradle.org/current/userguide/build_init_plugin.html), i.e. `gradle init` task.
  - `generate-samples.gradle.kts` registers multiple generator tasks that generate the same sample project code as you manually run `gradle init`.
  - These sample projects will also be published beside the user manual.
- The code samples under `subprojects/docs/src/samples`. They are published beside the user manual.
- [The code snippets](#code-snippets) under `subprojects/docs/src/snippets`, which are typically included in the user manual.

Note: the terminology `sample` could refer to different things depending on the context:

- The code samples under `subprojects/docs/src/samples`. We'll call them "code samples."
- The test unit used by [exemplar framework](https://github.com/gradle/exemplar/blob/9c4415f6237d7d86329fdbb32d80a2f7dd8ae0a9/samples-discovery/src/main/java/org/gradle/exemplar/model/Sample.java#L21). We'll call them "exemplar sample".

The build script of the `docs` subproject (`supprojects/docs/build.gradle`) eventually assembles the three kinds of docs code above to exemplar samples and then tests them with exemplar.

### `org.gradle.samples` plugin

`subprojects/docs/build.gradle` applies `generate-samples.gradle.kts`, which further applies an opinionated plugin called `org.gradle.samples`.
The source code of this plugin is [here](https://github.com/gradle/guides/blob/ba018cec535d90f75876bfcca29381d213a956cc/subprojects/gradle-guides-plugin/src/main/java/org/gradle/docs/samples/internal/LegacySamplesDocumentationPlugin.java#L9).
This plugin adds a [`Samples`](https://github.com/gradle/guides/blob/fa335417efb5656e202e95759ebf8a4e60843f10/subprojects/gradle-guides-plugin/src/main/java/org/gradle/docs/samples/Samples.java#L8) extension named `samples`.

This `samples` extension is configured in both `subprojects/docs/build.gradle` and `generate-samples.gradle.kts`:
all docs code to be tested will be assembled into [`samples.publishedSamples`](https://github.com/gradle/guides/blob/fa335417efb5656e202e95759ebf8a4e60843f10/subprojects/gradle-guides-plugin/src/main/java/org/gradle/docs/samples/Samples.java#L41), as follows:

```
This graph is generated by [asciiflow.com](https://asciiflow.com/). You can copy-paste it to the website to modify it. 

┌────────────────────────────────────┐
│ subprojects/docs/build.gradle      │
│ ┌────────────────────────────────┐ │    ┌─────────────────────────────────┐
│ │ generate-samples.gradle.kts    ├─┼───►│ code generated by init tasks    ├───┐
│ │                                │ │    │                                 │   │
│ └────────────────────────────────┘ │    └─────────────────────────────────┘   │
│                                    │                                          │
│  samples {                         │    ┌─────────────────────────────────┐   │
│    ...                             │    │ code snippets in src/snippets   ├───┤
│    publishedSamples {  ────────────┼─┬─►│                                 │   │
│      ...                           │ │  └─────────────────────────────────┘   │
└────────────────────────────────────┘ │                                        │
                                       │  ┌─────────────────────────────────┐   │
                                       └─►│ code samples in src/samples     ├───┤
                                          │                                 │   │
                                          └─────────────────────────────────┘   │
                                                                                │
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

The elements in `samples.publishedSamples` container will later be installed into a local directory (by default [`docs/build/working/samples/install`](https://github.com/gradle/guides/blob/900650c6fd6c980ae7335d7aab6dea200a693aa0/subprojects/gradle-guides-plugin/src/main/java/org/gradle/docs/samples/internal/SamplesInternal.java#L46)) as exemplar samples.

After the exemplar examples are installed, `docs:docsTest` will start testing them (see [`BaseSamplesTest`](https://github.com/gradle/gradle/blob/a503d4a36c53e43a8857da3115fa744612c6ad36/subprojects/docs/src/docsTest/java/org/gradle/docs/samples/BaseSamplesTest.java#L66)).

### Code samples

To run the samples tests:
```
./gradlew :docs:docsTest --tests "org.gradle.docs.samples.*.*"
```

To run tests for a single sample, let's say from `samples/java/application`:
```
./gradlew :docs:docsTest --tests "org.gradle.docs.samples.DependencyManagementSnippetsTest.java-application*"
```

Note that the samples are also used in `samples` subproject, see [`@UsesSample`](https://github.com/gradle/gradle/blob/9ade1a05427aaf04c976a0e85814b44b3435f9f9/subprojects/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/UsesSample.java#L25) and [`Sample`](https://github.com/gradle/gradle/blob/903c5f2cee88c9768077d46025eaafdf65862fc8/subprojects/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/Sample.java#L37).

### Code snippets

As an example, you can run Kotlin and Groovy snippets tests from [`src/snippets/java/toolchain-task/`](src/snippets/java/toolchain-task) using:
```
./gradlew :docs:docsTest --tests "*.snippet-java-toolchain-task_*"
```

You can also filter the tests for a specific DSL like this:
```
./gradlew :docs:docsTest --tests "*.snippet-java-toolchain-task_kotlin_*"
```

### Testing with configuration cache

It is possible to run samples and snippets with the configuration cache enabled to ensure compatibility.
You can do that by setting the Gradle property `enableConfigurationCacheForDocsTests` in the command line or in the `gradle.properties` file.
```
./gradlew :docs:docsTest --tests "*.snippet-java-toolchain-task_*" -PenableConfigurationCacheForDocsTests=true
```

## Groovy DSL Reference

The DSL reference is authored in Docbook syntax, with sources under `src/docs/dsl`.
Much of the content is extracted from code doc comments.

To build it, run:

```bash
./gradlew :docs:dslHtml
```

The output is available under `build/working/dsl`.

### Useful docbook tags

See the [docbook reference](http://docbook.org/tdg/en/html/part2.html) for a list of all available tags.

#### Custom Tags

##### `<apilink>`

This is an inline element which adds a link to the API documentation for a particular class or method.

    You can use the <apilink class='org.gradle.api.Project' /> interface to do stuff.

The link will point to the DSL reference for the specified class, if available. Otherwise, it will point to the javadoc for the class.

To link to a method:

    <apilink class='org.gradle.api.Project' method="apply(java.util.Map)" />

## Javadocs

To build these, run:

    ./gradlew :docs:javadocAll

The output is available within `build/javadoc`.

## Building all the docs

There is a convenience task to build all of the documentation:

    ./gradlew :docs:docs
