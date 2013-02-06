Currently, the JVM language plugins assume that a given set of source files is assembled into a single
output. For example, the `main` Java source is compiled and assembled into a JAR file. However, this is not
always a reasonable assumption. Here are some examples:

* When building for multiple runtimes, such as Groovy 1.8 and Groovy 2.0.
* When building multiple variants composed from various source sets, such as an Android application.
* When packaging the output in various different ways, such as in a JAR and a fat JAR.

By making this assumption, the language plugins force the build author to implement these cases in ways that
are not understood by other plugins that extend the JVM language plugins, such as the code quality and IDE
plugins.

This problem is also evident in non-JVM languages such as C++, where a given source file may be compiled and
linked into more than one binaries.

This spec defines a number of changes that aim to extend the JVM language models to expose the fact that a
given set of source may end up in more than one output. It aims to do so in a way that works well with non-JVM
languages.

# Use cases

## Multiple build types for Android applications

An Android application is assembled in to multiple _build types_, such as 'debug' or 'release'.

# Implementation plan

The implementation plan involves introducing some new model elements:

* A _jvm binary_. This is some set of artifacts that can run on the JVM. A packaging carries meta-data with
  it, such as its runtime dependencies.
* A _language source set_. This represents a language specific group of source files that form inputs
  to a packaging that can run on the JVM. Usually there is some compilation or other transformation step
  that can take the input source files and transform them to the output packaging. A language source set
  carries meta-data about the source files, such as their compile and runtime dependencies.
* A _composite source set_. This represents a some grouping of other source sets. This can be used to represent
  a set of source files that play some role in the project, such as production or unit test source files.
* A _classpath_ which describes a set of jvm binaries. A classpath is a collection of things that can be
  resolved either to a set of files or a set of dependency declarations.

We will introduce the concept of a _build item_ to represent these things. Source sets and packagings are both
types of build items.

* Build items may or may not be buildable. For example, a binary may be downloaded from a repository or
  built from some input source sets. Or a source set may be used from the file system or generated from some
  other input source set.
* Build items can take other build items as input when they are built, and often require other things to be
  present when they are used. For example, a binary is typically compiled from source and other binaries.
  At runtime, the binary requires some other binaries to be present.
* Build items that are buildable carry meta-data about what their inputs are. Things that have already been built
  carry meta-data about what their inputs were when they were built.

## Story: Introduce language source sets

This story adds the concept of composite and language source sets, and provides this as an alternate view over
the source sets currently added by the languages plugins. At this stage, it will not be possible to define
source sets except via the existing `sourceSets` container.

1. Add a `LanguageSourceSet` interface that extends `Buildable` and `Named`. This has the following properties:
    - `source` of type `SourceDirectorySet`.
2. Add a `SourceSet` interface that is a container of `LanguageSourceSet` instances. Initially, this will be
   publically read-only.
3. Add a `JavaSourceSet` interface that extends `LanguageSourceSet`.
4. Add a `ResourceSet` interface that extends `LanguageSourceSet`.
5. Add a `language-base` plugin that adds a `SourceSet` instance as a project extension called `sources`.
6. Change the `java-base` plugin to apply the `language-base` plugin.
7. Change the `java-base` plugin so that when a source set is added to the `sourceSets` container:
    1. A corresponding `SourceSet` instance is added to the `source` container.
    2. A `JavaSourceSet` instance called `java` is added to this source set, and shares the same Java source
       directory set as the old source set.
    3. A `ResourceSet` instance called `resources` is added to this source set, and shares the same
       resource source directory set as the old source set.

To configure the `main` source set:

    apply plugin: 'java'

    source {
        main {
            java {
                srcDirs 'src/main'
            }
            resources {
                include '**/*.txt'
            }
        }
    }

    assert source.main.java.source.srcDirs == sourceSets.main.java.srcDirs
    assert source.main.resources.source.srcDirs == sourceSets.main.resources.srcDirs

## Story: Introduce Java source set compile classpath

This story introduces the compile classpath for a Java source set, along with the general purpose concept of
a classpath. Initially, a classpath will provide only a read-only view.

1. Add a `Classpath` interface. Initially, this will be a query-only interface. It has the following properties:
    - `files` of type `FileCollection`
2. Add a `compileClasspath` to the `JavaSourceSet` interface.
3. Change the `java-base` plugin so that the Java source set instance it adds has the same compile classpath.

## Story: Introduce class directory binaries

This story adds the concept of class directory binaries and splits the ability to build them from Java
source and resource files out of the `java-base` plugin. At this stage, it will not be possible to define
binaries except via the existing `sourceSets` container.

1. Add a `ClassDirectoryBinary` interface that extends `Named` and `Buildable`. This has the following
   properties:
    - `classesDir` of type `File`.
    - `source` of type `DomainObjectCollection<LanguageSourceSet>`.
2. Add a `jvm-lang` plugin
3. The `jvm-lang` plugin to add a container of JVM binaries called `jvm.binaries`.
4. When a `ClassDirectoryBinary` is added to the container:
    - Default `classesDir` to `$buildDir/classes/$name`.
    - Add a lifecycle task, called `classes` for the `main` binary and `${name}Classes` for other binaries.
