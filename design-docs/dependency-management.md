
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

## Story: Dependency resolution result produces a graph of component instances instead of a graph of module versions

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
    - Add `ModuleComponentIdentifier getPublishedAs()`. Mark method as `@Nullable`. Implementation should return the same as
      value as `getId()` (for now).
8. Change the methods of `DependencyResult` and `UnresolvedDependencyResult` to use `ComponentSelector` instead of `ModuleVersionSelector`.

### Test coverage

- Nothing beyond some unit tests for the new methods and types.

### Open issues

- Packages for the new types
- The results are actually component _instances_ rather than components (as per the definition above). Perhaps come up with a new name for 'component'.
- Rename `DependencyResult` to use 'requirement' instead of 'dependency'.
- Rename `ResolvedComponentResult.getId()` to something that is more explicit about the lack of guarantees. Maybe `getLocalId()` or ...
- Rename `getPublishedAs()` to `getModuleIdentifier()` and return `ModuleIdentifier`. This returns the module which the component instance is mapped to.

## Story: Dependency resolution result exposes local component instances

This story changes the dependency resolution model to distinguish between component instances that are produced by the build and those that are
produced outside the build. This will allow IDE integrations to map dependencies by exposing this information about the source of a component
instance.

This story also changes the dependency resolution model so that local component instances are no longer treated as module versions. Instead, a local project
path will be used to identify these instances. For now, every local component instance will have an associated (group, module, version) identifier.

1. Introduce a `BuildComponentIdentifier` type that extends `ComponentIdentifier` and add a private implementation.
    - `project` property, as the project path.
    - `displayName` should be something like `project :path`.
2. Change `ModuleVersionMetaData` to add a `ComponentIdentifier getComponentId()` method.
    - Default should be a `ModuleComponentIdentifier` with the same attributes as `getId()`.
    - For project components (as resolved by `ProjectDependencyResolver`) this should return a `BuildComponentIdentifier` instance.
3. Change `ResolvedComponentResult` implementations so that:
    - `getId()` returns the identifier from `ModuleVersionMetaData.getComponentId()`.
    - `getPublishedAs()` returns a `ModuleComponentIdentifier` with the same attributes as `ModuleVersionMetaData.getId()`.
4. Introduce `BuildComponentSelector` type that extends `ComponentSelector` and add a private implementation.
    - `project` property, as the project path.
5. Change `DependencyMetaData` to add a `ComponentSelector getSelector()`
    - Default should be a `ModuleComponentSelector` with the same attributes as `getRequested()`.
    - For project dependencies this should return a `BuildComponentSelector` instance.

This will allow a consumer to extract the external and project components as follows:

    def result = configurations.compile.incoming.resolve()
    def projectComponents = result.root.dependencies.selected.findAll { it.id instanceof BuildComponentIdentifier }
    def externalComponents = result.root.dependencies.selected.findAll { it.id instanceof ModuleComponentIdentifier }

### Test coverage

- Need to update the existing tests for the dependency report tasks, as they will now render different values for project dependencies.
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

- Renamed `BuildComponentIdentifier` to `ProjectComponentIdentifier` or something else
- Do the same for `BuildComponentSelector`
- Convenience for casting selector and id?
- Convenience for selecting things with a given id type or selector type?
- Result root.id should be an instance of `BuildComponentIdentifier` not `ModuleComponentIdentifier`
- Rename internal class `ModuleVersionSelection` and its methods
- Extract a `ModuleComponentMetadataDetails` out of `ComponentMetadataDetails` and use `ComponentIdentifier` instead of `ModuleVersionIdentifier` as id.

## Story: Dependency reports indicate the source of a component

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

1. Change the `RenderableDependency` hierarchy to use the component id and publishedAs id.
2. Update the the dependency report tests as appropriate.

The HTML dependency report should change in a similar way.

### Test coverage

- Update the existing test coverage for the new display values.
- Ensure that the int test coverage for the dependency report, dependency insight report and HTML dependency report all verify that the report can be used
  with a mix of project and external dependencies.

### Open issues

- Ensure there is coverage for the dependency report and the dependency HTML report where
    - There are project dependencies in the graph
- Ensure there is coverage for the dependency insight report where:
    - There are project dependencies in the graph
    - There are project dependencies in the graph and the `--dependency` option is used.

## Story: IDE plugins use dependency resolution result to determine IDE classpaths

