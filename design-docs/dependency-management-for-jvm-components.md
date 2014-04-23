
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

TODO - replace 'binary' with 'component'.

# Stories

## Feature: Build author declares a Java library

For example:

    apply plugin: 'new-java' // TBD - needs a better identifier

    libraries {
        myLib {
            // Infer the type by the available set of library types
        }
    }

This defines a Java library that:
- Has a single Java source set
- Has a single resources source set
- Has no dependencies
- Produces a jar binary as output.
- Has a lifecycle task to build the binary.

It should be possible to declare multiple libraries for a given project.

### Open issues

- Need a better id for the plugin, to sync up with the native plugins.
- Make the library type explicit, rather than infer it? Possibly with a type, possibly by naming the container
- The legacy JVM plugins should also declare a jvm component

## Feature: Build author declares a dependency on a Java library produced by another project

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
- Should be possible for old plugins to depend on project with new plugins.
- Need an API to query the various classpaths.
- Add this to native plugins
- Add dependencies for each source set.
- Need to be able to configure the resolution strategy for each usage.

## Feature: Build author declares a dependency on an external Java library

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            dependencies {
                library "myorg:mylib:2.3"
            }
        }
    }

This makes the jar of "myorg:mylib:2.3" and its dependencies available at both compile time and runtime.

## Feature: Build author declares dependency for legacy Java project declares on a Java library produced by another project

For example:

    apply plugin: 'java'

    dependencies {
        compile project: 'other-project'
    }

When the project attribute refers to a project with a component plugin applied:

- Select the target library from the libraries of the project. Assert that there is exactly one JVM library.
- At compile time, include the library's jar binary only.
- At runtime time, include the library's jar binary and runtime dependencies.

## Feature: Build user views dependency report for the libraries of a project

The dependency reports show the dependencies of the libraries of a project:

- `dependencies` task
- `dependencyInsight` task
- HTML report

## Feature: Build author declares an API dependency

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

## Feature: Build author declares a runtime dependency

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

## Feature: Build author declares the target platform for a Java library

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
            runsOn platforms.myContainer
        }
    }

This defines a custom container that requires Java 6 or later.

This includes the API of 'myorg:mylib:1.2' at compile time, but not at runtime. The bytecode for the library is compiled for java 6.

When a library depends on another library, assert that the referring library targets the same platform.

### Open issues

- Sync up with native components.

## Feature: Custom plugin provides its own Java library implementation

## Feature: API to resolve a Java library dependency graph

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

## Feature: Custom plugin consumes Java libraries

## Feature: Java library produces multiple variants

## Feature: Dependency resolution for native components

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
