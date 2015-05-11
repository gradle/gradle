
This spec outlines work required to introduce dependency management for JVM based components.

# Approach

The goal is to support dependency graphs made up of components build from Java source that depend on other components
that present some Java API.

Specifically the following consumers:

- Java library
- Custom library or application built from Java

And the following producers:

- Java library
- Custom component that provides a Java API

Here is an example:

<img src="img/jvm_dependency_management.png"/>

External components are out of scope for this work.

The work can be made up of several parts:

1. Add an initial DSL to declare the dependencies of a Java source set owned by a Java library.
2. Add a basic implementation to honour these dependencies at compile time. Only for local Java libraries that consume other Java libraries.
3. Add support to allow a custom component to be built from Java source with dependencies. Only consume local Java libraries.
4. Add support to allow a custom component to provide a Java API. Can consume from Java libraries and custom components.
5. Add support to declare the API of a Java library.

And later:

1. Add support for custom component variants.
2. Add support for execution of Java based components. For example, some way to consume the runtime classpath of a Jvm binary from a task.
3. Improve dependency DSL.

# Feature: Build author declares dependencies of Java library

## Story: Build author declares required libraries of Java source set

Add a basic DSL to declare the required libraries of a Java source set:

    model {
        components {
            main {
                sources {
                    java {
                        dependencies {
                            library 'someLib' // Library in same project
                            project 'otherProject' library 'someLib' // Library in other project
                            project 'otherProject' // Library in other project, expect exactly one library
                        }
                    }
                }
            }
        }
    }

Model `JavaSourceSet.dependencies` as a mutable collection of dependency declarations, with conveniences to add items to the collection.

Out of scope:

- Resolving or using the dependencies. This story is simply to get a basic DSL in place.
- Provide any public API or DSL to query the resolved dependencies. Resolution will be internal at this stage.

## Story: Resolve required libraries of Java source set

Resolve enough of the compile time dependency graph for a Java source set to validate that the required libraries exist.

- When a Java library is compiled, fail resolution when that is a dependency declaration for which no matching Java library can be found
- Error cases:
    - Not found, error message should include list of available components in target project.
        - Project dependency, not exactly one Java library in target project.
        - Project + library dependency, no component with given name.
    - Unsupported type, error message should include information about supported component types
- Direct dependencies only.

Out of scope:

- Building the required library Jars or making the library Jars available at compile time.
- API or DSL to query the resolved graph.

### Implementation:

The implementation *must* make use of the dependency resolution engine, and refactor the resolution engine where required:

- Entry point should be `ArtifactDependencyResolver`.
    - Extract a minimal interface out of `ConfigurationInternal` that does not extend `Configuration` and change `ArtifactDependencyResolver` to accept this instead
      of `ConfigurationInternal`.
    - Pass in an implementation that represents the consuming Java library. Can start with no dependencies.

- TBD

## Story: API of required libraries is made available when Java source set is compiled

Resolve the task dependencies and artifacts for the compile time dependency graph for a Java source set, and make the result available at compile time.

- When a Java library is to be compiled, determine the tasks required to build its required libraries.
- When a Java library is compiled, provide a classpath that contains the Jars of the required libraries.
- Error cases:
    - Java library does not have exactly one Jar binary.

Out of scope:

- API or DSL to query the resolved classpath.
- Validation of target platform.

### Implementation:

TBD

## Feature backlog

Improve resolution implementation: error messages, etc.

# Feature: Custom component built from Java source

Allow a custom component to declare that it is built from Java source.

Allow plugin to use compiled classes from a Java source set to build a custom binary.

Expose a way to query the resolved compile classpath for a Java source set

TBD

## Feature backlog

# Later work

# Feature: Java library consumes local Java library

- Same project
- Other project
- Not external
- Not legacy plugins
- Consumes API dependencies at compile time
- Consumes runtime dependencies at runtime
- Select jar binary or classes binary with compatible platform, fail if not exactly one
- Need an API to query the various classpaths.
- Need to be able to configure the resolution strategy for each usage.
- Declare dependencies at component, source set and binary level
- Reporting
- Dependency resolution rules
- Resolution events

# Feature: Custom Java based component local library

- Java source only
- Custom component consumes Java library
- Custom component consumes custom library
- Java library consumes custom library
- Reporting

# Feature: Java library consumes external Java library

- Reporting

# Feature: Legacy JVM language plugins declare and consume JVM library

- JVM component can consume legacy JVM project
- Legacy JVM project can consume JVM library
- Change dependency reporting to present project as a JVM component

# Feature: Domain specific usages

- Custom component declares additional usages and associated dependencies.
- Custom binary provides additional usages
- Reporting
