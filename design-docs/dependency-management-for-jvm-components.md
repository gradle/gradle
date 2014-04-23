
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

## Feature: Plugins produce and consume local jvm library binaries

### Rename 'binary' to 'component'

TBD

### API to resolve dependencies on a graph of binaries

- Add a new consumer API that allows a graph of binaries to be resolved. Takes as input:
    - A set of dependency declarations. Can be a set of `Dependency` instances at this stage.
    - The expected binary type. Only jvm library binaries will be supported for this story.
    - The usage. Only 'compile' and 'runtime' will be supported for this story.
- Produces a `ResolutionResult` and an `ArtifactResolutionResult`.
- External module dependencies are resolved as per the `Configuration` API.
    - All usages should resolve using the `default` configuration, or the configuration specified using an override.
- Project dependencies are resolved as per the `Configuration` API.
    - All usages should resolve using the `default` configuration.
    - Fail if a project dependency contains configuration, artifact or exclude overrides
- A custom plugin can use this API to resolve a graph of dependencies.

#### User visible changes

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

#### Test cases

- Project can use new resolution API to:
    - Use jars from external module dependencies, both direct and transitive
        - referenced by override configuration of an external module dependency
        - referenced by artifact and classifier overrides on an external module dependency
    - Use jars from another project that applies the `java` plugin, both direct and transitive
- Fails if a project dependency contains configuration, artifact or exclude overrides

#### Open issues

- Should be possible to declare the consuming binary, for conflict resolution.
- The new dependency sets should be visible in the dependency reports.
- Probably need to retro-fit this to `Configuration`.
- Task dependencies on artifacts for a usage.

### Plugin declares a local jvm library binary that it produces

This story introduces the ability to declare a jvm library binary and some basic interoperability between old and new resolution APIs, and between
new and old JVM plugins.

- A custom plugin can register a binary that it produces for a given project. Only jvm library binaries are supported for this story.
- Add some convenience to allow a plugin to implement or define a jvm library binary.
- To resolve a project dependency via old API:
    - When the dependency include a 'configuration' override, use that project configuration.
    - When the project produces a binary, resolve using the default usage for that binary. Fail when multiple binaries.
        - Do not allow 'artifacts' or 'exclude' overrides on dependency.
    - Otherwise, use the `default` project configuration.
- To resolve a project dependency via new API:
    - Do not allow configuration, artifact or exclude overrides
    - When the project produces a binary, resolve using the requested usage for that binary. Fail when multiple binaries.
        - Do not allow 'artifacts' or 'exclude' overrides on dependency.
        - Fail for unknown usage.
    - Otherwise, use the `default` project configuration.

#### Test cases

- Project that uses `java` plugin can use a jvm library binary produced by another project that does not use the `java` plugin.
    - Uses runtime dependencies and artifacts.
- Project with custom plugin that uses new resolution API can use a jvm library binary produced by another project.
    - Can require different dependencies at compile and runtime.
    - Can provide different artifacts at compile and runtime.

#### Open issues

- Task dependencies on artifacts for a usage.
- Resolution result should use binary's id.
- Dependencies report should use binary's display name.

### Legacy jvm plugins declare local jvm library binary

- The `java` plugin declares a jvm binary, backed by the project configurations.
- To resolve a project dependency via new API:
    - Fail if target project does not produce a jvm binary.
    - Do not allow configuration, artifact or exclude overrides

### Plugin declares a custom structured binary type

- Plugin registers some meta-data about a binary type.
    - binary type is identified by some fully qualified name.
    - the usages that binaries of this type provide.
    - the artifacts types included for each usage.
    - the meta-data must declare a default usage.
- API should allow this meta-data to be inferred from a Java interface that represents the binary type.
- API should allow a way to map the properties of this object to the dependency meta-data for the binary:
    - The binary type.
    - The dependencies for each usage.
    - The artifacts for each usage.
