
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

# Feature 1: Build author declares dependencies of Java library

This feature adds support for compile time dependencies between Java libraries.

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

It should be possible to query the set of requirements. For example, model `JavaSourceSet.dependencies` as a `ModelSet`.

Out of scope:

- Resolving or using the dependencies. This story is simply to get a basic DSL in place.
- Provide any public API or DSL to query the resolved dependencies. Resolution will be internal for this feature.

### Implementation

- New classes should live in `platformBase` or `platformJvm` projects. Avoid adding classes to `core` or `dependencyManagement`

## Story: Resolve required libraries of Java source set

Resolve enough of the compile time dependency graph for a Java source set to validate that the required libraries exist.

- When a Java library is compiled, fail resolution when that is a dependency declaration for which no matching Java library can be found
- Error cases:
    - Not found, error message should include list of available components in target project.
        - Project dependency, not exactly one Java library in target project.
        - Project + library dependency, no component with given name.
    - Unsupported type, error message should include information about supported component types
- Direct dependencies only.
- Cycles:
    - Should be allowed at resolve time. It is entirely possible to handle this case at compile time (using source path, for example). For this feature,
      the failure can happen later due to the cycle between jar tasks.
    - Will be required for native support.

Out of scope:

- Building the required library Jars or making the library Jars available at compile time.
- API or DSL to query the resolved graph.
- Making any state of `JavaSourceSet` managed.

### Test cases

- Can require a library in the same project.
- Can require multiple different libraries in another project.
- Can require self.
- Can have a cycle in the graph.
- Exercise the error cases above.

### Implementation:

The implementation *must* make use of the dependency resolution engine, and refactor the resolution engine where required:

- Wire in resolution to Java compilation
    - Change the `JavaLanguagePlugin.Java` transformation to set the `classpath` to a `FileCollection` implementation that will perform the dependency resolution.
    - Ignore the existing `JavaSourceSet.classpath` property. It is used by the legacy Java plugin but is empty for the source sets created by rules.
- Entry point to resolution should be `ArtifactDependencyResolver`.
    - This is a build scoped service.
    - Extract some interface out of `ConfigurationInternal` that does not extend `Configuration` and change `ArtifactDependencyResolver` to accept this instead
      of `ConfigurationInternal`. Change `ConfigurationInternal` to extend this or create an adapter from `ConfigurationInternal` to this new type.
    - This new type represents a 'resolve context' (for now). There are 2 parts to this:
        - Some information about the consumer.
        - Some information about the usage, that is, what is the consumer going to do with the result?
    - Pass in an implementation that represents the consuming Java source set. Can ignore dependencies at this stage.
    - Can pass in an empty set of repositories for this feature.
- Create the resolve meta-data for the consuming library
    - `DependencyGraphBuilder` currently converts parts of `ConfigurationInternal` into resolve meta-data using a `ModuleToComponentResolver`.
      Change the signature of this resolver so that it accepts the type introduced above, rather than a `ModuleInternal` and set of `ConfigurationInternal` instances.
    - Use some composite converter that can build a `ComponentResolveMetaData` for the consuming Java library.
      Should be able to make use of `DefaultLocalComponentMetaData` to assemble this.
    - Introduce a new public subtype of `ComponentIdentifier` to represent a library component. Use this as the id in the meta-data.
    - Currently the meta-data includes a `ModuleVersionIdentifier`, used for conflict resolution. Given that there are currently no external dependencies referenced
      in the graph, can use something like (project-path, library-name, project-version).
    - For now, don't attach any dependencies or artifacts to the resolve meta-data. It should be possible at this point to perform the resolve (but receive an empty result).
- Provide a way to resolve project dependencies
    - Introduce a new public subtype of `ComponentSelector` to represent a library selector.
    - For each dependency declared by the source set include a library selector in the component resolve meta-data.
    - Add a library resolver that implements `DependencyToComponentIdResolver` and `ComponentMetaDataResolver`. This would be used where `ProjectDependencyResolver`
      currently is used (can also use this as an example). Can include both resolvers in the chain created by `DefaultDependencyResolver`, so don't need to make
      this configurable.
    - Library resolver should close the `components` for the target project, then select a matching component. Fail as described above if no match.
      Can return empty meta-data for the matching component for this story.

Avoid adding specific knowledge about Java libraries to the `dependencyManagement` project. Instead, the `platformJvm` project should inject this knowledge.
Can use the service discovery mechanism to do this.

## Story: API of required libraries is made available when Java source set is compiled

Resolve the task dependencies and artifacts for the compile time dependency graph for a Java source set, and make the result available at compile time.

- When a Java library is to be compiled, determine the tasks required to build the API of its required libraries.
- When a Java library is compiled, provide a classpath that contains the API of its required libraries.
- API of a Java library is its Jar binary only, and no transitive dependencies. The runtime of the Java library, which is to be added in later stories, will include
  the transitive dependencies.
