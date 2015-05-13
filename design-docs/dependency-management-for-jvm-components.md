
This spec outlines work required to introduce dependency management for JVM based components.

# Approach

The goal is to support dependency graphs made up of components built from Java source, that depend on other components
which present some Java API.

Specifically the following components that consume some other library:

- Java library
- Custom library or application built from Java

And the following producers:

- Java library
- Custom library that provides a Java API

An example:

<img src="img/jvm_dependency_management.png"/>

External components are out of scope for this work.

The work can be broken down into:

1. An initial DSL to declare the dependencies of a Java source set owned by a Java library.
2. A basic implementation to honour these dependencies at compile time. Only for local Java libraries that consume other Java libraries.
3. Allow a custom component to be built from Java source with dependencies. Only consume local Java libraries.
4. Allow a custom component to provide a Java API. Can consume from Java libraries and custom components.
5. Support multiple variants of a Java library or custom component.

Later work:

1. Declare the API dependencies of a Java library.
2. Support for runtime dependencies.
3. TBD - reporting, etc.

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

Model `JavaSourceSet.dependencies` as a mutable collection of library requirements (that is libraries that are required, not the requirements of a library),
with conveniences to add items to the collection.

It should be possible to query the set of requirements. For example, model `JavaSourceSet.dependencies` as a `ManagedSet`.

Out of scope:

- Resolving or using the dependencies. This story is simply to get a basic DSL in place.
- Provide any public API or DSL to query the resolved dependencies. Resolution will be internal for this feature.

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

- Wire in resolution to Java compilation
    - Change the `JavaLanguagePlugin.Java` transformation to set the `classpath` to a `FileCollection` implementation that will perform the dependency resolution.
    - Ignore the existing `JavaSourceSet.classpath` property. It is used by the legacy Java plugin but is empty for the source sets created by rules.
- Entry point to resolution should be `ArtifactDependencyResolver`.
    - This is a build scoped service.
    - Extract some interface out of `ConfigurationInternal` that does not extend `Configuration` and change `ArtifactDependencyResolver` to accept this instead
      of `ConfigurationInternal`. Change `ConfigurationInternal` to extend this or create an adapter from `ConfigurationInternal` to this new type.
    - Pass in an implementation that represents the consuming Java source set. Can ignore dependencies at this stage.
    - Can pass in an empty set of repositories for this feature.
- Create the resolve meta-data for the consuming library
    - `DependencyGraphBuilder` currently converts parts of `ConfigurationInternal` into resolve meta-data using a `ModuleToComponentResolver`.
      Change the signature of this resolver so that it accepts the type introduced above, rather than a `ModuleInternal` and set of `ConfigurationInternal` instances.
    - Use some composite converter that can build a `ComponentResolveMetaData` for the consuming Java library.
      Should be able to make use of `DefaultLocalComponentMetaData` to assemble this.
    - For now, don't attach any dependencies or artifacts to the resolve meta-data. It should be possible at this point to perform the resolve (but receive an empty result).
- Provide a way to resolve project dependencies
    - Introduce a new subtype of `ComponentSelector` to represent a library selector.
    - For each dependency declared by the source set include a library selector in the component resolve meta-data.
    - Add a library resolver that implements `DependencyToComponentIdResolver` and `ComponentMetaDataResolver`. This would be used where `ProjectDependencyResolver`
      currently is used (can also use this as an example). Can include both resolvers in the chain created by `DefaultDependencyResolver`, so don't need to make
      this configurable.
    - Library resolver should close the `components` for the target project, then select a matching component. Fail as described above if no match.
      Can return empty meta-data for the matching component for this story.

## Story: API of required libraries is made available when Java source set is compiled

Resolve the task dependencies and artifacts for the compile time dependency graph for a Java source set, and make the result available at compile time.

- When a Java library is to be compiled, determine the tasks required to build the API of its required libraries.
- When a Java library is compiled, provide a classpath that contains the API of its required libraries.
- API of a Java library is its Jar binary only.
- Error cases:
    - Java library does not have exactly one Jar binary.

Out of scope:

- Transitive API dependencies.
- API or DSL to query the resolved classpath.
- Validation of target platform.

### Implementation:

The implementation should continue to build on the dependency resolution engine.

- TBD

## Feature backlog

Improve resolution implementation: transitive API dependencies, error messages, etc.

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
- Select correct variant of custom library
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
