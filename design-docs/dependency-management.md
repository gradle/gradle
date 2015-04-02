This specification is a proposal for a deep reworking of the Gradle dependency management and publication model.

# Why?

* To come up with a concrete plan for solving dependency management problems for projects that are not simply 'build my jar' projects. For example,
  projects that build and publish C/C++ binaries, Javascript libraries, and so on.
* To provide a richer model that will allow projects to collaborate through the dependency management system, making these projects less coupled at
  configuration time. This will allow us to introduce some nice performance and scalability features.
* To allow Gradle to move beyond simply building things, and into release management, promotion, deployment and so on.

# Use cases

- Replace the old dependency result graph with one that is easier to use and consumes less heap space.
- Replace the old dependency result graph with one that better models dependency management.
- Publish and resolve C and C++ libraries and executables.
- Publish and resolve Android libraries and applications.
- Publish and resolve Scala and Groovy libraries which target multiple incompatible Scala/Groovy language versions.
- Publish and resolve some custom component type

# Terminology

## Software component

A logical software component, such as a JVM library, a native executable, a Javascript library or a web application.

## Component instance

Some physical representation of a component. Most components are represented as more than one instance.

From an abstract point of view, component instances are arranged in a multi-dimensional space. The dimensions might include:

- Time, for a component which changes over time. This dimension is often represented by some version number or a source code revision.
- Packaging, which is how the component is physically represented as files.
- Target platform, which is where the instance can run. For example, on a java 6 vs java 8 JVM, or on a 64 bit Windows NT machine.
- Build type, which is some non-functional dimension. For example, a debug development build vs an optimized release build.
- Some component type specific dimensions.

# Requirement

Also known as a 'dependency', this is some description of some requirement of a component which must be satisfied by another component.

# Module version

A component instance with an associated (group, module, version) identifier.

# Implementation plan

See also the completed stories in [dependency-management.md](done/dependency-management.md).

## Story: IDE plugins use dependency resolution result to determine IDE class paths

This story changes the `idea` and `eclipse` plugins to use the resolution result to determine the IDE project classpath.

- Change `IdeDependenciesExtractor` and `JavadocAndSourcesDownloader` to use the resolution result to determine the project and
  external dependencies.

## Story: Dependency resolution result exposes a consumer that is not a module version

This story exposes different kinds of consumers for a dependency graph. The consumer is represented as the root of the dependency resolution result.

- Result `root.id` should return a `ProjectComponentIdentifier` when a project configuration is resolved.
- Result `root.id` should return an opaque `ComponentIdentifier` implementation when any other kind of configuration is resolved.
    - Add an internal implementation that implements only `ComponentIdentifier`. Two such implementations are equal when their display
      names are the same.
    - This implementation should use the configuration's display name for the component display name.
- When there is a project dependency in the graph that refers to the root, the selected component for the dependency should be the same instance
  as `root`.
- The 'required by' message in the resolve exception should use the root's display name, rather than the module version.
- Some internal refactoring to push components down further:
    - Rename internal class `ModuleVersionSelection` and its methods.
    - Change `ResolutionResultBuilder` and its implementations to use component id rather than module version id as the key for the graph node.

### Test coverage

- `root.id` has the correct type when resolving a script classpath.
- `root.id` has the correct type when resolving a project configuration.
- `root.id` has the correct type when there is a dependency cycle between projects.
- Update dependency report tests to reflect the change in root id display name.

### Open issues

- Is it a bit of a stretch to call some of these consumers a 'component'?
- The results are actually component _instances_ rather than components (as per the definition above). Perhaps come up with a new name for 'component'.
- Sync this up with the variant resolution stories below. When resolving a native component's dependencies, the `root` should represent the consuming native component.
- Clean up the display names for configurations.
- Packages for the new types.
- Convenience for casting selector and id?
- Convenience for selecting things with a given id type or selector type?
- Rename `DependencyResult` to use 'requirement' instead of 'dependency'.
- Rename `ResolvedComponentResult.getId()` to something that is more explicit about the lack of guarantees. Maybe `getLocalId()` or ...
- Extract a `ModuleComponentMetadataDetails` out of `ComponentMetadataDetails` and use `ComponentIdentifier` instead of `ModuleVersionIdentifier` as id.

## Story: Query the artifacts for all components defined by a configuration

Currently, there is no way to determine which artifacts in a resolution result are associated with a given component. The artifacts are currently exposed
as `ResolvedArtifact` instances, which reference a module version identifier but not a component identifier. As such, there is no way
to match a `ResolvedArtifact` to a component that is not uniquely identified by a module version.

