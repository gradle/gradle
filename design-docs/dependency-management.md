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

## Story: IDE plugins use dependency resolution result to determine IDE class paths

This story changes the `idea` and `eclipse` plugins to use the resolution result to determine the IDE project classpath.

- Change `IdeDependenciesExtractor` and `JavadocAndSourcesDownloader` to use the resolution result to determine the project and
  external dependencies.

## Story: Dependency resolution result exposes a consumer that is not a module version

This story exposes different kinds of consumers for a dependency graph.

- Result `root.id` should return a `ProjectComponentIdentifier` when a project configuration is resolved.
- Result `root.id` should return an opaque `ComponentIdentifier` implementation when any other kind of configuration is resolved.
    - Add an internal implementation that implements only `ComponentIdentifier`. Two such implementations are equal when their display
      names are the same.
    - This implementation should use the configuration's display name for the component display name.
- When there is a project dependency in the graph that refers to the root, the selected component for the dependency should be the same instance
  as `root`.
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

## Story: Allow the artifacts for a component instance to be queried

Currently, there is no way to determine which artifacts in a resolution result are associated with a given component. The artifacts are currently exposed
as `ResolvedArtifact` instances. These artifacts have a module version identifier associated with them, which is used to match against the component's
module version identifier. This only work when the component has a module version associated with it, which is not always the case.

TBD: provide some API to query the artifacts associated for a given component instance in a resolution result.

- It should be possible to query only the graph and not the artifacts. This should not download any artifacts.
- It should be possible to query the artifacts as a single batch, so that, for example, we will be able to resolve and download artifacts
      in parallel.
- The API should expose download failures.
- A component may have zero or more artifacts associated with it.

### Test cases

- Can query the artifacts of an external component.
- Can query the artifacts of a project component.
- Can query those artifacts that could not be resolved or downloaded.
- Caching is applied as appropriate.

## Story: IDE plugins use the resolution result to determine library artifacts

This story changes the `idea` and `eclipse` plugins to use the resolution result to determine the IDE classpath artifacts.

- Change `IdeDependenciesExtractor` and `JavadocAndSourcesDownloader` to use the resolution result to determine the project and
  external artifacts.

## Story: Dependency resolution resolves all artifacts as a batch

Change dependency resolution implementation to resolve all artifacts as a single batch, when any artifact is requested.

- Use progress logging to report on the batch resolution progress.

## Story: Profile report displays artifact resolution time

TBD

## Story: Allow the source and Javadoc artifacts for an external Java library to be queried

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

### API design proposals

#### Resolve and iterate over all jvm libraries, without resolving artifacts

```
def result = configuration.incoming.getResolutionResult(JvmLibrary)
for (jvmLibrary in result.getComponents(JvmLibrary.class)) { println jvmLibrary.id }

// alternatively, ResolutionResult could be changed to have component type as a type parameter

def result = configuration.incoming.getResolutionResult(JvmLibrary)
for (jvmLibrary in result.getComponents()) { ... }
```

ResolutionResult#getComponents returns an `Iterable`. This is more flexible than providing an internal iterator,
because it allows to filter, map, etc. the components.

##### Alternative design

Additionally or instead, we could make components available via ResolvedComponentResult, and could make it easy to iterate over the graph of results.
For this, ResolvedComponentResult should be changed to have component type as a type parameter.

```
def resolutionResult = configuration.incoming.getResolutionResult(JvmLibrary, JvmLibraryMainArtifact, JvmLibrarySourceArtifact)
for (componentResult in resolutionResult.getComponentResults(JvmLibrary.class)) {
  def jvmLibrary = componentResult.component
  ...
}
```

#### Resolve jvm libraries together with their main and source artifacts, iterate over artifacts

```
def result = configuration.incoming.getResolutionResult(JvmLibrary, JvmLibraryMainArtifact, JvmLibrarySourceArtifact)
for (jvmLibrary in result.getComponents(JvmLibrary.class)) {
  for (artifact in jvmLibrary.artifacts) { println artifact.id }
}
```

#### Resolve jvm libraries together with their main and source artifacts, inspect artifact resolution failures

```
def result = configuration.incoming.getResolutionResult(JvmLibrary, JvmLibraryMainArtifact, JvmLibrarySourceArtifact)
for (failure in result.artifactResolutionFailures) {
  println failure.componentId
  println failure.artifactId
  println failure.exception
}

// alternatively, artifact resolution failures could be handled at component level

```
def result = configuration.incoming.getResolutionResult(JvmLibrary, JvmLibraryMainArtifact, JvmLibrarySourceArtifact)
for (jvmLibrary in result.getComponents(JvmLibrary.class)) {
  // design 1, similar to ResolvedDependencyResult/UnresolvedDependencyResult
  for (artifact in jvmLibrary.artifacts) {
    if (artifact instanceof UnresolvedArtifact) { println artifact.failure }
    else if (artifact instanceof ResolvedArtifact) { println artifact.file }
  }
  // or perhaps
  for (artifact in jvmLibrary.resolvedArtifacts { ... }
  for (artifact in jvmLibrary.unresolvedArtifacts { ... }
  // design 2
  for (failure in jvmLibrary.resolutionFailures) {
    println failure.artifactId
    println failure.exception
  }
}

#### Some API mockups

public interface ResolvedComponentResult {
  ...
  Component getComponent();
}

public interface Component<T extends Artifact> {
  ComponentIdentifier getId();
  Set<T> getArtifacts();
  <U extends T> Set<U> getArtifacts(Class<U> type);
}

public interface Artifact {
  File getFile();
}

public interface JvmLibrary extends Component<JvmLibraryArtifact>

public interface JvmLibraryMainArtifact extends JvmLibraryArtifact

public interface JvmLibraryJavadocArtifact extends JvmLibraryArtifact

public interface JvmLibrarySourceArtifact extends JvmLibraryArtifact

## Story: IDE plugins use the resolution result to determine library source and Javadoc artifacts

This story changes the `idea` and `eclipse` plugins to use the resolution result to determine the IDE classpath artifacts.

- Change `IdeDependenciesExtractor` and `JavadocAndSourcesDownloader` to use the resolution result to determine the source and Javadoc artifacts.
- Should ignore project components.

## Story: Dependency resolution uses conventional schemes to locate source and Javadoc artifacts for Ivy modules

This story improves the convention used to locate the source and Javadocs to cover some common Ivy conventions.

TBD

## Story: Source and Javadoc artifacts are exposed for a local Java component

TBD

## Story: Source and Javadoc artifacts are published for a Java component

This story changes the `ivy-publish` and `maven-publish` plugins to publish the source and Javadocs for a Java component.

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

## Story: Native component dependencies are visible in the dependency reports

### Open issues

- Dependencies need to shown per-variant.

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