5. The `jvm-lang` plugin adds a rule that when one or more `ResourceSet` instances are added as source for a
   `ClassDirectoryBinary`, a task of type `ProcessResources` is added which copies the resources into the
    output directory. This task is called `processResources` for the `main` binary and `process${name}Resources`
    for other binaries.
6. Add a `java-lang` plugin. This applies the `jvm-lang` plugin.
7. The `java-lang` plugin adds a rule that when one or more `JavaSourceSet` instances are added as source for a
   `ClassDirectoryBinary`, a task of type `JavaCompile` is added to compile the source into the output directory.
   This task is called `compileJava` for the `main` binary and `compile${name}Java` for the other binaries.
8. Change the `java-base` plugin to apply the `java-lang` plugin.
9. Change the `java-base` plugin so that when a source set is added to the `sourceSets` container:
    - Add a `ClassDirectoryBinary` for the source set.
    - No longer adds the lifecycle task, or process resources and Java compile tasks for the source set.
    - Synchronise the source set and binary's classes dir properties.
    - Attach the source set's resources and java source to the binary.

TBD - need to separate the classes and resources directories
TBD - other compile settings
TBD - other language plugins need to keep working

For this story, zero or one `JavaSourceSet` and zero or one `ResourceSet` instances will be supported.
Support for multiple source sets of each language will be added by later stories.

To configure the output of the main Java source and resources:

    apply plugin: 'java'

    jvm {
        binaries {
            main {
                classesDir 'build/main/classes'
            }
        }
    }

    assert sourceSets.main.output.classesDir == jvm.binaries.main.classesDir
    assert compileJava.destinationDir == jvm.binaries.main.classesDir

## Story: Define source sets and binaries without using the `java-base` plugin

This story introduces the ability to define arbitrary source sets and class directory binaries.

TBD - implementation

To build a binary from the main source set:

    apply plugin: 'java'

    jvm {
        binaries {
            release(ClassDirectoryBinary) {
                source sources.main
            }
        }
    }

Running `gradle releaseClasses` will compile the source and copy the resources into the output directory.

To build several binaries from source:

    apply plugin: 'java-lang'

    source {
        api {
            java(JavaSourceSet) { ... }
        }
        impl {
            java(JavaSourceSet) { ... }
            resources(ResourceSet) { ... }
        }
    }

    jvm {
        binaries {
            api(ClassDirectoryBinary) {
                source sources.api
            }
            impl(ClassDirectoryBinary) {
                source sources.impl
            }
        }
    }

Running `gradle implClasses` will compile the impl source.

TBD - running `gradle assemble` does what?

## Story: Build binaries from multiple source

## Story: Dependencies between source sets

## Story: Apply conflict resolution to class paths

## Story: Java source set runtime classpath

## Story: JAR packaging

This story adds the ability to define JAR binaries and build them from Java source and resource files.

1. Extract `JvmBinary` interface from `ClassDirectoryBinary`.
2. Add a `JarBinary` interface that extends `JvmBinary`.
3. When a JAR binary is added, a `jar` task is added that assembles the JAR.
4. Allow class dir binaries to be added to a JAR packaging.

TBD - integration with the `java-base` plugin.

To assemble a JAR binary:

    apply plugin: 'java-lang'

    source {
        main {
            java { ... }
            resources { ... }
        }
    }

    jvm {
        binaries {
            classes(ClassDirectoryBinary) {
                source source.main
            }
            jar(JarBinary) {
                source jvm.binaries.classes
            }
        }
    }

TBD - naming conventions

## Story: Code quality plugins support arbitrary source sets

This story changes the code quality plugins to analyse the source sets in the `source` container.

Some code quality tasks use the compiled bytecode to perform their analysis, so these plugins will need
to deal with the fact that a given source file may end up in multiple outputs.

## Story: IDE plugins support arbitrary source sets

This story changes the IDE plugins to use the source sets in the `source` container.

## Story: Groovy source sets

This story introduces the concept of a Groovy source set and moves the ability to build a jvm binary
from a Groovy source set out of the `groovy-base` plugin.

TBD - joint compilation.

## Story: Scala source sets

This story introduces the concept of a Scala source set and moves the ability to build a jvm binary
from a Scala source set out of the `scala-base` plugin.

TBD - joint compilation.

## Story: ANTLR source sets

This story introduces the concept of generated source, and support for using an ANTRL source set as input
to build a Java source set.

## Story: IDE plugins support generated source

This story changes the IDE plugins to understand that some source is generated and some is not.

## Story: C++ source sets

This story introduces the concept of C++ source sets and header source sets, as a refactoring of the
existing C++ plugins.

## Story: Native binaries

This story introduces the concept of native binaries, as a refactoring of the existing C++ plugins.

## Story: JavaScript source sets

This story introduces the concept JavaScript source sets and binaries. A JavaScript binary is simply a
JavaScript source set that is built from some input source sets.

## Story: Attach binary to Java library component

This story allows a JVM binary to be attached to a Java library component for publishing.

# Open issues

* Consuming vs producing.
* Custom source sets.
* Warn or fail when some source for a class packaging cannot be handled.
* Merge classes and resources?
* Joint compilation.
* Add JavaScript source sets.
* More packaging types.
* More type-specific meta-data on source sets.
* Navigate from a source set to its packagings and from a packaging to its source sets.
* Packagings and source sets resolved from a repository.
