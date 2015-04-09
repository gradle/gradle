## Story: New dependency graph uses less heap

The new dependency graph also requires substantial heap (in very large projects). We should spool it to disk during resolution
and load it into heap only as required.

### Coverage

* Existing dependency reports tests work neatly
* The report is generated when the configuration was already resolved (e.g. some previous task triggered resolution)
* The report is generated when the configuration was unresolved yet.

## Story: Restructure Gradle cache layout to give file store and metadata separate versions (DONE)

This story separates the file store and meta-data cache versions so that they can evolve separately.

The Gradle cache layout will become:

* `$gradleHome/caches/modules-${l}/` - this is the cache base directory, which includes the lock. When the locking protocol changes, the version `${l}` is changed.
* `$gradleHome/caches/modules-${l}/files-${l}.${f}` - this is the file store directory. When the file store layout changes, the version `${f}` is changed.
* `$gradleHome/caches/modules-${l}/metadata-${l}.${m}` - this is the meta-data directory, includes the artifact, dependency and module meta-data caches.
  When the meta-data format changes, the version `${m}` is changed.

Initial values: `l` = 1, `f` = 1, `m` = 27.

1. Change `CacheLockingManager.createCache()` to accept a relative path name instead of a `File`. This path should be resolved relative to the meta-data directory.
2. Add methods to `CacheLockingManager` to create the file store and module meta-data `PathKeyFileStore` implementations.

### Test coverage

- Update the existing caching test coverage for the new locations. No new coverage is required.

## Story: Dependency resolution result produces a graph of component instances instead of a graph of module versions (DONE)

Currently, the output of dependency resolution is effectively a graph of module versions. There are a number of issues with this approach.
One fundamental problem is that not all of the things that participate in dependency resolution are modules in a repository nor
are they necessarily all versioned. This series of stories changes dependency resolution so that it can deal with things which are not module versions.

The approach here is to introduce the more general concept of a component and base dependency resolution on this concept. Then, different
types of components will later be introduced to model the different kinds of things that participate in dependency resolution.

In this story, the dependency resolution result is changed so that it produces a graph of component instances, rather than a graph of module versions.

1. Rename `ResolvedModuleVersionResult` to `ResolvedComponentResult`.
    - Rename the `allModuleVersions` methods on `ResolutionResult` to `allComponents`.
2. Rename `ModuleVersionSelectionReason` to `ComponentSelectionReason`.
3. Introduce a `org.gradle.api.artifacts.component.ComponentIdentifier` type.
    - `displayName` property returns some arbitrary human-consumable value.
4. Introduce a `ModuleComponentIdentifier` type that extends `ComponentIdentifier` and add an internal implementation.
    - `group` property
    - `name` property
    - `version` property
5. Introduce a `org.gradle.api.artifacts.component.ComponentSelector` type.
    - `displayName` property returns some arbitrary human-consumable value.
6. Introduce a `ModuleComponentSelector` type that extends `ComponentSelector` and add an internal implementation.
    - `group` property
    - `name` property
    - `version` property
7. Change `ResolvedComponentResult`:
    - Change `getId()` to return a `ComponentIdentifier`. Implementation should return a `ModuleComponentIdentifier` implementation.
    - Add `ModuleVersionIdentifier getModuleVersion()`. Mark method as `@Nullable`. Implementation should return the same as
      value as `getId()` for this story.
8. Change the methods of `DependencyResult` and `UnresolvedDependencyResult` to use `ComponentSelector` instead of `ModuleVersionSelector`.

### Test coverage

- Nothing beyond some unit tests for the new methods and types.

## Story: Dependency resolution result exposes local component instances (DONE)

This story changes the dependency resolution model to distinguish between component instances that are produced by the build and those that are
produced outside the build. This will allow IDE integrations to map dependencies by exposing this information about the source of a component
instance.

This story also changes the dependency resolution model so that local component instances are no longer treated as module versions. Instead, a local project
path will be used to identify these instances. For now, every local component instance will have an associated (group, module, version) identifier.

1. Introduce a `ProjectComponentIdentifier` type that extends `ComponentIdentifier` and add a private implementation.
    - `project` property, as the project path.
    - `displayName` should be something like `project :path`.
2. Change `ModuleVersionMetaData` to add a `ComponentIdentifier getComponentId()` method.
    - Default should be a `ModuleComponentIdentifier` with the same attributes as `getId()`.
    - For project components (as resolved by `ProjectDependencyResolver`) this should return a `ProjectComponentIdentifier` instance.
3. Change `ResolvedComponentResult` implementations so that:
    - `getId()` returns the identifier from `ModuleVersionMetaData.getComponentId()`.
    - `getModuleVersion()` returns a `ModuleVersionIdentifier` with the same attributes as `ModuleVersionMetaData.getId()`.
