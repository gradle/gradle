
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

## Story: Dependency result exposes components instead of module versions

This story is a step in moving towards _components_ as the central dependency management concept, replacing the _module version_ concept.

## Story: Dependency result exposes which project a local component belongs to

This story allows IDE integrations to map dependencies.

### Open issues

* Expose this using different kind of component identifier, or as a component source, or both.
* Need to expose different kinds of component selectors too.
* IDE plugins should use this instead of the input dependency declarations.

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