This story makes it possible to obtain an `ArtifactResolutionResult` directly from a `Configuration`, providing the same set of
artifacts as returned by `Configuration.getResolvedArtifacts()`. In doing so, the artifacts for a configuration are provided per component.

This story also adds convenience mechanisms for obtaining all artifacts for an `ArtifactResolutionResult`, in addition to the existing
way to get artifacts per component and query for artifact download failures.

### User visible changes

Download all artifact files for a configuration, failing if the graph could not be resolved:

    copy {
        from configurations.compile.incoming.artifactResolutionResult.artifactFiles
        into "libs"
    }

Report on failed artifact downloads for a configuration:

    configurations.compile.incoming.artifactResolutionResult.artifacts.each { ArtifactResult artifact ->
        if (artifact instanceof UnresolvedArtifactResult) {
            println "Failed to download artifact ${artifact.id} for component ${artifact.id.componentIdentifier}: ${artifact.failure.message}"
        }
    }

### Implementation

- Add `ResolverResults.getArtifactQueryResult()`: the result should be constructed by combining the existing `ResolverResults` and `ResolvedConfiguration`.
    - Provides the same set of artifacts as `ResolvedConfiguration.getResolvedArtifacts()`
    - If resolution of the dependency graph fails, then `ResolverResults.getArtifactResolutionResult()` should throw a descriptive exception
    - If artifacts for a component cannot be determined or downloaded, then the `ArtifactResolutionResult` should encapsulate those failures.
- Add `Configuration.incoming.getArtifactResolutionResult()` produces an `ArtifactResolutionResult` for the configuration.
    - This result should contain the same set of artifacts currently returned by `ResolvedConfiguration.getResolvedArtifacts()`
- Move `ComponentArtifactIdentifier` onto the public API, and return that from new method `ArtifactResult.getId()`
- Add `ResolvedComponentArtifactsResult.getArtifacts()` that returns the set of all `ArtifactResult` instances for a component.
- Add convenience methods to `ArtifactResolutionResult`:
    - `getArtifacts()` returns the set of all `ArtifactResult` instances for all resolved components, failing if the result contains any
      `UnresolvedComponentResult` instances.
    - `getFiles()` returns a `FileCollection` containing all files associated with `ArtifactResult` instances for all resolved components,
      throwing an exception on access for any `UnresolvedArtifactResult`

### Test cases

- Refactor existing test cases to verify:
    - Can query artifacts for configuration consisting of project and external components
    - Can query artifacts for configuration with classifier set on dependency
    - Can query artifacts for configuration with artifact defined on dependency
    - Can query artifacts for configuration with dependency on a configuration other than default
- Caching of artifacts resolved from configuration
- Reports failure to resolve dependency graph
- Reports failures for all artifacts that could not be resolved or downloaded.
- Reports composite failure on attempt to get all artifacts where multiple artifacts could not be downloaded
- Use `Configuration.incoming.artifactResolutionResult` after first using `Configuration.incoming.resolutionResult`: artifact result is not regenerated

### Open issues

- Replacement for `ResolvedArtifact.name`, `ResolvedArtifact.extension` etc
- Need a way to query Artifact model without downloading artifact files

## Story: Access the ivy and maven metadata artifacts via the Artifact Query API for a configuration

### User visible changes

Access the ivy.xml files for all ivy module in a configuration:

    def result = dependencies.createArtifactResolutionQuery()
        .forComponents(configurations.compile)
        .withArtifacts(IvyModule, IvyDescriptorArtifact)
        .execute()

    Set<File> ivyFiles = result.getArtifactFiles()

Get the pom files for all maven modules in a configuration:

    def artifactResult = dependencies.createArtifactResolutionQuery()
        .forComponents(configurations.compile)
        .withArtifacts(MavenModule, MavenPomArtifact)
        .execute()
    Set<File> pomFiles = artifactResult.getArtifactFiles()
    
## Story: IvyModule and MavenModule are symmetric with IvyPublication and MavenPublication

These classes are the resolve / publish equivalents, so there should be:

- Consistency in package structure
- Consistency in type hierarchy (`IvyModule` and `MavenModule` are not `Component` subtypes)

## Story: Reliable mechanism for checking for success with new resolution result APIs

- Add `rethrowFailure()` to `ArtifactResolutionResult` and `ResolutionResult`
- Collect all failures
    - All component metadata resolution failures
    - All artifact resolution failures
- Update JvmLibraryArtifactResolveTestFixture to rethrow failures and verify the exception messages and causes directly in the tests

## Story: Directly access the source and javadoc artifacts for a configuration using the Artifact Query API

### User visible changes

