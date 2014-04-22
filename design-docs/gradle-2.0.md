# Gradle 2.0

Gradle 2.0 is the next major Gradle release that offers the opportunity to make breaking changes to the public interface of Gradle. This document captures a laundry
list of ideas to consider before shipping Gradle 2.0.

Note: for the change listed below, the old behaviour or feature to be removed should be deprecated in a Gradle 1.x release, probably no later than Gradle 1.9. Similarly
for changes to behaviour.

# Planned for 2.0

The following stories are to be included in Gradle 2.0.

## Un-deprecate using the packaging declared in a Maven POM to probe for the module artifacts

Leave this behaviour in until the mechanisms to better infer the artifacts for a module have been implemented.

## Remove all features deprecated as at Gradle 1.12

In the Gradle 2.0-rc-1 release, remove all features that are deprecated as at Gradle 1.12 or earlier:

* Search for usages of `DeprecationLogger`, `@Deprecated`, `@deprecated` and remove the associated feature.
* Review usages of `DeprecationLogger.whileDisabled()`.
* Remove `JavaPluginGoodBehaviourTest#changing debug flag does not produce deprecation warning`

## Replace deprecation warnings with errors

* Convert deprecated behaviours with errors.

## Remove Ivy types from the Gradle repository API

These types expose the implementation details of dependency management and force a certain implementation on Gradle. Removing these types from the API
allows us to implement new features and remove some internal complexity.

* Remove methods from `ArtifactRepositoryContainer` and `RepositoryHandler` that accept an Ivy `DependencyResolver` as parameter.
* Remove methods from `ArtifactRepositoryContainer` that return `DependencyResolver`.
* Remove `RepositoryHandler.mavenRepo()`.
* Change the `AbstractMavenResolver` so that it no longer extends `DependencyResolver`.
* Change the `FlatDirRepository` implementation so that it no longer uses a `DependencyResolver` implementation.
* Remove Ivy packages from the Gradle API filter.
* Remove Ivy version from the output of `gradle -v`.
* Remove loopback resolver, ModuleVersionRepository -> Ivy adapter.

## Remove support for the Gradle Open API implementation (DONE)

Now that we have reasonable tooling support via the tooling API, remove the Open API.

* Implement a stub to fail with a reasonable error message when attempting to use Gradle from the Open API.
* Add integration test coverage that using the Open API fails with a reasonable error message.

Note that the `openAPI` project must still remain, so that the stubs fail in the appropriate way when used by Open API clients.
This will be removed in Gradle 3.0.

## Remove the `GradleLauncher` API (DONE)

The public API for launching Gradle is now the tooling API. The `GradleBuild` task can also be used.

* Replace internal usages of the static `GradleLauncher` methods.
* Move the `GradleLauncher` type from the public API to an internal package.

## Remove tooling API support for Gradle 1.1 clients and earlier

Gradle 1.2 was released on 12th sept 2012. This change means that tooling more than roughly 18 months old as of the Gradle 2.0 release
will not be able to invoke Gradle 2.0 or later.

* Change the implementation of methods on `ConnectionVersion4` and `InternalConnection` to fail with a decent error message.
* The model implementations no longer need to implement `ProjectVersion3` of the protocol interfaces.
* Change test suite to default to tooling API versions >= 1.2.
* Add integration test coverage that tooling API versions <1.2 fail with a reasonable error message, when running build or fetching model.

## Remove tooling API support for Gradle providers 1.0-milestone-7 and earlier

Gradle 1.0-milestone-8 was release on 14th feb 2012. This change means that tooling will not be able to run builds using Gradle versions more than
approximately 2 years old as of the Gradle 2.0 release.

* Consumer fails with a decent error message instead of falling back to the methods on `ConnectionVersion4`.
* Add support for fetching partial `BuildEnvironment` model for unsupported versions.
* Remove the appropriate `ConsumerConnection` implementations.
* Change the test suite to default to target Gradle version >= 1.0-milestone-8
* Add integration test coverage that running build with Gradle version < 1.0-milestone-8 fails with a reasonable error message, when running build or fetching model.
* Add integration test coverage that can fetch a partial `BuildEnvironment` model for Gradle version < 1.0-milestone-8.

## Misc API tidy-ups

* Remove unused `IllegalOperationAtExecutionTimeException`.
* Remove unused `AntJavadoc`.

## Reset deprecation warnings

* Remove most calls to `DeprecationLogger.whileDisabled()`

## All Gradle scripts use UTF-8 encoding

* Change Gradle script parsing to assume UTF-8 encoding.

## Upgrade to most recent Groovy 2.x

* Change the version of Groovy exposed via the Gradle API to most recent Groovy 2.x.

## Archive tasks + base plugin

* Move defaults for output directory and other attributes from the base plugin to an implicitly applied plugin, so that they are applied to all instances.
* Use `${task.name}.${task.extension}` as the default archive name, so that the default does not conflict with the default for any other archive task.

## Test output directories

The current defaults for the outputs of tasks of type `Test` conflict with each other:

* Change the default result and report directory for the `Test` type to include the task's name, so that the default
  does not conflict with the default for any other `Test` task.
* Change the default TestNG output directory.

## Remove usages of JNA and JNA-Posix (DONE)

Replace all usages of JNA and JNA-Posix with native-platform. Currently, this means that console support and
UNIX file permissions with JVMs earlier than Java 7 will not be supported on the following platforms:

* Linux-ia64
* Solaris-x86, -amd64, -sparc, -sparcv9

