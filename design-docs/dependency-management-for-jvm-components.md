
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

### Test cases

- Given the example above:
    - ~~source files in all source sets of A can reference public types from library B~~
    - ~~source files in C fail to compile if they reference public types from library B~~
    - ~~same tests above for a library B defined in a different project~~
    - ~~same tests above given A declares component level dependencies on both libraries, B from the same project and B from a different project~~
- Given a library dependency declared at both the component and api levels:
    - ~~source files in all source sets can reference public types from said library~~
    - ~~the API of said library is considered part of the API of the consuming library~~
- Given a library dependency declared at both the component and source set levels:
    - ~~source files in all source sets can reference public types from said library~~
    - ~~the API of said library is _not_ considered part of the API of the consuming library~~
- Given a library dependency for a library which cannot be found:
    - ~~compilation should fail with a suitable error message pointing to the dependency declaration~~

## Story: Java library sources are compiled against library Jar resolved from Maven repository

- Extend the dependency DSL to reference external modules:

    ```groovy
    model {
        components {
            main(JvmLibrarySpec) {
                dependencies {
                    // external module spec can start with either group or module
                    group 'com.acme' module 'artifact' version '1.0'
                    module 'artifact' group 'com.acme' version '1.0'

                    // shorthand module id syntax
                    module 'com.acme:collections:1.42'

                    // passing a module id to library should fail
                    library 'com.acme:collections:1.42'

                    // existing usage remains
                    project ':foo' library 'bar'
                    library 'bar' project ':foo'
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
    - ~~Main Jar artifact of module is included in compile classpath.~~
    - ~~Main Jar artifact of compile-scoped transitive dependencies are included in the compile classpath.~~
    - ~~Artifacts from runtime-scoped (and other scoped) transitive dependencies are _not_ included in the compile classpath.~~
- For local component dependencies:
    - ~~Artifacts from transitive external dependencies that are non part of component API are _not_ included in the compile classpath.~~
- ~~Displays a reasonable error message if the external dependency cannot be found in a declared repository~~
- ~~Displays a reasonable error message if a module id is given to `library`~~

### Out of scope

- Rule-based definition of repositories
- Support for custom `ResolutionStrategy`: forced versions, dependency substitution, etc

## Story: Build author defines repositories using model rules

## Story: Plugin author can specialize how a custom component is shown in the components report

### Motivation

The need for this story came about as @lptr and I were investigating how to get the components report to show the API level and component level dependencies of a java library (`JvmLibrarySpec`).

We've discovered there was no mechanism already in place that would let us specialize the reporting behavior for subtypes of `ComponentSpec` although such a mechanism does exist for subtypes of `BinarySpec` via the `TypeAwareBinaryRenderer` class.

At the same time we were reviewing the tidying up of NodeInitializer semantics and found [some code](https://github.com/gradle/gradle/blob/45d3d3fbb8855638bb797ac34ec792f74aafca2b/subprojects/model-core/src/main/java/org/gradle/model/internal/core/DefaultNodeInitializerRegistry.java#L41) dealing with the same problem in a slightly different way using a chain-of-responsibility.

So here they are, three instances of the same and very common problem. So common in fact that is deserving of its own name, *the expression problem*.

Having slightly different solutions in different modules to the same basic problem adds unnecessary complexity and worse, keeps these subsystems closed to extension by plugin authors.

We should devise a solution to the expression problem suitable to our setting and apply it uniformly throughout.

As food for thought here's a sketch of how one of my favorite solutions to the expression problem, protocols as introduced by clojure, could let plugin authors extend behavior to the different types in a hierarchy.

```java

interface PrettyPrinter<T> {
    PrettyDocument print(T subject);
}

class PrettyPrinterProtocolRules extends RuleSource {

    /***
     * Extends the PrettyPrinter protocol to the ComponentSpec type (and subtypes) relying on the PrettyPrinter
     * for LanguageSourceSet to pretty print the component sources.
     */
    @Protocol PrettyPrinter<ComponentSpec> componentPrettyPrinter(PrettyPrinter<LanguageSourceSet> sourceSetPrinter) {
        // Calling `sourceSetPrinter.print(sourceSet)` where `sourceSet` is a `JavaLanguageSourceSet`
        // would trigger the version of the protocol specialized to `JavaLanguageSourceSet` declared below.
        return new PrettyPrinter { ... };
    }

    /***
     * Extends the PrettyPrinter protocol to the JvmLibrarySpec type (and subtypes) delegating common
     * behavior to the ComponentSpec PrettyPrinter acquired via the Protocol definition / meta type.
     */
    @Protocol PrettyPrinter<JvmLibrarySpec> jvmLibraryPrettyPrinter(Protocol<PrettyPrinter<?>> prettyPrinterProtocol) {
        final PrettyPrinter<ComponentSpec> base = prettyPrinterProtocol.specializedTo(ComponentSpec.class);
        // Calling `base.print(component)` where `component` is a `JvmLibrarySpec` would still trigger
        // the `ComponentSpec` version of the protocol defined above.
        // A call to `prettyPrinterProtocol.specializedTo(JvmLibrarySpec.class)` here would be invalid as it leads to a cycle.
        return new PrettyPrinter { ... };
    }

    /***
     * Extends the PrettyPrinter protocol to the JavaSourceSet type (and subtypes).
     */
    @Protocol PrettyPrinter<JavaLanguageSourceSet> javaLanguageSourceSetPrettyPrinter() {
        return new PrettyPrinter { ... };
    }
}
```

## Story: Components report shows component level and api level dependencies of a Java Library

### Implementation

The implementation will rely on the extension mechanism defined in `Plugin author can specialize how a custom component is shown in the components report` to specialize how `JvmLibrarySpec` components are shown in the report.

### Test cases

- Report shows component level dependencies
- Report shows api dependencies


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
