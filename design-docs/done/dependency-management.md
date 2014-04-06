
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
