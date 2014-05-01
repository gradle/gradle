
This spec describes some work to allow plugins to define the kinds of components that they produce and consume.

## A note on terminology

There is currently a disconnect in the terminology used for the dependency management component model, and that used
for the component model provided by the native plugins.

The dependency management model uses the term `component instance` or `component` to refer to what is known as a `binary`
in the native model. A `component` in the native model doesn't really have a corresponding concept in the dependency
management model (a `module` is the closest we have, and this is not the same thing).

Part of the work for this spec is to unify the terminology. This is yet to be defined.

For now, this spec uses the terminology from the native component model, using `binary` to refer to what is also
known as a `component instance` or `variant`.

# Features

## Feature: Build author creates a JVM library with Java sources

### Story: Build author defines JVM library

#### DSL

Project defining single jvm libraries

    apply plugin: 'jvm-component'

    libraries {
        main(JvmLibrary)
    }

Project defining multiple jvm libraries

    apply plugin: 'jvm-component'

    libraries {
        main(JvmLibrary)
        extra(JvmLibrary)
    }

Combining native and jvm libraries in single project

    apply plugin: 'jvm-component'
    apply plugin: 'native-component'

    libraries {
        myNativeLib(NativeLibrary)
        myJvmLib(JvmLibrary)
    }

#### Implementation plan

- Introduce `org.gradle.jvm.JvmLibrary`
- Rename `org.gradle.nativebinaries.Library` to `org.gradle.nativebinaries.NativeLibrary`
    - Similar renames for `Executable`, `TestSuite` and all related classes
- Introduce a common superclass for `Library`.
- Extract `org.gradle.nativebinaries.LibraryContainer` out of `nativebinaries` project into `language-base` project,
  and make it an extensible polymorphic container.
    - Different 'library' plugins will register a factory for library types.
- Add a `jvm-component` plugin, that:
    - Registers support for `JvmLibrary`.
    - Adds a single `JvmLibraryBinary` instance to the `binaries` container for every `JvmLibrary`
    - Creates a binary lifecycle task for generating this `JvmLibraryBinary`
    - Wires the binary lifecycle task into the main `assemble` task.
- Rename `NativeBinariesPlugin` to `NativeComponentPlugin` with id `native-component`.

#### Test cases

- Can apply `jvm-component` plugin without defining library
    - No binaries defined
    - No lifecycle task added
- Define a jvm library component
    - `JvmLibraryBinary` added to binaries container
    - Lifecycle task available to build binary: builds an empty jar
    - Binary is buildable: can add dependent tasks which are executed when building binary
- `JvmLibraryBinary` has no dependencies
- Define and build multiple java libraries in the same project
  - Build library jars individually using binary lifecycle task
  - `gradle assemble` builds single jar for each library
  - `gradle assemble` builds each native library
- Can combine native and JVM libraries in the same project

#### Open issues

- Remove the need to declare the library type, rather than making it explicit: Possibly based on available types, possibly by naming the container?
- Split the native binaries plugins into `native-component` and the various language support plugins
    - Consistent plugin composition for java/native
- Move `Binary` and `ClassDirectoryBinary` to live with the runtime support classes (and not the language support classes)
- Consider splitting jvm-runtime & jvm-lang support into separate projects. Similar for native-runtime and native-lang.

### Story: Build author creates JVM library jar from Java sources

When a JVM library is defined with Java language support, then binary is built from conventional source set locations:

- Has a single Java source set hardcoded to `src/myLib/java`
- Has a single resources source set hardcoded to `src/myLib/resources`

#### DSL

Java library using conventional source locations

    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    libraries {
        myLib(JvmLibrary)
    }


Combining jvm-java and native (multi-lang) libraries in single project

    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    apply plugin: 'native-component'
    apply plugin: 'cpp-lang'
    apply plugin: 'c-lang'

    libraries {
        myNativeLib(NativeLibrary)
        myJvmLib(JvmLibrary)
    }

#### Test cases

- Define and build the jar for a java library (assert jar contents for each case)
    - With no sources or resources
    - With sources but no resources
    - With resources but no sources
    - With both sources and resources
- Reports failure to compile library
- Compiled sources and resources are available in a common directory
- All generated resources are removed when all resources source files are removed.
- All compiled classes are removed when all java source files are removed.

#### Open issues

- Need `groovy-lang` and `scala-lang` plugins
- Need to decide where the 'compile java for jvm' functionality lives: should probably live with the language plugin, so that it's easier to
  add support for a new JVM language without modifying the base `jvm-component` plugin.
    - Possibly need a 'java-for-jvm' plugin that is auto-applied in the presence of the 'jvm-component' and 'java-lang' plugin?
    - Could then have the equivalent 'java-for-native' plugin that uses GCJ to compile java for native runtime
