The docs project produces the [userguide](http://gradle.org/docs/current/userguide/userguide.html), [DSL reference](http://gradle.org/docs/current/dsl/), [javadoc](http://gradle.org/docs/current/javadoc/) and [groovydoc](http://gradle.org/docs/current/groovydoc/) (as well as some other minor bits).

The following is some help for working with the docs, all file paths are relative to this directory unless specified otherwise.

## Release Notes

The release notes are generated from `src/docs/release/notes.md`.

### Schema 

Every `h2` tag will be listed in the generated TOC.

After every `h3` all content after the first element (usually a `p`) will be collapsed/expandable, up until the next `h3`, or `h2`.

After every `h4` all content will be collapsed/expandable, up until the next `h4`, `h3` or `h2`.

### Generating

Run the `viewReleaseNotes` task to generate the release notes, and open them in your browser (requires Java 6).

### Editing

You can run the `editReleaseNotes` task to open the raw markdown notes in whatever editor is registered for this file type (requires Java 6).

## Userguide

The source for the userguide lives @ `src/docs/userguide`. The userguide is authored using [docbook](http://www.docbook.org/) and uses [docbook stylesheets](http://docbook.sourceforge.net/) with some customisations in `src/stylesheets` to generate HTML. It uses [Flying Saucer](https://xhtmlrenderer.dev.java.net/) + [iText](http://www.lowagie.com/iText/) to generate the PDF from the HTML.

When adding new content, it's generally best to find an example of the kind of content that you want to add somewhere else in the userguide and copy it.

To generate the userguide and see your changes, run:

    ./gradlew docs:userguide

You can then view the built html in `build/docs/userguide` (open the `userguide.html`) to view the front page.

### Custom Tags

#### `<apilink>`

This is an inline element which adds a link to the API documentation for a particular class or method.

    You can use the <apilink class='org.gradle.api.Project' /> interface to do stuff.

    Here is a link to a groovy class: <apilink class='org.gradle.api.task.bundling.Tar' />

The link will point to the DSL reference for the specified class, if available. Otherwise, it will point to the javadoc or groovydoc for the class as appropriate.

To link to a method:

    <apilink class='org.gradle.api.Project' method="apply(java.util.Map)" />

#### `<sample>`

This is a block element which adds some source code from one of the sample builds in `src/samples`.

    <sample id='aUniqueIdForTheSample' dir='userguide/someSample' includeLocation="true" title='a title for the sample'>
        <layout after='someTask'>
            dir1/
            dir1/build.gradle
            dir2/
            dir2/src/main/java/org/gradle/SomeClass.java
        </layout>
        <sourcefile file='build.gradle'/>
        <sourcefile file='water/build.gradle' snippet='some-snippet'/>
        <output args='-PsomeProp=1020 hello'/>
        <output args='-q hello' outputFile='someSample.out' ignoreExtraLines="true"/>
        <test args="-q someTask"/>
    </sample>

You can include zero or more `<sourcefile>` elements, zero or more `<output>` elements, and optionally one `<layout>` element. They can appear in any order, and are included in the userguide in the order they appear in the source document.

Attribute `includeLocation` is optional and defaults to false. When set to true, a tip is included in the userguide to inform the reader when they can find the source for the sample.

##### `<layout>`

The `<layout>` element generates a directory tree listing showing the given files and directories. It will be compared against the actual sample directory layout, to test that the listing included in the userguide matches that in the source. The `after` attribute is optional. When present, Gradle will be run with the given arguments before checking that the files and directories exist. This way, you can document generated files. The `<layout>` element should contain a list of file or directory paths, one per line, relative to the sample directory. Directory names must end with a trailing `/` character.

##### `<sourcefile>`

The `<sourcefile>` element includes a source file in the userguide. It must have a `file` attribute. This is the path to the file to include, relative to the sample base directory. It may optionally have a `snippet` attribute, which is the name of the snippet to include from the source file.

##### `<output>`

The `<output>` element includes a screen listing showing the command to be executed and the expected output.

##### `<test>`

The `<test>` elements defines an integration test to exercise the sample. Nothing is included in the userguide for this element.

When you use the `<sample>` element, a test is added to the integration testsuite to ensure that the sample actually works. If no `<output>`, `<test>`, or `<layout after='...'>` element is present, the test will run `gradle tasks` in the sample directory, and check that the build does not fail. For each `<output>` element, the test will run `gradle $args` and compare the output against the corresponding expected output file in `src/samples/userguideOutput`. For each `<test args='...'>` or `<layout after='...'>` element, the test will run `gradle $args`.

#### `condition="standalone"`

This attribute can be attached to any docbook element to conditionally includes the element in the generated document when the target document is a standalone document, rather than part of the userguide.

    <section condition="standalone">
      <para>You are not reading the user guide right now.</para>
    </section>

#### `<ulink url="website:somepage.html"/>`

Adds a link to the given page on the Gradle web site. This will be replaced by a relative link when the content is included in the web site, and an absolute link when the content is included in a stand alone user guide.

#### Snippets

The sample source files can contain snippets which can be included in the documentation, in place of the entire source file.

    // START SNIPPET something
    some code
    // END SNIPPET something

    some other code

### Useful docbook tags:

#### `<programlisting>`

For code snippets

## Reference Documentation

The reference documentation (i.e. dsl, groovydoc, javadoc etc.) are extracted from the in code doc comments.

To build these, run:

    ./gradlew docs:dslHtml
    ./gradlew docs:javadoc
    ./gradlew docs:groovydoc

The output is available in the `dsl`, `javadoc` and `groovydoc` directories respectively within `build/docs`.

## Building all the docs

There is a convenience task to build all of the documentation:

    ./gradlew docs:docs
