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

### Implementation

The implementation should continue to build on the dependency resolution engine.

- Create a `Factory` which allows dependency resolver providers to be instantiated on a request basis.
    - A request in this context is a single dependency resolution, that is
to say a call to `DefaultDependencyResolver#resolve`.
    - The factory should accept a `ResolveContext` instance
    - Update `DefaultDependencyResolver` to use the factory to instantiate a new `ResolverProvider`, providing it with a `ResolveContext`.
- Replace direct instantiation of `LocalLibraryDependencyResolver` in `BuildScopeServices` with a call to the factory
    - Create a `JavaLibraryResolverProvider` which accepts a `ResolveContext` as a constructor argument and delegates to a `LocalLibraryDependencyResolver`
    - Update `LocalLibraryDependencyResolver` to accept a `JvmBinarySpec` as a constructor argument. This spec provides information to the resolver about the usage of the library
- Update `LocalLibraryDependencyResolver` to use the `JavaPlatform` from the usage information to select the appropriate version of the dependency
- Update `LocalLibraryDependencyResolver` to provide a meaningful error message in case no appropriate variant is applicable
- `LocalLibraryDependencyResolver` should *not* store the `ResolveContext` instance. It is the responsibility of the plugin `ResolverProvider` to extract the appropriate
information from `ResolveContext` to create the `LocalLibraryDependencyResolver`

### Test cases

- Given library `a` requires library `b`
    - When `a` targets Java 6 and Java 7, and `b` targets Java 6, then both binaries of `a` are built against `b` Java 6.
    - When `a` targets Java 6 and Java 7, and `b` targets Java 6 and Java 7, then `a` Java 6 is built against `b` Java 6 and `a` Java 7 is built against `b` Java 7.
    - When `a` targets Java 6 and Java 7, and `b` targets Java 6, Java 7 and Java 8, then `a` Java 6 is built against `b` Java 6 and `a` Java 7 is built against `b` Java 7.
    - When `a` targets Java 6 and Java 7, and `b` targets Java 6 and Java 7, then `a` Java 6 is built against `b` Java 6 and `a` Java 7 is built against `b` Java 7 and the order
    of declaration of the platforms on `b` doesn't matter
    - When `a` targets Java 6 and `b` targets Java 7 then `a` Java 6 is not buildable
        - Error message should detail available platform variants of `b`
    - When `a` targets Java 6 and Java 7, and `b` targets Java 7 and Java 8, then `a` Java 6 is not buildable and `a` Java 7 is built against `b` Java 7
      - Reasonable error message when attempting to build `a` Java 6
      - Error message should detail available platform variants of `b`
    - When `a` targets Java 6, and `b` has 2 different variants for Java 6
        - Error attempting to build `a` outlines multiple variants with compatible platform
    - When `a` targets Java 6 and Java 7, and `b` has 2 different variants for Java 6
        - Error attempting to build `a` Java 6 outlines multiple variants with compatible platform
        - Error attempting to build `b` Java 7 outlines no compatible variants