4. Introduce `ProjectComponentSelector` type that extends `ComponentSelector` and add a private implementation.
    - `project` property, as the project path.
5. Change `DependencyMetaData` to add a `ComponentSelector getSelector()`
    - Default should be a `ModuleComponentSelector` with the same attributes as `getRequested()`.
    - For project dependencies this should return a `ProjectComponentSelector` instance.

This will allow a consumer to extract the external and project components as follows:

    def result = configurations.compile.incoming.resolutionResult
    def projectComponents = result.root.dependencies.selected.findAll { it.id instanceof ProjectComponentIdentifier }
    def externalComponents = result.root.dependencies.selected.findAll { it.id instanceof ModuleComponentIdentifier }

### Test coverage

- Need to update the existing tests for the dependency report tasks, as they will now render different values for project dependencies.
- Update existing integration test cases so that, for the resolution result:
    - for the root component
        - `id` is a `ProjectComponentIdentifier` with `project` value referring to the consuming project.
        - `moduleVersion` is a `ModuleVersionIdentifier` with correct `group`, `module`, `version` values.
    - for a project dependency
        - `requested` is a `ProjectComponentSelector` with `project` value referring to the target project.
    - for a resolved project component
        - `id` is a `ProjectComponentIdentifier` with `project` value referring to the target project.
        - `moduleVersion` is a `ModuleVersionIdentifier` with correct `group`, `module`, `version` values.
    - for an external dependency:
        - `requested` is a `ModuleComponentSelector` with correct `group`, `module`, `version` values.
    - for an external module component:
        - `id` is a `ModuleComponentIdentifier` with correct `group`, `module`, `version` values.
        - `moduleVersion` has the same attributes as `id`.

## Story: Dependency reports indicate the source of a component (DONE)

The dependency reporting will change to give some indication of the source of the component:

For an external component instance, this will be unchanged:

    +- group:name:1.2
    +- group:other:1.3 -> group:other:1.3.1

For a local component that is not a module version, this will look something like:

    +- project :some:path
    +- project :some:path -> group:other:1.2

For a local component that is a module version, this will look something like

    +- project :some:path (group:name:1.2)
    +- project :some:path (group:name:1.2) -> group:other:1.2

1. Change the `RenderableDependency` hierarchy to use the component id and module version id, if not null.
2. Update the the dependency report tests as appropriate.

The HTML dependency report should change in a similar way.

### Test coverage

- Update the existing test coverage for the new display values.
- Ensure there is coverage for the dependency report and the dependency HTML report where
    - There are a mix of external and project dependencies in the graph
- Ensure there is coverage for the dependency insight report where:
    - There are a mix of external and project dependencies in the graph
    - There are a mix of external and project dependencies in the graph and the `--dependency` option is used.

## Story: Allow the source and Javadoc artifacts for an external Java library to be queried (✓)

This story introduces an API which allows the source and Javadoc artifacts for a Java library to be queried

- Should be possible to query the artifacts as a single batch, so that, for example, we will be able to resolve and download artifacts
  in parallel.
- The API should expose download failures.
- A component may have zero or more source artifacts associated with it.
- A component may have zero or more Javadoc artifacts associated with it.
- Should introduce the concept of a Java library to the result.
- Should have something in common with the story to expose component artifacts, above.
- Initial implementation should use the Maven style convention to currently used by the IDE plugins. The a later story will improve this for Ivy repositories.

### Test cases

- Query the source artifacts only
- Query the Javadoc artifacts only
- Query which artifacts could not be resolved or downloaded.
- Caching is applied as appropriate.

## Story: IDE plugins use new artifact resolution API to download sources and javadoc (✓)

This story changes the `idea` and `eclipse` plugins to use the resolution result to determine the IDE classpath artifacts.

- Change `IdeDependenciesExtractor` and `JavadocAndSourcesDownloader` to use the resolution result to determine the source and Javadoc artifacts.
- Should ignore project components.

## Story: Dependency resolution uses conventional schemes to locate source and Javadoc artifacts for Ivy modules (✓)

This story improves the convention used to locate the source and Javadocs to cover some common Ivy conventions.

### User visible changes

Source artifacts contained in a 'sources' configuration in ivy.xml will be now be automatically downloaded and linked into an IDE project. Similar for javadoc artifacts in a 'javadoc' configuration.

### Implementation

* Make it possible to use ResolveIvyFactory to create a DependencyToModuleVersionResolver without a configuration: use a default ResolutionStrategy and supplied name.
* Create a `DependencyMetaData` for each supplied `ModuleComponentIdentifier`, and use this to obtain the ModuleVersionMetaData for the component.
    * Fail for any other types of `ComponentIdentifier`
