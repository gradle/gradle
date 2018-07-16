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

Run the `viewReleaseNotes` task to generate the release notes, and open them in your browser (requires Java 6).

### Editing

You can run the `editReleaseNotes` task to open the raw markdown notes in whatever editor is registered for this file type (requires Java 6).

## User Manual

The source for the user manual lives @ `src/docs/userguide`, and is authored in [Asciidoctor](https://asciidoctor.org).

To generate the user manual and see your changes, run:

    ./gradlew :docs:userguide
    
This will generate:

 - A multi-page HTML manual in `build/docs/userguide` for each chapter. There is a 1-1 mapping from `.adoc` file to `.html` file.
 - A single-page HTML manual at `build/docs/userguide/userguide_single.html`
 - A PDF at `build/docs/userguide/userguide.pdf`

Note that PNG files in the source are generated from ".graphml" files in the same directory.  You can edit these files
with tools like [yEd](http://www.yworks.com/en/products_yed_about.html) and then generate the associated PNG.

### Authoring with AsciiDoc

To write a chapter in Asciidoctor format, simply place it in `src/docs/userguide` called `<chapter>.adoc`.

You will find these references useful when authoring AsciiDoc:

 - [AsciiDoc Syntax Quick Reference](https://asciidoctor.org/docs/asciidoc-syntax-quick-reference/)
 - [Asciidoctor User Manual](https://asciidoctor.org/docs/user-manual/)
 - [Asciidoctor Gradle Plugin Reference](https://asciidoctor.org/docs/asciidoctor-gradle-plugin/)
 
### Linking to References



### Code Samples

Samples and output belong under `src/samples` and are typically included in the user manual. This is a typical example:

```asciidoc
[source.multi-language-sample,groovy]             
.init.gradle
----
include::{samples-path}/userguide/initScripts/customLogger/init.gradle[]
----
[source.multi-language-sample,kotlin]             
.init.gradle.kts
----
include::{samples-path}/userguide/initScripts/customLoggerKts/init.gradle.kts[]
----

.Output of **`gradle -I init.gradle build`**
----
> gradle -I init.gradle build
include::{samples-path}/userguide/initScripts/customLogger/custom_logging_ui.out[]
----
```

Notice that the Groovy and Kotlin samples are declared with `[source.multi-language-sample,<language>]`. 
This notes to our front-end code to group these 2 samples and show them with selector tabs. 
We intend to implement embedded samples so that the sample sources are in the user manual itself. 

### Using Asciidoctor

To write a chapter in Asciidoctor format, simply place it in `src/docs/userguide` called `<chapter>.adoc`.
=======
```asciidoc
// tag::something[]
interesting code
// end::something[]

some other code
```

They should be included using the `tag` or `tags` attribute this way:

```asciidoc
.build.gradle
----
include::{samples-path}/foo/build.gradle[tag=something]
----
```

## Groovy DSL Reference

The DSL reference is authored in Docbook syntax, with sources under `src/docs/dsl`. 
Much of the content is extracted from code doc comments.

To build it, run:

```bash
./gradlew :docs:dslHtml
```

The output is available under `build/docs/dsl`.

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

    ./gradlew :docs:javadoc

The output is available within `build/docs/javadoc`.

## Building all the docs

There is a convenience task to build all of the documentation:

    ./gradlew :docs:docs