Get JvmLibrary components with source and javadoc artifacts for a configuration:

    def artifactResult = dependencies.createArtifactResolutionQuery()
        .forComponents(configurations.compile)
        .withArtifacts(JvmLibrary, JvmLibrarySourcesArtifact, JvmLibraryJavadocArtifact)
        .execute()
    def libraries = artifactResult.getComponents(JvmLibrary)

## Story: IDE plugins use the resolution result to determine library artifacts

This story changes the `idea` and `eclipse` plugins to use the resolution result to determine the IDE classpath artifacts.

- Change `IdeDependenciesExtractor` and `JavadocAndSourcesDownloader` to use the resolution result to determine the project and
  external artifacts.

## Story: Dependency resolution resolves all artifacts as a batch

Change dependency resolution implementation to resolve all artifacts as a single batch, when any artifact is requested.

- Use progress logging to report on the batch resolution progress.

## Story: Profile report displays artifact resolution time

TBD

## Story: Source and javadoc artifacts are updated when Maven snapshot changes

- Use the timestamp as part of the component identifier for unique Maven snapshots.
- A unique snapshot is no longer considered a changing module.

### Test cases

* New artifacts are used when snapshot has expired:
    * Resolve the source and javadoc artifacts for a Maven snapshot.
    * Publish a new snapshot with changed artifacts.
    * With `cacheChangingModules` set to 0, verify that the new source and javadoc artifacts are used.

* Old artifacts are used when snapshot has not expired:
    * Resolve a Maven snapshot, but not the source and javadoc artifacts.
    * Publish a new snapshot with changed artifacts
    * With `cacheChangingModules` set to default, verify that the old source and javadoc artifacts are used.

* No requests for Maven snapshot source and javadoc are made with build is executed with `--offline`, even when cache has expired.
* Can recover from a broken HTTP request by switching to use `--offline`.

## Story: Source and javadoc artifacts are updated for changing module based on configured cache expiry

Currently it is not possible to configure how often the Artifact Query API should check for changes to artifacts.
This means that the source and javadoc for a changing module may not be updated when the corresponding artifact is updated.

This story introduces a new cache control DSL that can apply to both dependency graph and artifact resolution:

- Introduce a 'check for changes' cache control DSL, as a replacement for `ResolutionStrategy`.
- Cache control DSL allows a frequency at which changing things should be checked.
    - Should be possible to declare 'never', 'always' and some duration.
- Cache control DSL allows a rule to be declared that specifies the frequency at which changing things from a given module should be checked.
- DSL should be reusable in some form for plugin resolution and external build script caching (but not wired up to these things yet).
- The existing DSL on `ResolutionStrategy` should win over the new cache control DSL.
- User guide explains how to use the cache control DSL, and DSL is documented in the DSL guide.

### Test cases

* New DSL can be used to control caching for all types of cached dependency resolution state:
    - version list
    - module version meta-data
    - downloaded artifacts
    - resolved artifact meta-data
    - Maven snapshot timestamp

Some test cases that are not directly related, but require this feature to be implemented:

* Source and javadoc for a non-unique Maven snapshot is updated when check-for-changes is 'always'.
* No requests for source and javadoc are made with build is executed with `--offline`, even when cache has expired.
* Can recover from a broken HTTP request by switching to use `--offline`.

## Story: Frequency for checking for missing artifacts is dependent on check-for-changes of owning module

Currently, the frequency with which we re-check for missing artifacts is fixed at once per day. This re-check occurs regardless of
whether the module containing the artifact is considered 'changing' or not.

Instead, the frequency of checking for missing artifacts should be determined by the frequency that we check for changes in the
owning module.

## Story: Dependency resolution result exposes local component instances that are not module versions

This story changes the resolution result to expose local component instances that are not module versions. That is, component instances that do not
have an associated module version identifier.

1. Change `ModuleVersionMetaData` to add a `ModuleVersionIdentifier getExternalId()`
    - Initially return the same as `getId()`
    - Change the implementation of `ResolvedComponentResult.getModuleVersion()` to return this value.
2. Change `ProjectDependencyResolver` to use the `ProjectPublicationRegistry` service to determine the identifier and metadata for a project dependency, if any.
3. Change the dependency reporting to handle a component with null `moduleVersion`.
4. Merge `ProjectDependencyPublicationResolver` into the `ProjectPublicationRegistry` service.

### Test cases

- Update the existing reporting task so that:
    - An external module is rendered as the (group, module, version).
    - A project that is not published is rendered as (project)
    - A project that is published rendered as (project) and (group, module, version)
- Update existing tests so that, for resolution result:
    - For the root component and any dependency components:
        - A project that is not published has null `moduleVersion`.
        - A project that is published using `uploadArchives` + Ivy has non-null `moduleVersion`
        - A project that is published using `uploadArchives` + Maven deployer has non-null `moduleVersion`
        - A project that is published using a Maven or Ivy publication has non-null `moduleVersion`