This story changes the `idea` and `eclipse` plugins to use the resolution result to determine the IDE project classpath.

- Change `IdeDependenciesExtractor` and `JavadocAndSourcesDownloader` to use the resolution result to determine the project and
  external dependencies.

## Story: Dependency resolution result exposes local component instances that are not module versions

This story changes the resolution result to expose local component instances that are not module versions. That is, component instances that do not
have an associated (group, module, version) identifier.

It introduces local and external identifiers for a component, and associates an external identifier only with those components that are published
or publishable.

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
* Maybe don't use the new publication stuff until project dependencies are resolved to a component within the project, or until the engine understands multiple
  IDs for conflict resolution.

## Story: User guide describes the dependency management problem in terms of components

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

## Story: Model the native binary dependencies as requirements

This story introduces a new API which can take an arbitrary set of requirements and some usage context and produce a set of files which
meet the requirements.

- Split up `NativeDependencyResolver` into several pieces:
    - A public API that takes a collection of objects plus some object that represents a usage and returns a buildable collection of files. This API should not
      refer to any native domain concepts.
    - A service that implements the API.
    - A registry of requirement -> buildable file collection converters.
- Add some way to query the resolved include roots, link files and runtime files for a native binary.

## Story: Implement native binary dependency resolution using self resolving dependencies

This story starts to push the resolution mechanism introduced in the above story down into the dependency management engine. For this story,
native binary dependencies will be converted to self-resolving dependencies which are then fed into the dependency management engine.

This story is simply a refactoring. No new user-visible behaviour will be added.

## Story: Model self resolving dependencies as component instances

Expose self-resolving dependencies as component instances in the resolution result. This will make these dependencies visible via the dependency
reports.

- Merge the special case resolution algorithm for self-resolving dependencies into the general purpose resolution algorithm.
- Introduce a new type of component identifier to represent a file-only component.
- Update reporting to understand this kind of component identifier.

### Test coverage

- Ensure that the int test coverage for the dependency report, dependency insight report and HTML dependency report all verify that the report can be used
  with a mix of project, external and file dependencies.
- Verify that when a local component is replaced by an external component (via conflict resolution or dependency substitution) then none of the files
  from the local component are included in the result. For example, when a local component includes some file dependency declarations.

## Story: Plugin contributes a component type implementation

Allow a plugin to contribute a component type implementation, which is responsible for defining some component type. For this story, the definition is
responsible for extracting the component meta-data from some local component instance. Later stories will generalise this to make the definition
reusable for other purposes, such as publishing.

- Use this in the native binary plugins to convert native library and binary instances to meta-data.

### Open issues

- Add some way to influence the target of a project dependency
- Generalise so that the meta-data model can be reused for publishing and resolving external components
    - Version the model
- Detangle the usage context from the dependency implementation and pass through to the resolvers
    - Needs to be considered when caching stuff
- Add some sugar to infer the meta-data model from some static types
- Expose the component instance graph from the new requirements API
- Remove `NativeDependencySet` and `LibraryResolver` interfaces
- Replace the existing headers and files configurations

## Story: Conflict resolution prefers local components over other components

When two components have conflicting external identifiers, select a local component.

Note: This is a breaking change.

## Story: Generate and publish component meta-data artifact

Introduce a native Gradle component descriptor file, generate and publish this.

## Story: Dependency resolution uses component meta-data artifact

Use the component descriptor, if present, during resolution.

## Story: Improve error messages when things cannot be found

Handle the following reasons why no matching component cannot be found for a selector:

- Typo in version selector:
    - List the available versions, if any are available. Present some candidates which might match the selector.
- Typo in (group, module) selector:
    - Inform that the module version was not found, if not. Present some candidates which might match the selector, by listing the groups and modules.
- Typo in repository configuration:
    - Inform which URLs were used to locate the module and versions
    - Inform about a missing meta-data artifact

Handle the following reasons why a given artifact cannot be found:

- Typo in artifact selector:
    - List the available artifacts, if any are available. Present some candidates which might match the selector.
- Typo in repository configuration:
    - Inform which URLs were used to locate the artifact

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

## Feature: Expose APIs for additional questions that can be asked about components

- List versions of a component
- Get meta-data of a component
- Get certain artifacts of a component. Includes meta-data artifacts

# Open issues

- When resolving a pre-built component, fail if the specified file does not exist/has not been built (if buildable).