- Some base jvm plugin defines jvm library binary.
- New resolution API uses this meta-data to validate dependency.
- To resolve a project dependency, select the binary with the requested type. Fail if not exactly one such binary.

#### Open issues

- Should be possible to extend the jvm library binary type.
- Make a binary specific meta-data view available in the resolution result.
- Make the binary specific artifact types available in the consumer.

## Feature: Core plugins produce and consume jvm library components

### Java component plugins declare the binaries that they produce

Note: this story assumes that the new java component plugins have been implemented sufficiently to actually
produce something.

- The java component plugins declare the jvm library binaries that they produce.
    - Compile usage includes the jar and any dependencies of the binary's API.
    - Runtime usage includes the jar and any runtime dependencies of the binary.
- At this stage, only a single binary will be supported.

### Java component plugins consume jvm library binaries

- The java component plugins use the new consumer API to resolve dependencies as jvm library binaries.
- Compilation classpath contains compile usage of all production dependencies.
- Runtime classpath contains runtime usage of all production dependencies.
- Test compilation classpath contains binary + compile usage of all production and test dependencies
- Test runtime classpath contains binary + runtime usage of all production and test dependencies.

#### Open issues

- Apply to legacy plugins too.

### Plugin declares the jvm library components it produces

- Plugin can declare the jvm library components it produces for a given project. Only jvm libraries are supported at this stage.
- Each component can have one or more binaries associated with it.
- Binaries of all components are considered when resolving a project dependency.

### Dependency can be declared on a library

- Plugins declares that component type represents a library.
- Change project dependencies to allow a 'library' attribute to be specified.
    - Cannot use with 'configuration' or 'artifact' overrides with 'library'.
    - Cannot use 'transitive' flag or 'exclude' rules overrides with 'library'.
- When resolving such a dependency, consider only the binaries of the target library component.
    - Fail if not exactly one such binary.

### Plugin declares a custom structured component type

- Plugin registers some meta-data about a component type:
    - Component types are identified by a fully qualified name
    - The binary types the component can be packaged as
- API allows component meta-data to be inferred from some Java interface.
- Core plugins declare a jvm library component type
    - Packaged as a jvm library binary.

### Java component plugins produce and consume test fixtures

Note: this story assumes that the new Java component plugins have been implemented sufficiently to allow
test fixtures to be declared.

- Library component can have a test fixture library associated with it.
- Plugin attaches test fixture binaries to production binaries.
- Test compilation classpath includes compile usage of test fixtures of all production and test dependencies.
- Test runtime classpath includes runtime usage of test fixtures of all production and test dependencies.

### Java component plugins produce and consume source and javadoc artifacts

#### Open issues

- Apply to legacy java plugins too.

## Feature: Plugin produces and consumes component variants

### Plugin declares variant dimensions for component type

- Plugin declares the variant dimensions that may be relevant for a component of a given type.

### Plugin declares variant dimensions for local binary

- Plugins declares the values of the variant dimensions for a given binary.

### Project consumes variant dimension of local binaries

- New consumer API allows variant dimensions to be declared when resolving the graph.
- Resolution uses exact match on variant dimension.
- Dependency reports need to show the per-dimension dependencies.

#### Open issues

- Apply to legacy jva plugins too.

### Plugin can declare compatibility rules for variant dimensions

- Plugin can select best match from the set of candidates for each variant dimension.

## Feature: Dependency management for native binaries

- Native plugins declare native component types.
- Native plugins declare binaries and components.
- Native plugins consume using new consumer API.
    - Replaces existing configurations, `NativeDependencySet` and so on.
- Prebuilt libraries are a kind of local binary.

## Feature: Plugin extends some other binary or component type

- Allow a new binary type to be derived some some other type (eg Android library is-a jvm library).
- Allow artifacts to be attached to a binary.
- Allow usages to be attached to a binary.
- Allow binaries to be attached to a component.
- Allow variant dimensions to be attached to a component.
- Statically and ad hoc.

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
