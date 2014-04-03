
This spec describes some work to allow plugins to define the kinds of components that they produce and consume.

## A note on terminology

There is currently a disconnect in the terminology used for the dependency management component model, and that used
for the component model provided by the native plugins.

The dependency management model uses the term `component instance` or `component` to refer to what is knoen as a `binary`
in the native model. A `component` in the native model doesn't really have a corresponding concept in the dependency
management model (a `module` is the closest we have, and this is not the same thing).

Part of the work for this spec is to unify the terminology. This is yet to be defined.

For now, this spec uses the terminology from the native component model, using `binary` to refer to what is also
known as a `component instance` or `variant`.

# Stories

## Feature: Plugins produce and consume local binaries with custom type

### Plugin declares a structured binary type

- Plugin registers some meta-data about a binary type.
    - binary type is identified by some fully qualified name.
    - the usages that binaries of this type provide.
    - the artifacts types included for each usage.
    - the meta-data must declare a default usage.
- API should allow this meta-data to be inferred from a Java interface that represents the binary type.

### Plugin declares a local binary that it produces

- Every project produces an implicit 'legacy' or 'untyped' binary
    - This binary will be used to transition from the existing model to the component model, and will be deprecated and removed
      once the work described in this spec has been completed.
    - The dependency meta-data for this binary is backed by the project's configurations.
- A plugin can register a binary that it produces for a given project.
- API should allow a way to map the properties of this object to the dependency meta-data for the binary:
    - The binary type.
    - The dependencies for each usage.
    - The artifacts for each usage.
- API should allow a convenience to register the binary and its meta-data as a single operation.
- To resolve a project dependency:
    - When the dependency include a 'configuration' override, use that project configuration.
    - Otherwise, resolve using the dependencies and artifacts from the legacy binary's default usage.

#### Open issues

- Task dependencies on artifacts for a usage.
- Resolution result should use binary's id
- Dependencies report should use binary's display name.

### API to resolve dependencies for a given binary type and usage

- Add a new consumer API that allows a graph of dependencies to be resolved and:
    - Allows the expected binary type to be declared.
    - Allows the usage to be declared.
    - Allows the consuming binary to be declared.
- To resolve a project dependency:
    - When the dependency include a 'configuration' override, use that project configuration.
    - Otherwise, select a matching binary
        - Select the binary with the declared type, if any. Fall back to the legacy binary if none. Fail if multiple such binaries.
        - Resolve using the dependencies and artifacts from the nominated usage.
        - Do not allow any 'artifact' overrides in this kind of dependency.

#### Open issues

- The new dependency sets should be visible in the `dependencies` report.
- Probably need to retro-fit this to `Configuration`.

## Feature: Core plugins produce and consume jvm library binaries

### Core plugins define jvm library binary type

- Core plugins define a jvm library binary type.
    - Compile and runtime usages.
    - Has jar artifacts.
    - Has source and javadoc artifacts.
- Artifact query API uses binary meta-data to produce results, rather than hard-coded in the implementation.

#### Open issues

- Make a binary specific meta-data view available in the resolution result.
- Make the binary specific artifact types available in the consumer.

### Legacy jvm plugins declare the binaries that they produce

- `java` plugin declares a jvm library binary.
- The usage meta-data of this binary is backed by the project's configurations.
- The API should allow the `java` plugin to decorate the legacy binary to attach jvm library meta-data to it.

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

## Feature: Core plugins produce and consume jvm libraries

This feature refers to jvm library components, as an aggregate of jvm library binaries.

### Plugin declares a custom structured component type

- Plugin registers some meta-data about a component type:
    - Component types are identified by a fully qualified name
    - The binary types the component can be packaged as
- API allows component meta-data to be inferred from some Java interface.

### Plugin declares the components it produces

- Plugin can declare the components it produces for a given project.
- Each component can have one or more binaries associated with it.
- Binaries of all components are considered when resolving a project dependency.

### Core plugins declare jvm library component type

- Declare a jvm library component type
    - Packaged as a jvm library binary.

### Dependency can be declared on a library

- Plugins declares that component type represents a library.
- Change project dependencies to allow a 'library' attribute to be specified.
    - Cannot use with 'configuration' or 'artifact' overrides with 'library'.
- When resolving such a dependency, consider only the binaries of the requested library component.
    - Fail if not exactly one such binary.

### Java component plugins produce and consume test fixtures

Note: this story assumes that the new Java component plugins have been implemented sufficiently to allow
test fixtures to be declared.

- Library component can have a test fixture library associated with it.
- Plugin attaches test fixture binaries to production binaries.
- Test compilation classpath includes compile usage of test fixtures of all production and test dependencies.
- Test runtime classpath includes runtime usage of test fixtures of all production and test dependencies.

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
