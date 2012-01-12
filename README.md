<img src="http://gradle.org/img/gradle_logo.gif" />

Gradle is build automation *evolved*. Gradle can automate the building, testing, publishing, deployment and more of software packages or other types of projects such as generated static websites, generated documentation or indeed anything else.

For more information about Gradle, please visit http://gradle.org

## Downloading

You can download built versions (including nightly edge builds) from http://gradle.org/downloads

## Building

Naturally, Gradle builds itself with Gradle. Gradle provides an innovative [wrapper](http://gradle.org/docs/current/userguide/gradle_wrapper.html) that allows you to work with a Gradle build without having to manually install Gradle. The wrapper is a batch script on Windows and a shell script on other operating systems. You should always build the Gradle project via wrapper.

So to build the entire Gradle project, you can run the following in the root of the checkout…

    ./gradlew build

This will compile all the code, generate all the documentation and run all the tests. It can take up to an hour on a fast machine.

### Installing from source

To create an install from the source tree you can run either of the following:

    ./gradlew install -Pgradle_installPath=/usr/local/gradle-source-build

This will create a minimal installation; just what's needed to run Gradle (i.e. no docs). Note that the `-Pgradle_installPath` denotes where to install to. 

You can then build a Gradle built project with this installation:

    /usr/local/gradle-source-build/bin/gradle «some task»

To create a full installation (includes docs)…

    ./gradlew install -Pgradle_installPath=/usr/local/gradle-source-build

### Working with sub projects

The Gradle build uses Gradle's ability to customise the logical structure of a multiproject build. All of the subprojects of the build are in the `subprojects/` directory, but these are mapped to top level children (in [settings.gradle](https://github.com/gradle/gradle/blob/master/settings.gradle)).

This means that to build just the `core` subproject (that lives in `subprojects/core`) you would run:

    ./gradlew core:build

Or to build the docs:

    ./gradlew docs:build

And so on.

## Contributing

If you're looking to contribute to Gradle or provide a patch/pull request, you can find info on how to get in touch with the developers @ http://gradle.org/development.

### Contributing Code

This is a complicated topic and the Gradle development team are happy to help anybody get started working with the Gradle code base, so don't hesitate to get in touch with the developers if you need help working with the finer points of the build.

If you are simply wanting to fix something or adding a small minor feature, it is usually good enough to simply make your change to the code and then run the `check` task for that subproject. So if the patch was to the `launcher` package for example, you can run:

    ./gradlew launcher:check

To run all of the tests and code quality checks for that module.

### Contributing Documentation

The Gradle project goes to much effort to produce top quality [documentation](http://gradle.org/documentation). All of the documentation is contained in the `docs` (path: `subprojects/docs`).

#### Contributing to the Userguide

The source for the userguide lives @ `subprojects/docs/src/docs/userguide` and is in [docbook](http://www.docbook.org/). When adding new content, it's generally best to find an example of the kind of content that you want to add somewhere else in the userguide and copy it.

To generate the userguide and see your changes, run:

    ./gradlew docs:userguide

You can then view the built html in `subprojects/docs/build/docs/userguide` (open the `userguide.html`) to view the front page.

#### Contributing to the reference documentation

The reference documentation (i.e. [dsl](http://gradle.org/docs/current/dsl/), [javadoc](http://gradle.org/docs/current/javadoc/) and [groovydoc](http://gradle.org/docs/current/groovydoc/)) are extracted from the in code doc comments.

To build these, run:

    ./gradlew docs:dslHtml
    ./gradlew docs:javadoc
    ./gradlew docs:groovydoc

The output is available in the `dsl`, `javadoc` and `groovydoc` directories respectively within `subprojects/docs/build/docs`.

#### Building all the docs

There is a convenience task to build all of the documentation:

    ./gradlew docs:docs