## Rename this spec

# Candidates for Gradle 3.0

The following stories are candidates to be included in a major release of Gradle. Currently, they are *not* scheduled to be included in Gradle 2.0.

## Remove the Gradle Open API stubs

* Remove the remaining Open API interfaces and stubs.
* Remove the `openApi` project.

## Remove `group` and `status` from project

Alternatively, default the group to `null` and status to `integration`.

## Remove the Ant-task based Scala compiler

* Change the default for `useAnt` to `false` and deprecate the `useAnt` property.

## Don't inject tools.jar into the system ClassLoader

Currently required for in-process Ant-based compilation on Java 5. Dropping support for one of (in-process, ant-based, java 5) would allow us to remove this.

## Decouple publishing DSL from Maven Ant tasks

* Change the old publishing DSL to use the Maven 3 classes instead of Maven 2 classes. This affects:
    * `MavenResolver.settings`
    * `MavenDeployer.repository` and `snapshotRepository`.
    * `MavenPom.dependencies`.
* Remove `MavenDeployer.addProtocolProviderJars()`.
* Change `PublishFilter` so that it accepts a `PublishArtifact` instead of an `Artifact`.

## Copy tasks

There are several inconsistencies and confusing behaviours in the copy tasks and copy spec:

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
* Remove CharSequence -> Enum conversion code in `DefaultTaskLogging`.
* Remove all set methods that contain no custom logic.

## Tooling API clean ups

* Move `UnsupportedBuildArgumentException` and `UnsupportedOperationConfigurationException` up to `org.gradle.tooling`, to remove
  package cycle from the API.

## Clean up `DefaultTask` hierarchy

* Inline `ConventionTask` and `AbstractTask` into `DefaultTask`.
* Remove `Task.dependsOnTaskDidWork()`.
* Mix `TaskInternal` in during decoration and remove references to internal types.

## Remove references to internal classes from API

* Remove `Configurable` from public API types.
* Remove `PomFilterContainer.getActivePomFilters()`.
* Change `StartParameter` so that it no longer extends `LoggingConfiguration`.
* Move `ConflictResolution` from public API (it's only used internally).
* Move `Module` from public API (it's only used internally).
* Move `Logging.ANT_IVY_2_SLF4J_LEVEL_MAPPER` from public API.
* Move `AntGroovydoc` and `AntScalaDoc` from public API.
* Move `BuildExceptionReporter`, `BuildResultLogger`, `TaskExecutionLogger` and `BuildLogger` out of the public API.

## Remove support for convention objects

Extension objects have been available for over 2 years and are now an established pattern.

* Migrate core plugins to use extensions.
* Remove `Convention` type.

## Container API tidy-ups

* Remove the specialised subclasses of `UnknownDomainObjectException` and the overridden methods that exist simply to declare this from `PluginContainer`, `ArtifactRepositoryContainer`,
  `ConfigurationContainer`, `TaskCollection`.
* Remove the specialised methods such as `whenTaskAdded()` from `PluginCollection`, `TaskCollection`
* Remove the `extends T` upper bound on the type variable of `DomainObjectCollection.withType()`.
* Remove the type variable from `ReportContainer`
* Remove unused constants from `ArtifactRepositoryContainer`
* Move `ReportContainer.ImmutableViolationException` to make top level.

## Dependency API tidy-ups

* Remove `equals()` implementations from `Dependency` subclasses.
* Remove `ExternalDependency.force`. Use resolution strategy instead.
* Remove `SelfResolvingDependency.resolve()` methods. These should be internal and invoked only as part of resolution.
* Remove `ClientModule` and replace with consumer-side component meta-data rules.
* Remove `ExternalModuleDependency.changing`. Use component meta-data rules instead.

## Invocation API tidy-ups

* Remove the public `StartParameter` constructor.
* Remove the public `StartParameter` constants, `GRADLE_USER_HOME_PROPERTY_KEY` and `GRADLE_USER_HOME_PROPERTY_KEY`.
* Change `StartParameter` into an interface.

## Misc API tidy-ups

* Rename `IllegalDependencyNotation` to add `Exception` to the end of its name.
* Remove `ConventionProperty`, replace it with documentation.
* Remove `Settings.startParameter`. Can use `gradle.startParameter` instead.
* Remove `org.gradle.util` from default imports.
* Remove `AbstractOptions`.

## Remove `sonar` plugin

Promote the `sonar-runner` plugin and remove the `sonar` plugin.

## Remove support for running Gradle on Java 5

Would still be able to compile for Java 5. Would need to improve cross-compilation support.

## Decorate classes at load time instead of subclassing

Decorating classes at load time is generally a more reliable approach and offers a few new interesting use cases we can support. For example, by decorating classes
at load time we can support expressions such as `new MyDslType()`, rather than requiring that Gradle control the instantiation of decorated objects.

Switching to decoration at load time should generally be transparent to most things, except for clients of `ProjectBuilder` that refer to types
which are not loaded by Gradle, such as the classes under test.

## Restructure plugin package hierarchy

## buildNeeded and buildDependents

* Rename buildDependents to buildDownstream
* Rename buildNeeded to buildUpstream
* Add a new task buildStream which is equivalent to buildDownstream buildUpstream

## build.gradle in a multiproject build

* A Gradle best pattern is to name the gradle file to be the same name as the subproject.
* Let's support this out of the box, possibly as a preference to `build.gradle`, and maybe drop support for `build.gradle` in subprojects.