- Split the native binaries plugins into `native-component` and the various language support plugins
    - Consistent plugin composition for java/native
- Consider splitting jvm-runtime & jvm-lang support into separate projects. Similar for native-runtime and native-lang.

### Story: Legacy JVM language plugins declare a jvm library

#### Test cases

- JVM library with name 'main' is defined with any combination of `java`, `groovy` and `scala` plugins applied
- Can build legacy jvm library jar using standard lifecycle task

#### Open issues

- The legacy application plugin should also declare a jvm application.

## Feature: Build author declares that a Java library depends on a Java library produced by another project

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            dependencies {
                project 'other-project' // Infer the target library
                project 'other-project' library 'my-lib'
            }
        }
    }

When the project attribute refers to a project with a component plugin applied:

- Select the target library from the libraries of the project. Assert that there is exactly one matching JVM library.
- At compile time, include the library's jar binary only.
- At runtime time, include the library's jar binary and runtime dependencies.

When the project attribute refers to a project without a component plugin applied:

- At compile and runtime, include the artifacts and dependencies from the `default` configuration.

### Open issues

- Should be able to depend on a library in the same project.
- Need an API to query the various classpaths.
- Need to be able to configure the resolution strategy for each usage.

## Feature: Build author declares that a Java library depends on an external Java library

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            dependencies {
                library "myorg:mylib:2.3"
            }
        }
    }

This makes the jar of `myorg:mylib:2.3` and its dependencies available at both compile time and runtime.

### Open issues

- Using `library "some:thing:1.2"` will conflict with a dependency `library "someLib"` on a library declared in the same project.
Could potentially just assert that component names do not contain ':' (should do this anyway).

## Feature: Build author declares that legacy Java project depends on a Java library produced by another project

For example:

    apply plugin: 'java'

    dependencies {
        compile project: 'other-project'
    }

When the project attribute refers to a project with a component plugin applied:

- Select the target library from the libraries of the project. Assert that there is exactly one JVM library.
- At compile time, include the library's jar binary only.
- At runtime time, include the library's jar binary and runtime dependencies.

### Open issues

- Allow `library` attribute?

## Feature: Build user views the dependencies for the Java libraries of a project

The dependency reports show the dependencies of the Java libraries of a project:

- `dependencies` task
- `dependencyInsight` task
- HTML report

## Feature: Build author declares that a native component depends on a native library

Add the ability to declare dependencies directly on a native component, using a similar DSL as for Java libraries:

    apply plugin: 'cpp'

    libraries {
        myLib {
            dependencies {
                project 'other-project'
                library 'my-prebuilt'
                library 'local-lib' linkage 'api'
            }
        }
    }

Also reuse the dependency DSL at the source set level:

    apply plugin: 'cpp'

    libraries {
        myLib
    }

    sources {
        myLib {
            java {
                dependencies {
                    project 'other-project'
                    library 'my-lib' linkage 'api'
                }
            }
        }
    }

## Feature: Build author declares that the API of a Java library requires some Java library

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            dependencies {
                api {
                    project 'other-project' library 'other-lib'
                }
            }
        }
    }

This makes the API of the library 'other-lib' available at compile time, and the runtime artifacts and dependencies of 'other-lib' available at
runtime.

It also exposes the API of the library 'other-lib' as part of the API for 'myLib', so that it is visible at compile time for any other component that
depends on 'myLib'.

The default API of a Java library is its Jar file and no dependencies.

### Open issues

- Add this to native libraries

## Feature: Build author declares that a Java library requires some Java library at runtime

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            dependencies {
                runtime {
                    project 'other-project' library 'other-lib'
                }
            }
        }
    }

### Open issues

- Add this to native libraries

## Feature: Build author declares the target JVM for a Java library

For example:

    apply plugin: 'new-java'

    platforms {
        // Java versions are visible here
    }

    libraries {
        myLib {
            buildFor platforms.java7
        }
    }

This declares that the bytecode for the binary should be generated for Java 7, and should be compiled against the Java 7 API.
Assume that the source also uses Java 7 language features.

When a library `a` depends on another library `b`, assert that the target JVM for `b` is compatible with the target JVM for `a` - that is
JVM for `a` is same or newer thatn the JVM for `b`.

The target JVM for a legacy Java library is the lowest of `sourceCompatibility` and `targetCompatibility`.

### Open issues

- Need to discover or configure the JDK installations.

## Feature: Build author declares a custom target platform for a Java library

For example:

    apply plugin: 'new-java'

    platforms {
        myContainer {
            runsOn platforms.java6
            provides {
                library 'myorg:mylib:1.2'
            }
        }
    }

    libraries {
        myLib {
            buildFor platforms.myContainer
        }
    }

This defines a custom container that requires Java 6 or later, and states that the library should be built for that container.