- Error cases:
    - Java library does not have exactly one Jar binary. For example, for a library with multiple target platforms.
        - Error message should include details of which binaries are available.
- Include a sample.

Out of scope:

- Transitive API dependencies.
- API or DSL to query the resolved classpath.
- Validation of target platform.

### Test cases

- Given `a` requires `b` requires `c`.
    - When the source for `a` is compiled, the compile classpath contains the Jar for `b` but not the Jar for `c`.
    - When `c` does not exist, can successfully resolve the classpath of `a`. Cannot actually build the Jar for `a` because resolution of the classpath of `b` will fail.
- Reasonable error message when building a library with a dependency cycle.
- Jar is built when library depends on itself.
- Error cases as above.

### Implementation:

The implementation should continue to build on the dependency resolution engine.

- When the meta-data for a Java library is assembled, attach the Jar
    - Select the `JarBinarySpec` to use from the Java library's set of binaries. Fail if there isn't exactly one such binary.
    - Add a `PublishArtifact` implementation for this `JarBinarySpec`.

## Story: Compatible variant of Java library is selected

When a Java library has multiple target Java platforms, select a compatible variant of its dependencies, or fail when none available.

- When compiling Java library variant for Java `n`, then from the target library select the Jar binary with the highest target platform that is <= 'n'
- Error cases:
    - Fail when there is no Jar binary with compatible target platform. For example, when building for Java 7, fail if a required library has target platform Java 9.
      Error message should include information about which target platforms are available.

### Test cases

- Given library `a` requires library `b`
    - When `a` targets Java 6 and Java 7, and `b` targets Java 6, then both binaries of `a` are built against `b` Java 6.
    - When `a` targets Java 6 and Java 7, and `b` targets Java 6 and Java 7, then `a` Java 6 is built against `b` Java 6 and `a` Java 7 is built against `b` Java 7. 
- Error cases as above.

## Story: Resolution failures explain why library selector was attempted

The goal is parity with the error reporting for `Configuration` resolution failures.

- Show the path through the graph from the consumer to the problematic selector 
- Give a reasonable display name for the consumer, nodes in the graph and the selectors
- Collect all selector resolve failures

## Feature backlog

- documentation
- resolve libraries from binary repositories, for libraries with a single variant
    - Maven repo: assume API dependencies are defined by `compile` scope. 
    - Ivy repo: look for a particular configuration, if not present assume no API dependencies (or perhaps use `default` configuration)
    - Assume no target platform, and assume compatible with all target platforms. 
- declare transitive API dependencies
- use component model terminology in error messages and exception class names.
- reporting
- runtime dependencies
- allow another dependency set to be added as input.
- allow a `LibrarySpec` instance to be added as a requirement.
- make dependency declarations managed and immutable post resolve

# Feature 2: Custom component built from Java source

This feature allows a plugin author to define a component type that is built from Java source and Java libraries.

## Story: Jar binary is built from input source sets 

Allow multiple Jar binaries to be built from multiple Java source and resource sets, to somewhat approximate the Jars built for Android components.

- A source set may be input to multiple binaries.
    - These are not owned by the binary, they are inputs to the binary.
    - A binary may also own some source sets, these are also implicit inputs to the binary. 
- A binary may be built from multiple input source sets
- A Jar binary can be built from one or more input Java and resource source sets.
- Error cases:
    - Fail at configuration time when there is no language rule available to build the binary from a given input source set. 

### Test cases

- Given a custom component: 
    - Java Source set `a` and `b`
    - Resources set `res-a` and `res-b`
    - Binaries `jar-a` and `jar-b`
    - Can build binaries with `a`, `b`, `res-a` and `res-b` as inputs, plus a source set owned by the binary.  
        - Only the expected compiled classes and resources end up in each jar.
- Error cases above.

### Implementation

- Changes to `BinarySpec`
    - Add `ModelMap <LanguageSourceSet> getSources()` and deprecate `getSource()`.
    - Change implementation of `sources(Action)` to execute the action against the return value of `getSources()`. 
    - Add `Set<LanguagesSourceSet> getInputs()`.
    - Every source set created in `BinarySpec.sources` should also appear in `BinarySpec.inputs`.
    - Any source set instance can be added to `BinarySpec.inputs`.
    - Remove internal `Set<LanguageSourceSet> getAllSources()`, this is replaced by `getInputs()`.
- Change component report to report on all inputs for a binary.
- Change language transforms implementation to fail at configuration time when no rule is available to transform a given input source set for a binary.
- Change base plugins to add component source sets as inputs to its binaries, rather than owned by the binary.
- Remove special case for inputs from `DefaultNativeTestSuiteBinarySpec` and reuse general mechanism.

## Story: Plugin author defines a custom component built from Java source

Define a custom component that produces a Jar binary from Java source and resources. When the Jar is built, the compile time
dependencies of the source are also built and the source compiled.

