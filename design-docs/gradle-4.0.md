# Gradle 4.0

## Remove duplicate types

- `Jar` and `Jar`
- `CreateStartScripts` and `CreateStartScripts`

## Simplify definition of public API

- Ensure all internal packages have `internal` in their name
- Remove `org.gradle.util` from default imports.

Do this by deprecate-replace for those types that are reachable from the public API, for example via `DefaultTask`.  Also for types in `org.gradle.util`

## Remove support for using the project build script classloader before it has been configured

Would have to tackle implicit usage, eg `evaluationDependsOn(child-of-some-project)`, which implicitly queries the classloader of `some-project`. Similar problems when using configure on demand.

## Address issues in dynamic method invocation

Currently, we look first for a method and if not found, look for a property with the given name and if the value is a closure, treat it as a method.

- Methods inherited from an ancestor project can silently shadow these properties.
- Method matching is inconsistent with how methods are matched on POJO/POGO objects, where a method is selected only when the args can be coerced to those accepted by the method, and if not, searching continues with the next object. Should do something similar for property-as-a-method matching.
- Type conversion is not applied to the parameters to a closure, or to static methods

## Isolate dependencies to Java 5 and Java 6

Currently, many projects are limited to Java 5 and Java 6 because of the project structure. Only then entry points
need to run on Java 5 to be able to give a meaningful error message. For Java 6 it is enough that we can run tests
there. If we isolate the necessary classes to dedicated subprojects we can use Java 7 in more places in our code base.
The same is true for the next Java version upgrade.

This also makes it possible to upgrade Guava from `guava-jdk5` to a current version.

## Remove Gradle GUI

It is now straight forward to implement a rich UI using the Gradle tooling API.

* Remove the remaining Open API interfaces and stubs.
* Remove the `openApi` project.

## Drop support for using old wrapper or old Gradle runtime versions

Reduce the number of supported (wrapper-version, runtime-version) combinations. 

- Remove old and unused configuration properties from `Wrapper`
- Drop support for running old Gradle distributions (older than 2.0 say) using the wrapper
    - For example, change the `wrapper` task to refuse to configure the wrapper to point to old Gradle distributions. Don't do this change at runtime in the wrapper, to keep the wrapper as small as possible.
- Drop support for running Gradle using old wrapper
    - For example, change the `wrapper` task to do some validation, or change `wrapper` to always use the newest wrapper (downloading as required), or provide some way for the runtime to query which wrapper it has been launched from.

## Drop support for old versions of things

- Cached artefact reuse for versions older than 2.0.
- Execution of task classes compiled against Gradle versions older than 2.0.
- Cross version tests no longer test against anything earlier than 1.0
- Local artifact reuse no longer considers candidates from the artifact caches for Gradle versions earlier than 1.0
- Remove old unused types that are baked into the bytecode of tasks compiled against older versions (eg `ConventionValue`). Fail with a reasonable
error message for these task types or generate as required.

## Logging changes

* Remove `Project.getLogging()` method. Would be replaced by the existing `logging` property on `Script` and `Task`.

## Archive tasks + base plugin

* Remove `org.gradle.api.tasks.bundling.Jar`, replaced by `org.gradle.jvm.tasks.Jar`.
* Move defaults for output directory and other attributes from the base plugin to an implicitly applied plugin, so that they are applied to all instances.
* Use `${task.name}.${task.extension}` as the default archive name, so that the default does not conflict with the default for any other archive task.

## Remove Ant <depend> based incremental compilation backend

Now we have real incremental Java compilation, remove the `CompileOptions.useDepend` property and related options.

## Remove `group` and `status` from project

Alternatively, default the group to `null` and status to `integration`.

## Remove the Ant-task based Scala compiler

* Change the default for `useAnt` to `false` and deprecate the `useAnt` property.
* Do a better job of matching up the target jvm version with the scala compiler backend.

## Don't inject tools.jar into the system ClassLoader

Currently required for in-process Ant-based compilation on Java 5. Dropping support for one of (in-process, ant-based, java 5) would allow us to remove this.

## Stable Maven plugin

Ideally, replace with new publishing plugin. In the meantime:

* Change the old publishing DSL to use the Maven 3 classes instead of Maven 2 classes. This affects:
    * `MavenResolver.settings`
    * `MavenDeployer.repository` and `snapshotRepository`.
    * `MavenPom.dependencies`.
* Remove `MavenDeployer.addProtocolProviderJars()`.
* Change `PublishFilter` so that it accepts a `PublishArtifact` instead of an `Artifact`.
* Change `MavenDeployer.repository {... }` DSL to use the same configuration DSL as everywhere else, rather than custom owner-first DSL.

## Decouple file and resource APIs from project and task APIs

Currently the file and resource APIs reference the `Task` type (via dependencies on the `Buildable` type), and the `Task` type reference the file and resource APIs (via project factory methods). This means that the file and resource APIs cannot be used in a context where tasks do not make sense, and prevents separating these two API and their implementation into separate pieces.

