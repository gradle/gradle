This specification defines some build-by-convention support for building and packaging JVM based and native applications and libraries.

# Use cases

## JVM based library

For a JVM based library, Gradle will build:

* The runtime JAR
* Runtime dependencies
* API documentation.

Optionally, Gradle will also be able to build:

* Library source ZIP.
* An API JAR and compile-time dependencies.
* User guide and other documentation.

Gradle will be able to assemble a distribution for the library, containing these artifacts.

It will be possible to share these artifacts between projects using dependency management, either locally or via an artifact repository.

## JVM based command-line application

For a JVM based command-line application, Gradle will build:

* Generated launcher scripts
* The runtime JAR and dependencies.

Optionally, Gradle will also be able to build:

* Application source ZIP.
* User guide and other documentation.

Gradle will be able to assemble a distribution for the application, containing these artifacts.

It will be possible to share these artifacts between projects using dependency management.

## C++/C library

For a C/C++ library, Gradle will build:

* The header files.
* The shared library binary and/or the static library binary.
* The import file, in the case of Windows.
* API documentation.

Optionally, Gradle will also be able to build:

* Debug file, in the case of Windows.
* Library source ZIP.
* User guide and other documentation.

Gradle will be able to assemble a distribution that contains the above artifacts for all variants of the library, and to build a distribution per variant.

It will be possible to share these artifacts between projects using dependency management.

## Native application

For a native application, Gradle will build:

* The native executable.
* Shared libraries required at runtime.

Optionally, Gradle will also be able to build:

* Debug file, in the case of Windows.
* Application source ZIP.
* User guide and other documentation.

Gradle will be able to assemble a distribution that contains the above artifacts for all variants of the library, and to build a distribution per variant.

It will be possible to share these artifacts between projects using dependency management.

## Gradle distribution

The Gradle -all, -bin, and -src distributions can be built and published using the distribution infrastructure.

## Libraries and applications composed from multiple projects

It should be possible to define a library or application that bundles or links together several libraries produced by other projects.

It should also be possible to define a project that does not contain any source, but simply aggregates together several libraries produced by other
projects to produce a composite library or application.

## Native executable

Gradle will be able to build a native launcher for an application, as an alternative to using launcher scripts.

## Executable Jar

Gradle will be able to package a JVM based application as an executable fat jar, as an alternative to using launcher scripts.

## JVM based daemon

For a JVM based application that implements a daemon, Gradle will package the application with launchers that allow the application to be run as a daemon.

## JVM based GUI application

For a JVM based application that implements a native GUI, Gradle will package that application as a platform-specific 'double-clickable' application.

## Native packaging

For a library or application, Gradle will build a native package that can be used to install the library or application using a native package manager.

More info in the [forum ticket](http://forums.gradle.org/gradle/topics/modeling_the_java_application_development_domain).

# User visible changes

Several new plugins will be added:

## `jvm-library` plugin

A base plugin that adds the capability to build JVM based libraries. Does not imply an implementation language. This plugin will provide some way to
define one or more JVM libraries. This plugin will add rules that, given a JVM library definition, know how to build and publish the JAR and API
documentation for the library.

You would use this plugin in combination with one or more language plugins to build and publish JVM based libraries from your project.

## `distribution` plugin

A base plugin that adds the capability to build distributions. This plugin will add rules that, given a distribution definition, know how to build, install
and publish the distribution.

## `java-library` plugin

An opinionated plugin that uses the `jvm-library` plugin to define a single `main` library, implemented in Java, and to be published to a repository.

## `jvm-application` plugin

A base plugin that adds the capability to build JVM based applications. Does not imply an implementation language. This plugin will provide some way to
define one ore more JVM applications. This plugin will add rules that, given a JVM application definition, know how to build and publish the launcher
scripts, JAR and install image for the application.

You would use this plugin in combination with one or more language plugins to build and publish JVM based applications from your project.

## `java-application` plugin

An opinionated plugin that uses the `jvm-application` plugin to define a single `main` application, implemented in Java, and to be published to a
repository.

# Implementation Plan

## Introduce a `java-library` plugin

An opinionated plugin that adds a single distribution that

    - 'java-library' plugin
        - applies 'java' plugin
        - adds 'distZip' task
            - packages up the jar and runtime dependencies of the library as a Zip.
            - include contents of src/dist
        - adds 'distribution' extension
            - has 'name' property that is used to configure the 'baseName' of the distZip task.

## DSL:

    apply plugin: 'java-library'
    //applies 'java' plugin

    distribution {
      name = 'someName'
    }

    distZip {  //type: Zip
      from { ... }
    }

## Sad day cases

- works if src/dist does not exist
- works if only 'java-library' is specified and nothing else (e.g. sensible defaults are used for the distro name, etc.)
- does not crash if distribution.name is configured to null

# Integration test coverage

- add an integ test that runs task to build up a distro that:
    - has some stuff in src/dist
    - includes some runtime dependency that is declared
    - uses the name of the distribution.name for the distro name

# Implementation approach

## Unit testing

- add unit tests for the plugin, validate all the features (separate tests) declared in 'user visible changes' section.

## Documentation

- add new plugin chapter and hook it up to the user guide
- add the xml for the new extension object
- new extension object should have a javadoc with small code sample (using our 'autoTested').
    - say, apply plugin and configure the distribution.name
- link the new extension from plugins.xml

# Next steps

Only after above is completed & integrated with the master we want to design and implement the following:

- Generate a Tar distribution as well.
- Share configuration and tasks with the application plugin, by extracting a common 'dist' plugin out of the application plugin.
- (Very advanced) Allow the distributions to be published instead of the jar.
    This would need to mess with the generated pom.xml/ivy.xml to remove the dependency declarations
    for those dependencies that have been bundled in the distribution.