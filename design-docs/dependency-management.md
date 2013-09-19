
This specification defines some improvements to dependency management.

# Use cases

- Replace the old dependency result graph with one that is easier to use and consumes less heap space.
- Plugin implements a custom component type.

# Implementation plan

## Story: Restructure Gradle cache layout to give file store and metadata separate versions

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

## Story: Dependency result models resolved components instead of module versions

This story is a step in moving towards _components_ as the central dependency management concept, replacing the _module version_ concept.

1. Rename `ResolvedModuleVersionResult` to `ResolvedComponentResult`.
    - Rename the `allModuleVersions` methods on `ResolutionResult` to `allComponents`.
2. Rename `ModuleVersionSelectionReason` to `ComponentSelectionReason`.
3. Introduce a `org.gradle.api.artifacts.component.ComponentIdentifier` type.
    - `displayName` property returns some arbitrary human-consumable value.
4. Introduce a `ModuleComponentIdentifier` type that extends `ComponentIdentifier` and add a private implementation.
    - `group` property
    - `name` property
    - `version` property
5. Introduce a `org.gradle.api.artifacts.component.ComponentSelector` type.
    - `displayName` property returns some arbitrary human-consumable value.
6. Introduce a `ModuleComponentSelector` type that extends `ComponentSelector` and add a private implementation.
    - `group` property
    - `name` property
    - `version` property
7. Change `ResolvedComponentResult`:
    - Change `getId()` to return a `ComponentIdentifier`. Implementation should implement `ModuleComponentIdentifier`.
    - Add `ModuleComponentIdentifier getPublishedAs()`. Mark method as `@Nullable`. Implementation should return the same as
      value as `getId()` (for now).
8. Change the methods of `DependencyResult` and `UnresolvedDependencyResult` to use `ComponentSelector` instead of `ModuleVersionSelector`.

### Test coverage

- Nothing beyond some unit tests for the new methods and types.

## Story: Dependency result exposes local components

This story allows IDE integrations to map dependencies by exposing some information about the source of a resolved component.

1. Introduce a `BuildComponentIdentifier` type that extends `ComponentIdentifier` and add a private implementation.
    - `project` property
2. Change `ModuleVersionMetaData` to add a `ComponentIdentifier getComponentId()` method.
    - Default should be a `ModuleComponentIdentifier` with the same attributes as `getId()`.
    - For project components (as resolved by `ProjectDependencyResolver`) this should return a `BuildComponentIdentifier` instance.
3. Change `ResolvedComponentResult` implementations so that:
    - `getId()` returns the identifier from `ModuleVersionMetaData.getComponentId()`.
    - `getPublishedAs()` returns a `ModuleComponentIdentifier` with the same attributes as `ModuleVersionMetaData.getId()`.
    - Add `<T extends ComponentIdentifier> T getId(Class<T> type)` that returns an id of the requested type.