- Allow a `JarBinarySpec` to be added to the binaries container of any component.
- When `jvm-component` plugin is applied, the Jar binary should be buildable.
- API dependencies should be built before the Java source is compiled.
- Each Java source set may have different dependencies. The compile classpaths for each source set should be isolated from the others.
- Default Java platform and tool-chains are used to build the binary. For this story, the Java platform is not configurable for these binaries.
- Dependency resolution honors the target platform of the consuming custom Jar binary.
- Error cases:
    - Fail when no rule is available to define the tasks for a `BinarySpec`.

### Test cases

- Given a custom component: 
    - Source set `a` depends on Java library `lib1`
    - Source set `b` depends on Java library `lib2`
    - Resources set `res-a` and `res-b`
    - Then when the binary is built:
        - Source from `a` is compiled against the API of `lib1` only and the compiled classes end up in the Jar.
        - Source from `b` is compiled against the API of `lib2` only and the compiled classes end up in the Jar.
        - Resources from `res-a` and `res-b` end up in the Jar.
        - Jar contains only the above items.
- Error cases above.

### Implementation

- Need to replace `ComponentSpecInternal.getInputTypes()`, possibly by moving to `BinarySpecInternal` for now.
- Will need to rework `JvmComponentPlugin` so that `ConfigureJarBinary` is applied in some form to all `JarBinarySpec` instances, not just those that belong to a Jvm library.
- Change `@BinaryTasks` implementation to fail at configuration time when no rule is available to build a given binary.

## Story: Plugin author defines a custom library that provides a Java API

Define a custom library that produces a Jar binary from Java source. When another Java or custom component requires
this library, the Jar binary is built and made available to the consuming component.

- Change dependency resolution to select a `LibrarySpec` instance that provides a `JarBinarySpec`.
    - For a requirement that specifies a `project` and no `library`, select the library that may provide a Jar binary.
        - Fail when there is not exactly one such library.
    - Given a selected library, fail when that library does not provide exactly one compatible `JarBinarySpec`.
- Dependency resolution honors the target platform of the consuming Jar binary, so that all API dependencies must be compatible with the target platform.
- Allows an arbitrary graph of custom libraries and Java libraries to be assembled and built.

## Story: Plugin author defines variants for custom component

Plugin author extends `JarBinarySpec` to declare custom variant dimensions:

    @Managed
    interface CustomBinarySpec extends JarBinarySpec { 
        @Variant
        String getFlavor()
        
        @Variant
        BuildType getBuildType() // Must extend `Named`
    }

Each property marked with `@Variant` defines a variant dimension for this kind of binary.

- Property type must either be a String or a type that extends `Named`.
- Annotate `JvmBinarySpec.targetPlatform`, the properties of `NativeBinarySpec` and `PlayApplicationBinarySpec`.
- Component report shows variants for a binary.
- Not all binaries for a library need to have the same set of dimensions.
- For this story, ignore variants when resolving dependencies.
- For this story, assume that it is possible to add managed subtypes of `JarBinarySpec`.

### Implementation

- Meta-data must be extracted once and cached as part of the `ModelSchema` associated with the type.
- Replace special case rendering of binary variant dimensions from the component report with general purpose reporting.

## Story: Plugin author defines variants for custom library

Change dependency resolution to honor the variant dimensions for a custom component.

- When resolving dependencies of a binary with variant dimensions `D` with values `d`:
    - Match any binary with variant dimensions `E` and values `e` where the intersection of `D` and `E` is not empty, and the values of `e` from the intersection are compatible. 
      with the values from `d`.
    - Fail when there is not exactly one such binary.
- To determine whether the variant values are compatible:
    - Exact match on name 
    - Special case compatibility for `JavaPlatform`.
- For example:
    - Resolving for a binary with `(platform, buildType, screenSize)` from a library with binaries with `(platform)`, select all candidates with compatible `platform` and
      ignore the other dimensions.
    - Resolving for a binary with `(platform)` from a library with binaries with `(platform, buildType, screenSize)`, select all candidates with compatible `platform`.      
    - Resolving for a binary with `(platform, screenSize)` from a library with binaries with `(platform, buildType)`, select all candidates with compatible `platform`.      

## Feature backlog

- Allow library to expose Jar, classes dir or any combination as its Java API.  
- Allow plugin to use compiled classes from a Java source set to build a custom binary.
- Plugin declares Jar or classes as intermediate output rather than final output.
- Add dependency set to JarBinarySpec to allow dependencies to be tweaked.
- Expose a way to query the resolved compile classpath for a Java source set.
- Plugin author defines target Java platform for Jar binary

# Feature 3: TBD 

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
- Handle compile time cycles.
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
- Remove the need for every component to have a module version id.

# Feature: Legacy JVM language plugins declare and consume JVM library

- JVM component can consume legacy JVM project
- Legacy JVM project can consume JVM library
- Change dependency reporting to present project as a JVM component

# Feature: Domain specific usages

- Custom component declares additional usages and associated dependencies.
- Custom binary provides additional usages
- Reporting
