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

To generate the user manual and see your changes, run:

    ./gradlew :docs:userguide
    
This will generate:

 - A multi-page HTML manual in `build/working/usermanual/render-multi/` for each chapter. There is a 1-1 mapping from `.adoc` file to `.html` file.
 - A single-page HTML manual at `build/working/usermanual/render-single-html/userguide_single.html`
 - A PDF at `build/working/usermanual/render-single-pdf/userguide_single.pdf`

Note that PNG files in the source are generated from ".graphml" files in the same directory.  You can edit these files
with tools like [yEd](http://www.yworks.com/en/products_yed_about.html) and then generate the associated PNG.

If you just need to see a change to one of the userguide sections, try:

    ./gradlew :docs:userguide -x :docs:userguideSinglePageHtml -x :docs:userguideSinglePagePdf

### Authoring with AsciiDoc

To write a chapter in Asciidoctor format, simply place it in `src/docs/userguide` called `<chapter>.adoc`.

You will find these references useful when authoring AsciiDoc:

 - [AsciiDoc Syntax Quick Reference](https://asciidoctor.org/docs/asciidoc-syntax-quick-reference/)
 - [Asciidoctor User Manual](https://asciidoctor.org/docs/user-manual/)
 - [Asciidoctor Gradle Plugin Reference](https://asciidoctor.org/docs/asciidoctor-gradle-plugin/)

### Using Asciidoctor

To write a chapter in Asciidoctor format, simply place it in `src/docs/userguide` called `<chapter>.adoc`.

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
include::sample[dir="snippets/initScripts/customLogger/groovy",files="init.gradle[]"]

include::sample[dir="snippets/initScripts/customLogger/kotlin",files="customLogger.init.gradle.kts[]"]
====

[.multi-language-text.lang-groovy]
----
$ gradle -I init.gradle build
include::{snippetsPath}/initScripts/customLogger/tests/customLogger.out[]
----
[.multi-language-text.lang-kotlin]
----
$ gradle -I customLogger.init.gradle.kts build
include::{snippetsPath}/initScripts/customLogger/tests/customLogger.out[]
----
```

Let's break down this example to explain:

* Enclosing `====` around the sample includes groups these samples and collapses them 
* `include::sample`: invokes the `SampleIncludeProcessor` asciidoctor extension, with a `dir` relative to `src/snippets/`, and a list of `files` separated by `;` (only 1 in this example), each with optional `tags=...` (like Asciidoctor's tags mechanism). We write this once for each DSL dialect. This notes to our front-end code to group these 2 samples and show them with selector tabs.
* `[.multi-language-text.lang-groovy]`: Most times the gradle command is identical between Groovy and Kotlin samples, but in this case we need to use `[.multi-language-text.lang-*]` that our CSS will collapse and switch for the DSL of choice. This is case-sensitive. You can use this construct for any 2 sibling blocks!

It is possible to embed sample sources, commands, and expected output directly in the Asciidoc (or a mixture of embedded and `include`d), but we don't use this for the user manual yet. See the [Exemplar documentation](https://github.com/gradle/exemplar/#configuring-embedded-samples) if you're interested in this.

### Code Samples

Samples and output belong under `src/samples` and are published beside the user manual. See the `org.gradle.samples` plugin.

To run the samples tests:
```
./gradlew :docs:docsTest --tests "org.gradle.docs.samples.*.*"
```

To run tests for a single sample, let's say from `samples/java/application`:
```
./gradlew :docs:docsTest --tests "org.gradle.docs.samples.DependencyManagementSnippetsTest.java-application*"
```

To run tests for a single snippet:

Let's say you want to run the snippet found at `src/snippets/dependencyManagement/customizingResolution-consistentResolution`.

then you can run the following command line:

```
   ./gradlew :docs:docsTest --tests "*.snippet-dependency-management-customizing-resolution-consistent-resolution*"
```

which would run both Groovy and Kotlin tests.

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