## Copy tasks

There are several inconsistencies and confusing behaviours in the copy tasks and copy spec:

* Change copy tasks so that they no longer implement `CopySpec`. Instead, they should have a `content` property which is a `CopySpec` that contains the main content.
  Leave behind some methods which operate on the file tree as a whole, eg `eachFile()`, `duplicatesStrategy`, `matching()`.
* Change the copy tasks so that `into` always refers to the root of the destination file tree, and that `destinationDir` (possibly with a better name) is instead used
  to specify the root of the destination file tree, for those tasks that produce a file tree on the file system.
* Change the `Jar` type so that there is a single `metaInf` copy spec which is a child of the main content, rather than creating a new copy spec each time `metainf`
  is referenced. Do the same for `War.webInf`.
* The `CopySpec.with()` method currently assumes that a root copy spec is supplied with all values specified, and no values are inherited by the attached copy spec.
  Instead, change `CopySpec.with()` so that values are inherited from the copy spec.
    * Change `CopySpec` so that property queries do not query the parent value, as a copy spec may have multiple parents. Or, alternatively, allow only root copy spec
      to be attached to another using `with()`.
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
* Remove CharSequence -> Enum conversion code in `DefaultTestLogging`.
* Remove all set methods that contain no custom logic.
* Formally document the Closure → Action coercion mechanism
    - Needs to be prominent enough that casual DSL ref readers understand this (perhaps such Action args are annotated in DSL ref)

## Tooling API clean ups

* `LongRunningOperation.withArguments()` should be called `setArguments()` for consistency.
* Remove the old `ProgressListener` interfaces and methods. These are superseded by the new interfaces. However, the new interfaces are supported only
  by Gradle 2.5 and later, so might need to defer the removal until 4.0.
* Move `UnsupportedBuildArgumentException` and `UnsupportedOperationConfigurationException` up to `org.gradle.tooling`, to remove
  package cycle from the API.

## Clean up `Task` DSL and hierarchy

* Remove the `<<` operator.
* Inline `ConventionTask` and `AbstractTask` into `DefaultTask`.
* Remove `Task.dependsOnTaskDidWork()`.
* Mix `TaskInternal` in during decoration and remove references to internal types from `DefaultTask` and `AbstractTask`

## Remove references to internal classes from public API

* Remove `Configurable` from public API types.
* Remove `PomFilterContainer.getActivePomFilters()`.
* Move rhino worker classes off public API

## Remove support for convention objects

Extension objects have been available for over 5 years and are now an established pattern. Supporting the mix-in of properties and methods in using convention objects also has implications for performance (more places to look for properties, requires reflective lookup) and statically compiled DSL (more places to look for data)

* Migrate core plugins to use extensions.
* Remove `Convention` type.

## Project no longer inherits from its parent project

* Project should not delegate to its build script for missing properties or methods.
* Project should not delegate to its parent for missing properties or methods.
* Project build script classpath should not inherit anything from parent project.

## Container API tidy-ups

* Remove the specialised subclasses of `UnknownDomainObjectException` and the overridden methods that exist simply to declare this from `PluginContainer`, `ArtifactRepositoryContainer`,
  `ConfigurationContainer`, `TaskCollection`.
* Remove the specialised methods such as `whenTaskAdded()` from `PluginCollection`, `TaskCollection`
* Remove the `extends T` upper bound on the type variable of `DomainObjectCollection.withType()`.
* Remove the type variable from `ReportContainer`
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

* Replace `TaskDependency.getDependencies(Task)` with `TaskDependency.getDependencies()`.
* Remove constants from `ExcludeRule`.
* Rename `IllegalDependencyNotation` to add `Exception` to the end of its name.
* Remove `ConventionProperty`, replace it with documentation.
* Remove `Settings.startParameter`. Can use `gradle.startParameter` instead.
* Remove `AbstractOptions`.
* Replace `ShowStacktrace.INTERNAL_EXCEPTIONS` with `NONE`.

## Deprecate and remove unintended behaviour for container configuration closures

When the configuration closure for an element of a container fails with any MethodMissingException, we attempt to invoke the method on the owner of the closure (e.g. the Project).
This allows for the following constructs to work:
```
configurations {
    repositories {
        mavenCentral()
    }
    someConf {
        allprojects { }
    }
}
```

The corresponding code is in ConfigureDelegate and has been reintroduced for backwards compatibility (https://github.com/gradle/gradle/commit/79d084e16050b02cc566f71df3c3ad7a342b9c5a ).

This behaviour should be deprecated and then removed in 4.0.

## Signing plugin tidy-ups

- `SignatoryProvider` and sub-types should use container DSL instead of custom DSL.

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

## Miscellaneous cleanups

* Remove `ExtensionContainerInternal`