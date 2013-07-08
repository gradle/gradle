# Gradle 2.0

Gradle 2.0 is the next major Gradle release that offers the opportunity to make breaking changes to the public interface of Gradle. This document captures a laundry
list of ideas to consider before shipping Gradle 2.0.

Note: for the change listed below, the old behaviour or feature to be removed should be deprecated in a Gradle 1.x release, probably no later than Gradle 1.8. Similarly
for changes to behaviour.

# Planned

The following stories are to be included in Gradle 2.0.

## Remove all features deprecated as at Gradle 1.8

In the Gradle 2.0-rc-1 release, remove all features that are deprecated as at Gradle 1.8 or earlier:

* Search for usages of `DeprecationLogger`, `@Deprecated`, `@deprecated` and remove the associated feature.

## Remove Ivy types from the Gradle repository API

These types expose the implementation details of dependency management and force a certain implementation on Gradle. Removing these types from the API
allows us to implement new features and remove some internal complexity.

* Remove methods from `ArtifactRepositoryContainer` and `RepositoryHandler` that accept an Ivy `DependencyResolver` as parameter.
* Remove methods from `ArtifactRepositoryContainer` that return `DependencyResolver`.
* Remove `RepositoryHandler.mavenRepo()`.
* Change the `MavenResolver` implementation so that it no longer extends `DependencyResolver`.
* Change the `FlatDirRepository` implementation so that it no longer uses a `DependencyResolver` implementation.

## Gradle Open API

Now that we have reasonable tooling support via the tooling API, remove the Open API.

* Implement a stub to report a reasonable error message when attempting to use Gradle from the Open API.
* Remove the remaining Open API classes and project.

# Candidates

The following stories are candidates to be included in Gradle 2.0.

## Decouple publishing DSL from Maven Ant tasks

* Change the old publishing DSL to use the Maven 3 classes instead of Maven 2 classes. This affects:
    * `MavenResolver.settings`
    * `MavenDeployer.repository` and `snapshotRepository`.
    * `MavenPom.dependencies`.
* Remove `MavenDeployer.addProtocolProviderJars()`.
* Change `PublishFilter` so that it accepts a `PublishArtifact` instead of an `Artifact`.

## Copy tasks

There are serveral inconsitencies and confusing behaviours in the copy tasks and copy spec:

* Change copy tasks so that they no longer implement `CopySpec`. Instead, they should have a `content` property which is a `CopySpec` that contains the main content.
  Leave behind some methods which operate on the file tree as a whole, eg `eachFile()`, `duplicatesStrategy`, `matching()`.
* Change the copy tasks so that `into` always refers to the root of the destination file tree, and that `destinationDir` (possibly with a better name) is instead used
  to specify the root of the destination file tree, for those tasks that produce a file tree on the file system.
* Change the `Jar` type so that there is a single `metaInf` copy spec which is a child of the main content, rather than creating a new copy spec each time `metainf`
  is referenced. Do the same for `War.webInf`.
* The `CopySpec.with()` method currently assumes that a root copy spec is supplied with all values specified, and no values are inherted by the attached copy spec.
  Instead, change `CopySpec.with()` so that values are inherited from the copy spec.
* Change the default duplicatesStrategy to `fail` or perhaps `warn`.
* Change the `Ear` type so that the generated descriptor takes precedence over a descriptor in the main content, similar to the manifest for `Jar` and the
  web XML for `War`.

## Archive tasks + base plugin

* Move defaults for output directory to the tasks and remove from base plugin.
* Use `${task.name}.${task.extension}` as the default archive name, so that the default does not conflict with another
  archive task.

## Test output directories

The current defaults for the outputs of tasks of type `Test` conflict with each other:

* Change the default result and report directory for the `Test` type to include the task's name, so that the default
  does not conflict with another `Test` task.

## Remove old dependency result graph

The old dependency result graph is expensive in terms of heap usage. We should remove it.

* Promote new dependency result graph to un-incubate it.
* Remove methods that use `ResolvedDependency` and `UnresolvedDependency`.
* Keep `ResolvedArtifact` and replace it later, as it is not terribly expensive to keep.

## Remove API methods that are added by the DSL decoration

Some model types hand-code the DSL conventions in their API. We should remove these and let the DSL decoration take care of this, to simplify these
types and to offer a more consistent DSL.

* Remove all methods that accept a `Closure` when an `Action` overload is available. Add missing overloads where appropriate.
* Remove all methods that accept a `String` or `Object` when a enum overload is available. Add missing overloads where appropriate.
* Remove all set methods that contain no custom logic.

## Remove GradleLauncher

The public APIs for launching Gradle is now the tooling API. The `GradleBuild` task can also be used.