This includes the API of 'myorg:mylib:1.2' at compile time, but not at runtime. The bytecode for the library is compiled for java 6.

When a library `a` depends on another library `b`, assert that both libraries run on the same platform, or that `b` targets a JVM compatible with
the JVM for the platform of `a`.

### Open issues

- Rework the platform DSL for native component to work the same way.
- Retrofit into legacy java and web plugins.

## Feature: Build author declares dependencies for a Java source set

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            source {
                java {
                    runsOn platforms.java7
                    dependencies {
                        project 'some-project'
                        library 'myorg:mylib:1.2'
                        runtime {
                            ...
                        }
                    }
                }
            }
        }
    }

Will have to move source sets live with the library domain object.

### Open issues

- Fail or skip if target platform is not applicable for the the component's platform?

## Feature: Custom plugin defines a custom library type

Add a sample plugin that declares its own library type:

    apply plugin: 'my-sample'

    libraries {
        myCustomLib {
            someProperty 17
        }
    }

A custom library type:
- Extends or implements some public base `Library` type.
- Has no dependencies.
- Produces no artifacts.

## Feature: Custom library produces binaries

Change the sample plugin so that it declares its own binary type for the libraries it defines:

    apply plugin: 'my-sample'

    libraries {
        myCustomLib {
            binaries {
                // Custom binaries are visible here, however it is that the plugin decides which binaries are available
            }
        }
    }

    binaries {
        // Custom binaries are visible here
    }

Allow a plugin to declare the binaries for a custom library.

A custom binary:
- Extends or implements some public base `LibraryBinary` type.
- Has some lifecycle task to build its outputs.

## Feature: Build author declares dependencies for custom library

Change the sample plugin so that it allows Java and custom libraries to be used as dependencies:

    apply plugin: 'my-sample'

    libraries {
        myCustomLib {
            dependencies {
                project 'other-project'
                customUsage {
                    project 'other-project' library 'some-lib'
                }
            }
        }
    }

Allow a plugin to resolve the dependencies for a custom library, via some API. Target library must produce exactly
one binary of the target type.

Move the hard-coded Java library model out of the dependency management engine and have the jvm plugins define the
Java library type.

Resolve dependencies with inline notation:

    def compileClasspath = dependencies.newDependencySet()
                .withType(JvmLibrary.class)
                .withUsage(Usage.COMPILE)
                .forDependencies("org.group:module:1.0", ...) // Any dependency notation, or dependency instances
                .create()

    compileTask.classPath = compileClasspath.files
    assert compileClasspath.files == compileClasspath.artifactResolutionResult.files

Resolve dependencies based on a configuration:

    def testRuntimeUsage = dependencies.newDependencySet()
                .withType(JvmLibrary.class)
                .withUsage(Usage.RUNTIME)
                .forDependencies(configurations.test.incoming.dependencies)
                .create()
    copy {
        from testRuntimeUsage.artifactResolutionResult.artifactFiles
        into "libs"
    }

    testRuntimeUsage.resolutionResult.allDependencies { dep ->
        println dep.requested
    }

Resolve dependencies not added a configuration:

    dependencies {
        def lib1 = create("org.group:mylib:1.+") {
            transitive false
        }
        def projectDep = project(":foo")
    }
    def deps = dependencies.newDependencySet()
                .withType(JvmLibrary)
                .withUsage(Usage.RUNTIME)
                .forDependencies(lib1, projectDep)
                .create()
    deps.files.each {
        println it
    }

### Open issues

- Component type declares usages.
- Binary declares artifacts and dependencies for a given usage.

## Feature: Build user views the dependencies for the custom libraries of a project

Change the `dependencies`, `dependencyInsight` and HTML dependencies report so that it can report
on the dependencies of a custom component, plus whatever binaries the component happens to produce.

## Feature: Build author declares target platform for custom library

Change the sample plugin to allow a target JVM based platform to be declared:

    apply plugin: 'my-sample'

    platforms {
        // Several target platforms are visible here
    }

    libraries {
        myCustomLib {
            minSdk 12 // implies: buildFor platforms.mySdk12
        }
    }

## Feature: Java library produces multiple variants

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            buildFor platforms.java6, platforms.java8
        }
    }

Builds a binary for Java 6 and Java 8.

Dependency resolution selects the best binary from each dependency for the target platform.

## Feature: Dependency resolution for native components

## Feature: Build user views the dependencies for the native components of a project

# Open issues and Later work

- Should use rules mechanism.
- Expose the source and javadoc artifacts for local binaries.
- Reuse the local component and binary meta-data for publication.
    - Version the meta-data schemas.
    - Source and javadoc artifacts.
- Legacy war and ear plugins define binaries.
- Java component plugins support variants.
- Deprecate and remove support for resolution via configurations.
- Add a report that shows the details for the components and binaries produced by a project.