4. Introduce `BuildComponentSelector` type that extends `ComponentSelector and add a private implementation.
    - `project` property
5. Change `DependencyMetaData` to add a `ComponentSelector getSelector()`
    - Default should be a `ModuleComponentSelector` with the same attributes as `getRequested()`.
    - For project dependencies this should return a `BuildComponentSelector` instance.
4. Change the dependency reports so that they render both `id` and `publishedAs` when they are not the equal.

A consumer can extract the external and project components as follows:

    def result = configurations.compile.incoming.resolve()
    def projectComponents = result.root.dependencies.selected.findAll { it.id instanceof BuildComponentIdentifier }
    def externalComponents = result.root.dependencies.selected.findAll { it.id instanceof ModuleComponentIdentifier }

### Test coverage

- Need to update the existing tests for the dependency tasks, as they will now render different values for projects and project dependencies.
- Update existing integration test cases so that, for the resolution result:
    - for the root component
        - `id` is a `BuildComponentIdentifier` with `project` value referring to the consuming project.
        - `publishedAs` is a `ModuleComponentIdentifier` with correct `group`, `module`, `version` values.
        - `getId(BuildComponentIdentifier) == id`
        - `getId(ModuleComponentIdentifier) == publishedAs`
    - for a project dependency
        - `requested` is a `BuildComponentSelector` with `project` value referring to the target project.
    - for a resolved project component
        - `id` is a `BuildComponentIdentifier` with `project` value referring to the target project.
        - `publishedAs` is a `ModuleComponentIdentifier` with correct `group`, `module`, `version` values.
        - `getId(BuildComponentIdentifier) == id`
        - `getId(ModuleComponentIdentifier) == publishedAs`
    - for an external dependency:
        - `requested` is a `ModuleComponentSelector` with correct `group`, `module`, `version` values.
    - for an external module component:
        - `id` is a `ModuleComponentIdentifier` with correct `group`, `module`, `version` values.
        - `publishedAs` == `id`.
        - `getId(ModuleComponentIdentifier) == id`
        - `getId(BuildComponentIdentifier) == null`

### Open issues

- Convenience for casting selector? Selecting things with a given id type or selector type?

## Story: IDE plugins use resolution result to determine IDE classpaths

TBD

## Story: Dependency result does not expose local components that are not published

This story improves the resolution result to distinguish between the local and external identifiers for a component, and
associates an external identifier only with those components that are published.

1. Change `ModuleVersionMetaData` to add a `ModuleComponentIdentifier getPublishedAs()`
    - Default is to return the same as `getComponentId()`
    - Change the implementation of `ResolvedComponentResult.getPublishedAs()` to return this value.
2. Add a private `ProjectPublicationRegistry` service, which collects the outgoing publications for each project. This replaces `ProjectModuleRegistry`.
   This service is basically a map from project path to something that can produce the component meta data for that project.
    - When a project is configured, register an implicit component with a null `publishedAs`.
    - When an `Upload` task is configured with an ivy repository, register a component with `publishedAs` = `(project.group, project.name, project.version)`
    - When an `Upload` task is configured with a `MavenDeployer`, register a component with `publishedAs` = `(deployer.pom.groupId, deployer.pom.artifactId, deployer.pom.version)`
    - When an `IvyPublication` is defined, register a component with `publishedAs` taken from the publication.
    - When an `MavenPublication` is defined, register a component with `publishedAs` taken from the publication.
3. Change `ProjectDependencyResolver` to use the identifier and metadata from this service.
4. Change the dependency tasks so that they handle a component with null `publishedAs`.
5. Change `ProjectDependencyPublicationResolver` to use the `ProjectPublicationRegistry` service.

### Test cases

- Update the existing reporting task so that:
    - An external module is rendered as the (group, module, version).
    - A project that is not published is rendered as (project)
    - A project that is published rendered as (project) and (group, module, version)
- Update existing tests so that, for resolution result:
    - For the root component and any dependency components:
        - A project that is not published has null `publishedAs`.
        - A project that is published using `uploadArchives` + Ivy has non-null `publishedAs`
        - A project that is published using `uploadArchives` + Maven deployer has non-null `publishedAs`
        - A project that is published using a Maven or Ivy publication has non-null `publishedAs`

### Open issues

* Need to expose component source.
* Need to expose different kinds of component selectors.
* Need to sync up with `ComponentMetadataDetails`.
* Add Ivy and Maven specific ids and sources.
* Rename and garbage internal types.

## Story: GRADLE-2713/GRADLE2678 Dependency management uses local component identity to when resolving project dependencies

Currently, dependency management uses the module version (group, module, version) of the target of a project dependency to detect conflicts. However, the
module version of a project is not necessarily unique. This leads to a number of problems:

- A project with a given module version depends on another project with the same module version.
- A project depends on multiple projects with the same module version.
- A project declares an external dependency on a module version, and a project dependency on a project with the same module version.

In all cases, the first dependency encountered during traversal determines which dependency is used. The other dependency is ignored. Clearly, this leads to
very confusing behaviour.

Instead, a project dependency will use the identity of the target project instead of its module version. The module version will be used to detect and resolve
conflicts.

### Open issues

- Excludes should not apply to local components. This is a breaking change.

## Story: Conflict resolution prefers local components over external components

When two components have conflicting external identifiers, select a local component.

Note: This is a breaking change.

## Story: Plugin selects the target component for a project dependency

This story allows a plugin some control over which component a project dependency is resolved to for a given dependency resolution.
Generally, this would be used to select components of a given type (eg select the Java library, if present).

- Wrap the meta-data and artifacts that are currently used in a 'legacy' component for backwards compatibility. This is used as the default.
- Add a new type of rule to `ResolutionStrategy` that is given the set of components in the target project + the currently selected component
  and can select a different component.
- The rule is invoked once for each target project. The result is reused for subsequent dependencies with the same target project.
- Expose the component identifier/source of the selected component through the resolution result.

### Open issues

- Possibly add a declarative shortcut to select components of a given type (and implicitly, ignore the legacy component).
- What component meta-data is exposed to the rule?

## Story: Plugin selects the target packaging for a local component

This story allows a plugin some control over which packaging of a component will be used for a given project dependency.
Generally, this would be used to select a variant + usage of a component.

- Add a new type of rule to `ResolutionStrategy` that is given the selected component + the requested packaging + the selected packaging. The rule can select a
  different packaging.
- The rule is invoked once for each selected component + requested packaging. The result is reused for all dependencies that have selected this component and requested
  the same packaging.

#### Open issues

- What component meta-data is exposed to the rule?
- Handle dependencies that declare a target configuration. Options:
    - Don't invoke this rule for these dependencies.
    - If the legacy component has not been selected, assert that at least one rule selects a packaging.
    - Map configuration to a packaging using the component meta-data.
- Artifacts are implicit in the packaging.
- Introduce rules that can tweak the selected packaging, define new ones, and so on.
- Introduce rules that given a selected packaging, select the artifacts to use.

## Story: Plugin selects the target packaging for an external component

Map an external component to component meta-data and pass to the packaging rules.

## Story: Introduce a direct component dependency

Allow a dependency to be declared on a component instance. This will allow a plugin to include components built by the current project in dependency
resolution, including conflict detection and resolution.

- Allow `SoftwareComponent` instances to be used on the RHS of a dependency declaration.
- Use the meta-data and artifacts defined by `SoftwareComponentInternal`.
- Expose the component identifier/source through the resolution result.
- If the component is published, use the publication identifier to apply conflict resolution.

## Story: Native binary plugins resolve variants for dependencies

- Expose native components as `SoftwareComponent` implementations.
- Add direct dependencies for libraries in the same project.
- Use the resolve rules to select native libraries for a project dependency.
- Use the resolve rules to select the appropriate variant + usage for each selected component.

### Open stories

- Allow plugin to package up the rules into a component type definition.

## Story: Generate and publish component descriptor

Introduce a native Gradle component descriptor file, generate and publish this.

## Story: Dependency resolution uses component descriptor

Use the component descriptor, if present, during resolution.

## Story: New dependency graph uses less heap

The new dependency graph also requires substantial heap (in very large projects). We should spool it to disk during resolution
and load it into heap only as required.

### Coverage

* Existing dependency reports tests work neatly
* The report is generated when the configuration was already resolved (e.g. some previous task triggered resolution)
* The report is generated when the configuration was unresolved yet.

## Story: Promote (un-incubate) the new dependency graph types.

In order to remove an old feature, we should promote the replacement API.

## Story: Remove old dependency graph model

TBD

## Story: declarative substitution of group, module and version

Allow some substitutions to be expressed declaratively, rather than imperatively as a rule.

# Open issues
