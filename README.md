<img src="http://gradle.org/img/gradle_logo.gif" />

Gradle is a build tool with a focus on build automation and support for multi-language development. If you are building, testing, publishing, and deploying software on any platform, Gradle offers a flexible model that can support the entire development lifecycle from compiling and packaging code to publishing web sites. Gradle has been designed to support build automation across multiple languages and platforms including Java, Scala, Android, C/C++, and Groovy, and is closely integrated with development tools and continuous integration servers including Eclipse, IntelliJ, and Jenkins.

For more information about Gradle, please visit: http://gradle.org

## Downloading

You can download released versions and nightly build artifacts from: http://gradle.org/downloads

## Building

Naturally, Gradle builds itself with Gradle. Gradle provides an innovative [wrapper](http://gradle.org/docs/current/userguide/gradle_wrapper.html) that allows you to work with a Gradle build without having to manually install Gradle. The wrapper is a batch script on Windows and a shell script on other operating systems.

You should use the wrapper to build the gradle project. Generally, you should use the wrapper for any wrapper-enabled project because it guarantees building with the Gradle version that the build was intended to use.

To build the entire Gradle project, you should run the following in the root of the checkout.

    ./gradlew build

This will compile all the code, generate all the documentation and run all the tests. It can take up to an hour on a fast machine because we have thousands of tests, including integration tests that exercise virtually every Gradle feature. Among the things we test are: compatibility across versions, validity of samples and Javadoc snippets, daemon process capabilities, etc.

### Installing from source

To create an install from the source tree you can run either of the following:

    ./gradlew install -Pgradle_installPath=/usr/local/gradle-source-build

This will create a minimal installation; just what's needed to run Gradle (i.e. no docs). Note that the `-Pgradle_installPath` denotes where to install to. 

You can then build a Gradle built project with this installation:

    /usr/local/gradle-source-build/bin/gradle «some task»

To create a full installation (includes docs)…

    ./gradlew installAll -Pgradle_installPath=/usr/local/gradle-source-build

### Working with subprojects

The Gradle build uses Gradle's ability to customise the logical structure of a multiproject build. All of the build's subprojects are in the `subprojects/` directory and are mapped to top level children in [settings.gradle](https://github.com/gradle/gradle/blob/master/settings.gradle).

This means that to build just the `core` subproject (that lives in `subprojects/core`) you would run:

    ./gradlew core:build

Or to build the docs:

    ./gradlew docs:build

And so on.

## Contributing

If you're looking to contribute to Gradle or provide a patch/pull request, you can find info on how to get in touch with the developers at http://gradle.org/development.

### Contributing Code

This is a complicated topic and the Gradle development team are happy to help anybody get started working with the Gradle code base, so don't hesitate to get in touch with the developers if you need help working with the finer points of the build.

If you are simply wanting to fix something or adding a small minor feature, it is usually good enough to simply make your change to the code and then run the `check` task for that subproject. So if the patch was to the `launcher` package for example, you can run:

    ./gradlew launcher:check

To run all of the tests and code quality checks for that module.

### Contributing Documentation

Please see the readme in the [docs subproject](https://github.com/gradle/gradle/tree/master/subprojects/docs).

## Opening in your IDE

### IntelliJ IDEA

To open the Gradle project in IDEA, simply run the following task from the root:

    ./gradlew idea

This will generate appropriate IDEA metadata so that the project can be opened from within IDEA.

### Eclipse

The Gradle project is not currently buildable in Eclipse. This is something that will be rectified in the future.

You can try running:

    ./gradlew eclipse

This command generates Eclipse metadata that allows importing the project into Eclipse. However, you will have to do some manual fixes to the project's setup to make it work.
