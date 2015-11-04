
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

## Story: Java library sources are compiled against library Jar resolved from Maven repository

- Extend the dependency DSL to reference external libraries:
    ```
    model {
        components {
            main(JvmLibrarySpec) {
                // TODO This is just a placeholder DSL: define a reasonable one.
                dependencies {
                    library group: 'com.acme', name: 'artifact', version: '1.0'
                    library 'com.acme:artifact:1.0'
                }
            }
        }
    }
```

- Reuse existing repositories DSL, bridging into model space.
- Main Jar artifact of maven module is included in compile classpath.
- Main Jar artifact of any compile-scoped dependencies are included transitively in the compile classpath.

- Assume external library is compatible with all target platforms.
- Assume external library declares only one variant.

- Update samples and user guide
- Update newJavaModel performance test?

### Test cases

- For maven module dependencies
    - Main Jar artifact of module is included in compile classpath.
    - Main Jar artifact of compile-scoped transitive dependencies are included in the compile classpath.
    - Artifacts from runtime-scoped (and other scoped) transitive dependencies are _not_ included in the compile classpath.
- For local component dependencies:
    - Artifacts from transitive external dependencies that are non part of component API are _not_ included in the compile classpath.
- Displays a reasonable error message if the external dependency cannot be found in a declared repository

### Out of scope

- Rule-based definition of repositories
- Support for custom `ResolutionStrategy`: forced versions, dependency substitution, etc

## Story: Dependencies report shows compile time dependency graph of a Java library

- Dependency report shows all JVM components for the project, and the resolved compile time graphs for each variant.

## Story: Build author defines repositories using model rules

## Story: Build author defines dependencies for an entire component

- Extend the JvmLibrarySpec DSL with a component scoped `dependencies` block with support for the usual dependency selectors:

```groovy
model {
    components {
        A(JvmLibrarySpec) {
            dependencies {
                library "B"
            }
            sources {
                core(JavaSourceSet) {
                    source.srcDir "src/core"
                }
            }
        }
        B(JvmLibrarySpec) {
        }
        C(JvmLibrarySpec) {
            dependencies {
                library "A"
            }
        }
    }
}
```

- When library A declares a component level dependency on library B, defined in the same project or a different one, then:
    - library B is considered part of the compile classpath of all source sets in library A
    - the API of library B is _not_ considered part of the API of library A unless an explicit api dependency is also declared (which renders the component level dependency redundant)
- Components report should show component level dependencies
### Test cases

- Given the example above:
    - source files in all source sets of A can reference public types from library B
    - source files in C fail to compile if they reference public types from library B
    - same tests above for a library B defined in a different project
    - same tests above given A declares component level dependencies on both libraries, B from the same project and B from a different project
- Given a library dependency declared at both the component and api levels:
    - source files in all source sets can reference public types from said library
    - the API of said library is considered part of the API of the consuming library
- Given a library dependency declared at both the component and source set levels:
    - source files in all source sets can reference public types from said library
    - the API of said library is _not_ considered part of the API of the consuming library
- Given a library dependency for a library which cannot be found:
    - compilation should fail with a suitable error message pointing to the dependency declaration

## Story: Resolve external dependencies from Ivy repositories

- Use artifacts and dependencies from some conventional configuration (eg `compile`, or `default` if not present) an for Ivy module.

## Story: Generate a stubbed API jar for external dependencies

- Generate stubbed API for external Jar and use this for compilation. Cache the generated stubbed API jar.
- Verify library is not recompiled when API of external library has not changed (eg method body change, add private element).
- Dependencies report shows external libraries in compile time dependency graph.

### Implementation

Should reuse the "stub generator" that is used to create an API jar for local projects.

### Test cases

- Dependent classes are not recompiled when method implementation of external dependency has changed
- Dependent classes are recompiled when signature of external dependency has changed

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
