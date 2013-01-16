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

See above.

# Implementation plan

The implementation plan involves introducing some new model elements:

* A "packaging". This is some set of artifacts that can run on the JVM. A packaging carries meta-data with
  it, such as its runtime dependencies.
* A "language source set" which represents a language specific group of source files that form inputs
  to a packaging that can run on the JVM. Usually there is some compilation or other transformation step
  that can take the input source files and transform them to the output packaging. A language source set
  carries meta-data about the source files, such as their compile and runtime dependencies.
* A "functional source set" which is a functional grouping of other source sets. This generally represents
  the set of source files that play some role in the project, such as production or unit test source files.

There are some cross-cutting aspects here:

* These things may or may not be buildable. For example, a packaging may be downloaded from a repository or
  built from some input source sets. Or a source set may be used from the file system or generated from some
  other input source set.
* Things that are buildable carry meta-data about what their inputs are. Things that are not buildable carry
  meta-data about what their inputs were when they were built.

## Story: Java source sets

This story adds the ability to define a set of Java source files and their compile and runtime dependencies.

1. Add a `JavaSourceSet` interface that extends `Named`. This has the following properties:
    1. `source` of type `SourceDirectorySet`. Defaults to `[$projectDir/src/$name/java]`, with a filter
       that includes only `**/*.java`.
    2. `compileClasspath` of type `ClassPath`. Defaults to an empty classpath.
    3. `runtimeClasspath` of type `ClassPath`. Defaults to an empty classpath.
2. Add a `java-lang` plugin.
3. This adds a `JavaExtension` extension with name `java`. This has the following properties:
    1. `sourceSets` - a collection of `JavaSourceSet` instances.

TBD - integration with the `java-base` plugin.

To define a Java source set and compile it:

    apply plugin: `java-lang`

    java {
        sourceSets {
            main {
                srcDirs 'src/main'
            }
        }
    }

    task compileJava(type: JavaCompile) {
        source java.sourceSets.main.srcDirs
        destinationDir file("$buildDir/classes/main")
        classpath = java.sourceSets.main.compileClasspath
    }

## Story: Resource source sets

This story adds the ability to define a set of resource files.

1. Add a `ResourceSet` interface that extends `Named`. This has the following properties:
    1. `source` of type `SourceDirectorySet`. Defaults to `[$projectDir/src/$name/resources]`.
2. Add a `jvm-lang` plugin.
3. This adds a `JvmLanguageExtension` with the name `?` This has the following properties:
    1. `sourceSets` - a collection of `ResourceSet` instances.
3. Change the `java-lang` plugin to apply the `jvm-plugin` plugin.
    1. Excludes `**/*.java` from every resource set.

TBD - integration with the `java-base` plugin.

To assemble a class directory packaging from Java source and resources:

    apply plugin: `java-lang`

    java {
        sourceSets {
            main {
                srcDirs 'src/main'
            }
        }
    }
    resources {
        sourceSets {
            main {
                srcDirs 'src/resources'
            }
        }
    }

    task compileJava(type: JavaCompile) {
        source java.sourceSets.main.srcDirs
        destinationDir file("$buildDir/classes/main")
        classpath = java.sourceSets.main.compileClasspath
    }

    task processResources(type: Copy) {
        from resources.sourceSets.main.srcDirs
        into file("$buildDir/classes/main")
    }

    task classes(dependsOn: compileJava, processResources)

## Story: Class directory packaging

This story adds the ability to define class directory packagings and build them from Java source and resource
files.

1. Add a `ClassDirectoryPackaging` interface that extends `Named`. This has the following properties:
    - `classesDir` of type `File`. Default value is `$buildDir/classes/$name`.
    - `runtimeClasspath` of type `ClassPath`.
2. Change the `jvm-lang` plugin to add a container of JVM packagings.
3. When a class directory packaging is added to the container, a `classes` lifecycle task is added.
4. Allow source set instances to be added to packaging.
    - When one or more `ResourceSet` instances are added, a `ProcessResources` task is added that copies the
      resources into the output directory.
    - When the `java-lang` plugin is applied and one or more `JavaSourceSet` instances are added, a
      `JavaCompile` task is added to compile the source into the output directory.

TBD - integration with the `java-base` plugin.

To assemble a class directory packaging from Java source and resources:

    apply plugin: `java-lang`

    java {
        sourceSets {
            main {
                srcDirs 'src/main'
            }
        }
    }
    resources {
        sourceSets {
            main
        }
    }
    jvm {
        packagings {
            main {
                source java.sourceSets.main
                source resources.sourceSets.main
            }
        }
    }

## Story: JAR packaging

This story adds the ability to define JAR packagings and build them from Java source and resource files.

1. Extract `JvmPackaging' interface from `ClassDirectoryPackaging`.
2. Add a `JarPackaging` interface that extends `JvmPackaging`.
3. When a JAR packaging is added, a `jar` task is added that assembles the JAR.
4. Allow class dir packagings to be added to a JAR packaging.
5. Allow source sets to be added to a JAR packaging.

TBD - integration with the `java-base` plugin.

To assemble a JAR packaging:

    apply plugin: `java-lang`

    java {
        sourceSets {
            main {
                srcDirs 'src/main'
            }
        }
    }
    resources {
        sourceSets {
            main
        }
    }
    jvm {
        packagings {
            main(JarPackaging) {
                source java.sourceSets.main
                source resources.sourceSets.main
            }
        }
    }

## Story: Code quality plugins support source sets with multiple outputs

## Story: IDE plugins honor support source sets

## Story: Groovy source sets

## Story: Scala source sets

## Story: ANTLR source sets

## Story: C++ source sets

Refactor `CppPlugin` to match the other lanugages.

## Story: JavaScript source sets

## Story: Attach packaging to Java library component

# Open issues

* Add JavaScript source sets.
* More packaging types.
* More type-specific meta-data on source sets.
* Navigate from a source set to its packagings and from a packaging to its source sets.
* Packagings and source sets resolved from a repository.
