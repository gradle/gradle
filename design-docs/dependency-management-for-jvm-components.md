
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

# Completed work

- Feature 1: Build author declares dependencies of Java library
- Feature 2: Custom component built from Java source

[Done](done/dependency-management-for-jvm-components.md)

## Open issues

- Dependency container is not reachable through a public API.
- We should have some validation around unmanaged types overriding managed types.
- Need to test that a managed property of type `ModelMap<ModelMap<UnmanagedType>>` throws an error.

## Feature backlog

- Allow library to expose Jar, classes dir or any combination as its Java API.
- Allow plugin to use compiled classes from a Java source set to build a custom binary.
- Plugin declares Jar or classes as intermediate output rather than final output.
- Add dependency set to JarBinarySpec to allow dependencies to be tweaked.
- Expose a way to query the resolved compile classpath for a Java source set.
- Plugin author defines target Java platform for Jar binary
- Change language transforms implementation to fail at configuration time when no rule is available to transform a given input source set for a binary.
    - This will require using `LanguageTransform`s in Scala
    - Windows resource sets on non-windows builds fail with the current `LanguageTransform.applyToBinary()` implementation
- Fail when no rule is available to define the tasks for a `BinarySpec`.
    - Will need to change `@BinaryTasks` implementation to fail at configuration time when no rule is available to build a given binary.


# Feature: Java library consumes external Java library

- Resolve libraries from binary repositories, for libraries with a single variant
    - Maven repo: assume API dependencies are defined by `compile` scope.
    - Ivy repo: look for a particular configuration, if not present assume no API dependencies (or perhaps use `default` configuration)
    - Assume no target platform, and assume compatible with all target platforms.

# Feature: Fully featured dependency resolution for local Java libraries

- Declare and consume API & runtime dependencies
    - ~Need to declare transitive API dependencies~
    - ~Consumes API dependencies at compile time~
    - Consumes runtime dependencies at runtime
    - Handle compile time cycles.
- Improvements to dependency management
    - Declare dependencies at component, source set and binary level
    - Allow a `LibrarySpec` instance to be added as a dependency.
    - Make dependency declarations managed and immutable post resolve
- Fully featured dependency resolution
    - API to query the various resolved classpaths
    - Configure the resolution strategy for each usage
    - Configure resolution rules for each usage
    - Wire in dependency resolution events
    - Include in dependency reports

# Feature: Legacy JVM language plugins declare and consume JVM library

- JVM component can consume legacy JVM project
- Legacy JVM project can consume JVM library
- Change dependency reporting to present project as a JVM component

# Feature: Domain specific usages

- Custom component declares additional usages and associated dependencies.
- Custom binary provides additional usages
- Reporting
- use component model terminology in error messages and exception class names.