## Remove tooling API support for some older versions

* Change the provider so that it refuses to work with a consumer earlier than Gradle 1.2 (we can't tell the difference between clients from 1.0-milestone-8 and 1.1).
  This means that a Gradle 1.2 or later tooling API client will be required to run builds for Gradle 2.0 and later.
* Change the consumer so that it refuses to work with a provider earlier than Gradle 1.0 (or any version earlier than 1.0-milestone-8).

## Replace StartParameter with several interfaces

Or at least remove the public constructor of `StartParameter` so that it can later be made abstract and interfaces extracted.

## Clean up `DefaultTask` hierarchy

* Inline `ConventionTask` and `AbstractTask` into `DefaultTask`.
* Remove `Task.dependsOnTaskDidWork()`.
* Mix `TaskInternal` in during decoration.
* Remove references to internal types.

## Remove references to internal classes from API

* Remove `Configurable` from public API types.
* Remove `PomFilterContainer.getActivePomFilters()`.
* Move `ConflictResolution` from public API (it's only used internally).
* Move `Module` from public API (it's only used internally).
* Move `Logging.ANT_IVY_2_SLF4J_LEVEL_MAPPER` from public API.
* Move `AntGroovydoc` and `AntScalaDoc` from public API.
* Move `BuildExceptionReporter`, `BuildResultLogger`, `TaskExecutionLogger` and `BuildLogger` from public API.

## Tooling API tidy-ups

* Remove support from the consumer for providers earlier than 1.0-milestone-8:
    * Consumer fails with a decent error message instead of falling back to the methods on `ConnectionVersion4`.
    * Add integration test coverage.
* Remove support from the provider for consumers earlier than 1.2.
    * Change the implementation of methods on `ConnectionVersion4` and `InternalConnection` to fail with a decent error message.
    * Model implementations no longer need to implement `ProjectVersion3` or the internal protocol interfaces.
    * Add integration test coverage.
* Move `UnsupportedBuildArgumentException` and `UnsupportedOperationConfigurationException` up to `org.gradle.tooling`, to remove
  package cycle from the API.

## Remove support for convention objects

Extension objects have been available for over 2 years and are now an established pattern.

* Migrate core plugins to use extensions.
* Remove `Convention` type.

## Container API tidy-ups

* Remove the specialised subclasses of `UnknownDomainObjectException` and the overridden methods that exist simply to declare this from `PluginContainer`, `ArtifactRepositoryContainer`,
  `ConfigurationContainer`, `TaskCollection`.
* Remove the specialised methods such as `whenTaskAdded()` from `PluginCollection`, `TaskCollection`
* Remove the `extends T` upper bound on the type variable of `DomainObjectCollection.withType()`.
* Remove the type varable from `ReportContainer`
* Remove unused constants from `ArtifactRepositoryContainer`
* Move `ReportContainer.ImmutableViolationException` to make top level.

## Dependency API tidy-ups

* Remove `equals()` implementations from `Dependency` subclasses.
* Remove `ExternalDependency.force`. Use resolution strategy instead.
* Remove `SelfResolvingDependency.resolve()` methods. These should be internal and invoked only as part of resolution.

## Misc API tidy-ups

* Rename `IllegalDependencyNotation` to add `Exception` to the end of its name.
* Remove unused `IllegalOperationAtExecutionTimeException`.
* Remove unused `AntJavadoc`.
* Remove `ConventionProperty`, replace it with documentation.
* Remove `Settings.startParameter`. Can use `gradle.startParameter` instead.

## Remove `sonar` plugin

Promote the `sonar-runner` plugin and remove the `sonar` plugin.

## Decorate classes at load time instead of subclassing

Decorating classes at load time is generally a more reliable approach and offers a few new interesting use cases we can support. For example, by decorating classes
at load time we can support expressions such as `new MyDslType()`, rather than requiring that Gradle control the instantiation of decorated objects.

Switching to decoration at load time should generally be transparent to most things, except for clients of `ProjectBuilder` that refer to types
which are not loaded by Gradle, such as the classes under test.

## Restructure plugin package heirarchy

## buildNeeded and buildDependents

* Rename buildDependents to buildDownstream
* Rename buildNeeded to buildUpstream
* Add a new task buildStream which is equivalent to buildDownstream buildUpstream

## build.gradle in a multiproject build

* A Gradle best pattern is to name the gradle file to be the same name as the subproject. 
* In Gradle 2.0, let's support this out of the box, possibly as a preference to `build.gradle`, and maybe drop support for `build.gradle` in subprojects.

## Why remind people about Maven?

Change from:

    repositories {
        mavenCentral()
    }

to:

    repositories {
        central()
    }
