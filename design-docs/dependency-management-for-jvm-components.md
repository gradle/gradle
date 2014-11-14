## Feature: Build author declares that a Java library depends on a Java library produced by another project

### Story: Legacy JVM language plugins declare a jvm library

- Rework the existing `SoftwareComponent` implementations so that they are `Component` implementations instead.
- Expose all native and jvm components through `project.components`.
- Don't need to support publishing yet. Attaching one of these components to a publication can result in a 'this isn't supported yet' exception.
- The target JVM for a legacy Java library is the lowest of `sourceCompatibility` and `targetCompatibility`.

```
apply plugin: 'java'

// The library is visible
assert jvm.libraries.main instanceof LegacyJvmLibrary
assert libraries.size() == 1
assert components.size() == 1

// The binary is visible
assert binaries.withType(ClassDirectoryBinary).size() == 1
assert binaries.withType(JarBinary).size() == 1
```

#### Test cases

- JVM library with name `main` is defined with any combination of `java`, `groovy` and `scala` plugins applied
- Web application with name `war` is defined when `war` plugin is applied.
- Can build legacy jvm library jar using standard lifecycle task
- Can mix legacy and new jvm libraries in the same project.

#### Open issues

- Expose through the DSL, or just through the APIs?
- Change `gradle dependencyInsight` to use the JVM library component to decide the default dependencies to report on.

### Story: Build author declares a dependency on another Java library

For example:

    apply plugin: 'jvm-component'

    jvm {
        libraries {
            myLib {
                dependencies {
                    project 'other-project' // Infer the target library
                    project 'other-project' library 'my-lib'
                }
            }
        }
    }

When the project attribute refers to a project with a component plugin applied:

- Select the target library from the libraries of the project. Assert that there is exactly one matching JVM library.
- At compile time, include the library's jar binary only.
- At runtime time, include the library's jar binary and runtime dependencies.

When the project attribute refers to a project without a component plugin applied:

- At compile and runtime, include the artifacts and dependencies from the `default` configuration.

#### Open issues

- Should be able to depend on a library in the same project.
- Need an API to query the various classpaths.
- Need to be able to configure the resolution strategy for each usage.

## Feature: Build author declares that a Java library depends on an external Java library

For example:

    apply plugin: 'jvm-component'

    jvm {
        libraries {
            myLib {
                dependencies {
                    library "myorg:mylib:2.3"
                }
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

## Feature: JVM platform aware dependency resolution

When a library `a` depends on another library `b`, assert that the target JVM for `b` is compatible with the target JVM for `a` - that is
JVM for `a` is same or newer than the JVM for `b`, or select the appropriate variant for `b`.

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

## Feature: Dependency resolution for native components

## Feature: Build user views the dependencies for the native components of a project