* Add a new method: `ArtifactResolver.resolve(ModuleVersionMetaData, Class<? extends JvmLibraryArtifact>, BuildableMultipleArtifactResolveResult)`
    * Note that this is a transitional API: long term the second parameter may be generalised in some way
    * `BuildableMultipleArtifactResolveResult` allows the collection of multiple downloaded artifacts of the type, or multiple failures, or a combination.
* Add a method to `ModuleVersionRepository` that provides the `ModuleVersionArtifactMetaData` for candidate artifacts
  given a particular ModuleVersionMetaData + JvmLibraryArtifact class.
    * This method should not require remote access to the repository.
    * For `MavenResolver` and `IvyDependencyResolverAdapter`, this would return artifacts defined with the appropriate classifiers.
    * For `IvyResolver`, this would inspect the `ModuleVersionMetaData` to determine the candidate artifacts.
    * This method should be used to implement the new `resolve` method on `UserResolverChain.ModuleVersionRepositoryArtifactResolverAdapter`.

### Test cases

* Where ivy.xml contains a 'sources' and/or 'javadoc' configuration:
    * Defined artifacts are included in generated IDE files
    * Defined artifacts are available via Artifact Query API
    * Detect and report on artifacts that are defined in ivy configuration but not found
    * Detect and report error for artifacts that are defined in ivy configuration where download fails
* Use ivy scheme to retrieve source/javadoc artifacts from a local ivy repository
* Resolve source/javadoc artifacts by maven conventions where no ivy convention can be used:
    * Flatdir repository
    * No ivy.xml file for module
    * Ivy module with no source/javadoc configurations defined in metadata
* Maven conventions are not used if ivy file declares empty sources and javadoc configuration

## Story: Access the ivy and maven metadata artifacts via the Artifact Query API for component ID(s)

### User visible changes

Access the ivy.xml files for a ivy components with the specified id:

    def result = dependencies.createArtifactResolutionQuery()
        .forComponents(ivyModuleComponentId1, ivyModuleComponentId2)
        .withArtifacts(IvyModule, IvyDescriptorArtifact)
        .execute()

    for(component in result.resolvedComponents) {
        component.getArtifacts(IvyDescriptorArtifact).each { assert it.file.name == 'ivy.xml' }
    }

Get the pom files for all maven modules in a configuration:

    def artifactResult = dependencies.createArtifactResolutionQuery()
        .forComponents(mavenModuleComponentId1, mavenModuleComponentId2)
        .withArtifacts(MavenModule, MavenPomArtifact)
        .execute()

    for(component in result.resolvedComponents) {
        component.getArtifacts(MavenPomArtifact).each { assert it.file.name == 'some-artifact-1.0.pom' }
    }

### Implementation

- Introduce `Module` domain model:
    - Add the interface `org.gradle.ivy.IvyModule` in the project `ivy`. The interface extends `org.gradle.api.component.Component`.
    - Add the interface `org.gradle.maven.MavenModule` in the project `maven`. The interface extends `org.gradle.api.component.Component`.
- Introduce `Artifact` domain model:
    - Add the interface `org.gradle.ivy.IvyDescriptorArtifact` in the project `ivy`. The interface extends `org.gradle.api.component.Artifact`.
    - Add the interface `org.gradle.maven.MavenPomArtifact` in the project `maven`. The interface extends `org.gradle.api.component.Artifact`.

### Test cases

- Invalid component type and artifact type
    - Cannot call `withArtifacts` multiple times for query
    - Cannot mix `JvmLibrary` with metadata artifact types
    - Cannot mix `IvyModule` and `MavenModule` component types with jvm library artifact types
- Unsupported artifact types:
    - When requesting `IvyModule` artifacts, the result for a maven component is `UnresolvedComponentResult` with a useful failure.
    - When requesting `MavenModule` artifacts, the result for an ivy component is `UnresolvedComponentResult` with a useful failure.
    - When requesting `IvyModule` or `MavenModule` artifacts, the result for a project component is `UnresolvedComponentResult` with a useful failure.
- Optional artifacts:
    - Request an ivy descriptor for an ivy module with no descriptor, and get empty set of artifacts.
    - Request a pom for a maven module with no pom, and get empty set of artifacts.
- Metadata artifacts are cached
    - Updates `IvyDescriptorArtifact` for changing module
    - Updates `MavenPomArtifact` for maven snapshot
    - Updates both with `--refresh-dependencies`

### Open issues

- Typed domain model for IvyModule and MavenModule