### Open issues

* Need to expose component source.
* Need to sync up with `ComponentMetadataDetails`.
* Add Ivy and Maven specific ids and sources.
* Rename and garbage collect internal types.
* Maybe don't use the new publication stuff until project dependencies are resolved to a component within the project, or until the engine understands multiple
  IDs for conflict resolution.

## Story: Model self resolving dependencies as component instances

Expose self-resolving dependencies as component instances in the resolution result. This will make these dependencies visible via the dependency
reports.

- Merge the special case resolution algorithm for self-resolving dependencies into the general purpose resolution algorithm.
- Introduce a new type of component identifier to represent a file-only component.
- Update dependency reporting to understand this kind of component identifier.
- Change the IDE dependency extraction so that it uses the resolution result to extract local file dependencies, rather than using the input `Dependency` set.

### Test coverage

- Ensure that the int test coverage for the dependency report, dependency insight report and HTML dependency report all verify that the report can be used
  with a mix of project, external and file dependencies.
- Verify that when a local component is replaced by an external component (via conflict resolution or dependency substitution) then none of the files
  from the local component are included in the result. For example, when a local component includes some file dependency declarations.

## Story: User guide describes the dependency management problem using new terminology

Update the user guide to use the term 'component' instead of 'module version' or 'module' where appropriate.

## Story: GRADLE-2713/GRADLE-2678 Dependency resolution uses local component identity when resolving project dependencies

Currently, dependency management uses the module version identifier (group, module, version) of the target of a project dependency to detect conflicts.
However, some projects do not have a meaningful module version identifier, and so one is assigned. The result is not always unique. This leads to a number of problems:

- A project with a given module version identifier depends on another project with the same module version identifier.
- A project depends on multiple projects with the same module version identifier.
- A project declares an external dependency on a module version identifier, and a project dependency on a project with the same module version identifier.

In all cases, the first dependency encountered during traversal determines which dependency is used. The other dependency is ignored. Clearly, this leads to
very confusing behaviour.

Instead, a project dependency will use the identity of the target project instead of its generated module version. The module version, if assigned, will be used to
detect and resolve conflicts.

### Open issues

- Excludes should not apply to local components. This is a breaking change.

## Story: Conflict resolution prefers local components over other components

When two components have conflicting external identifiers, select a local component.

Note: This is a breaking change.

## Story: Improve error messages when things cannot be found

Handle the following reasons why no matching component cannot be found for a selector:

- Typo in version selector:
    - List the available versions, if any are available. Present some candidates which might match the selector.
- Typo in (group, module) selector:
    - Inform that the module version was not found, if not. Present some candidates which might match the selector, by listing the groups and modules.
- Typo in repository configuration:
    - Inform which URLs were used to locate the module and versions
    - Inform about a missing meta-data artifact
- ~~No repositories declared~~

Handle the following reasons why a given artifact cannot be found:

- Typo in artifact selector:
    - List the available artifacts, if any are available. Present some candidates which might match the selector.
- Typo in repository configuration:
    - Inform which URLs were used to locate the artifact

### Open issues

- ~~Test cases for dynamic selector used with maven repo.~~
- ~~Error message should include some candidate versions for dynamic version when some versions found.~~
- Error message should include some candidate artifacts when artifact not found.
- ~~Error messages should distinguish between no versions found and no matching versions found.~~
- Fix case where static selector is used multiple times in same build for missing module.
- Fix case where dynamic selector is used multiple times in same build for module with no versions.
- Fix case where maven local contains pom and not artifact. Currently the error message implies that the pom is not there.
- Artifact resolution exception should include description of why the artifact is required, similar to module version resolution exception.
- Include this information in the HTML dependency reports.
- Does not report locations when cannot connect to server and is missing from other repositories.
- Report file locations as file paths.
- Move offline handling, so that only a single 'no cached version for offline model' exception is collected, instead of one per repository.
    - should handle case where there is a mix of local and remote repositories.
- Warn (once per build) when using an expired cached thing in offline mode.

## Story: Promote (un-incubate) the new dependency graph types.

In order to remove an old feature, we should promote the replacement API.

## Story: Remove old dependency graph model

TBD

## Feature: Expose APIs for additional questions that can be asked about components

- List versions of a component
- Get meta-data of a component
- Get certain artifacts of a component. Includes meta-data artifacts

# Open issues

- When resolving a pre-built component, fail if the specified file does not exist/has not been built (if buildable).
- In-memory caching for the list of artifacts for a component
