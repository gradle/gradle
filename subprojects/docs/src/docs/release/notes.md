## New and noteworthy

Here are the new features introduced in this Gradle release.

### Dependency substitution rules (i)

Previous versions of Gradle allowed an external dependency to be replaced with another using a 'Dependency Resolve Rule'.
With the introduction of 'Dependency Substitution Rules' this behaviour has been enhanced and extended,
which allow external dependencies and project dependencies to be replaced interchangeably.

When combined with a configurable Gradle settings file, this dependency substitution rules permit some powerful new ways of working with Gradle:

* While developing a patch for an external library, use a local project dependency instead of a module dependency.
* For a large multi-project build, only develop as subset of the projects locally, downloading the rest from an external repository.
* Enhancements to Gradle like [Prezi Pride](https://github.com/prezi/pride) no longer require a custom dependency syntax.

Substitute a project dependency with an external module dependency like this:

    resolutionStrategy {
        dependencySubstitution {
            substitute project(":api") with module("org.utils:api:1.3")
        }
    }

Alternatively, an external dependency can be replaced with a project dependency like this:

    resolutionStrategy {
        dependencySubstitution {
            substitute module("org.utils:api") with project(":api")
        }
    }

It is also possible to substitute a project dependency with another, or a module dependency with another. (The latter provides the same functionality
as `eachDependency`, with a more convenient syntax).

Note: adding a dependency substitution rule to a `Configuration` used as a task input changes the timing of when that configuration is resolved.
Instead of being resolved on first use, the `Configuration` is instead resolved when the task graph is being constructed. This can have unexpected
consequences if the configuration is being further modified during task execution, or if the configuration relies on modules that are published during
execution of another task.

For more information consult the [User Guide](userguide/dependency_management.html#dependency_substitution_rules)
and the [DSL Reference](dsl/org.gradle.api.artifacts.DependencySubstitutions.html).

Thanks go to Lóránt Pintér and his team at [Prezi](https://prezi.com) for providing much of the implementation of this feature.

### Specify default dependencies for a Configuration (i)

Many Gradle plugins allow the user to specify a dependency for a particular tool, supplying a default version only if none is
provided by the user. A common mechanism to do this involves using a `beforeResolve` hook to check if the configuration has any
dependencies, adding the appropriate dependency if not.

This mechanism does not work well with inherited or referenced configurations, so a new `defaultDependencies` method
has been introduced for supplying default dependencies for a configuration. The use of `beforeResolve` to specify default dependencies
will continue to work, but will emit a deprecation warning if the configuration has already participated in dependency resolution
when it is first resolved (see below).

Specify default dependency with `beforeResolve` (deprecated):

    def util = dependencies.create("org.gradle:my-util:1.0")
    conf.incoming.beforeResolve {
        if (conf.dependencies.empty) {
            conf.dependencies.add(util)
        }
    }

Specify default dependency with `defaultDependencies` (recommended):

    def util = dependencies.create("org.gradle:my-util:1.0")
    conf.defaultDependencies { dependencies ->
        dependencies.add(util)
    }

See the <a href="dsl/org.gradle.api.artifacts.Configuration.html#org.gradle.api.artifacts.Configuration:defaultDependencies(org.gradle.api.Action)">DSL reference</a> for more details.

### Support for Precompiled Headers (i)

Precompiled headers are a performance optimization for native builds that allows commonly used headers to be compiled only once rather than for
each file that includes the headers.  Precompiled headers are now supported for C, C++, Objective-C and Objective-C++ projects.

To use a precompiled header, a header file needs to defined containing all of the headers that should be precompiled.  This header file is
then declared in the build script as a precompiled header.

    model {
        components {
            hello(NativeLibrarySpec) {
                sources {
                    cpp {
                        preCompiledHeader "pch.h"
                    }
                }
            }
        }
    }

Each source set can have a single precompiled header defined.  Any source file that includes this header file as the first header will
be compiled using the precompiled header.  Otherwise, the precompiled header will be ignored and the source file will be compiled in the
normal manner.  Please see the [userguide](userguide/nativeBinaries.html#native_binaries:preCompiledHeaders) for further information.

### Google Test support (i)

- TBD

### Continuous build (i)

The new continuous build support allows Gradle to automatically start building in response to file system changes.
When you run with the `--continuous` command line option, Gradle will not exit at the end of a build.
Instead, Gradle will wait for something to change.
When changes are detected, Gradle will re-run the previous build with the same task selection.

For instance, if you run `gradle --continuous build` in a typical Java project, main and test sources will be built and tests will be run.
If changes are made to the project's main sources, Gradle will rebuild the main Java sources and re-run the project's tests.
If changes are made to the project's test sources, Gradle will only rebuild the test Java sources and re-run the project's tests.

For more information, please see the [new User Guide chapter](userguide/continuous_build.html).

### Task group accessible from the Tooling API

Tasks in Gradle may define a _group_ attribute, but this group wasn't accessible from the Tooling API before. It is now possible to query the
group of a task through `org.gradle.tooling.model.Task#getGroup`.

### Progress events for build operations through the Tooling API

You can now listen to progress events for various build operations through `org.gradle.tooling.LongRunningOperation.addProgressListener(org.gradle.tooling.events.ProgressListener)`. You
will receive all available events as the Gradle build being executed goes through its life-cycle. For example, you will receive events when the
settings are being loaded, when the task graph is being populated, when the tasks are being executed, when each task is executed, when the tests
are executed, etc. All operations are part of a single-root hierarchy that can be traversed through the operation descriptors via `org.gradle.tooling.events.ProgressEvent#getDescriptor`
and `org.gradle.tooling.events.OperationDescriptor#getParent`.

If you are only interested in the progress events for a sub-set of all available operations, you can use
`org.gradle.tooling.LongRunningOperation.addProgressListener(org.gradle.tooling.events.ProgressListener, java.util.Set<org.gradle.tooling.events.OperationType>)`. For example, you
can configure to only receive events for the execution of task operations.

Progress events for more fine-grained operations will be added in future releases of Gradle.

### New model improvements

- The model report for [Rule based model configuration](userguide/new_model.html) has been enhanced to display string representations of some values.
This allows the effective values of the build model to be visualised, not just the structure as was the case previously.

- [`@Managed`](javadoc/org/gradle/model/Managed.html) models can now have managed model properties of type `java.io.File`.

[`@Managed`](javadoc/org/gradle/model/Managed.html) types can now implement the [`Named`](javadoc/org/gradle/api/Named.html) interface.
The `name` property will be automatically populated based on the objects location in the model graph.

It is now possible to declare properties of type [`ModelMap<T>`](javadoc/org/gradle/model/ModelMap.html), where `T` is-a [`Named`](javadoc/org/gradle/api/Named.html).

- TBD: Also means finer grained rules and improved performance (more efficient model implementation, rules, etc).

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

### Changing a configuration after it has participated in dependency resolution

Unexpected behaviour can result from changing a configuration after it has participated in dependency resolution. Examples include:

* Change the dependencies of a parent of a configuration: the child configuration will get different dependencies depending on when
  it is resolved.
* Changing the artifacts of a configuration referenced as a project dependency: whether the referencing project gets those artifacts
  depends on the order that configurations are resolved.

Previous versions of Gradle prevented the resolved configuration itself from being modified, but did nothing to
prevent modifications to related configurations after they have participated in dependency resolution. This version of Gradle extends
the checks, emitting a deprecation warning when a modification is made to a configuration that has been referenced in dependency
resolution.

One exception is that changes to the `ResolutionStrategy` of a configuration can be made at any time until that configuration is
itself resolved. Changes to the strategy do not impact the resolution of child configurations, or configurations referenced as
project dependencies. Thus, these changes are safe to make.

### Distribution Plugin changes

Due to a bug in the distribution plugin (see GRADLE-3278), earlier Gradle versions didn't follow the general naming convention for the assemble task of the main distribution.
This has been fixed and assemble task name for the main distribution has changed from `assembleMainDist` to `assembleDist`.

### Deprecation of `CollectionBuilder` and `ManagedSet`.

The types `org.gradle.model.collection.CollectionBuilder` and `org.gradle.model.collection.ManagedSet` have been deprecated and replaced by
[`org.gradle.model.ModelMap`](javadoc/org/gradle/model/ModelMap.html) and [`org.gradle.model.ModelSet`](javadoc/org/gradle/model/ModelSet.html) respectively.

As these types were incubating, they will be removed before Gradle 3.0.
Please change your usage to the new `ModelMap` and `ModelSet` types.

## Potential breaking changes

### Changes to the new configuration and component model

As work continues on the new configuration and component model for Gradle, many changes to the new incubating plugins and types
are required. These changes should have no impact on builds that do not leverage these new features.

#### Removal of `componentSpec` project extension

As part of work on exposing more of the component model to rules the `componentSpec` project extension previously added by all language plugins via `ComponentModelBasePlugin` has been removed.
Currently component container can be only accessed using model rules.

#### Changes to `ComponentSpecContainer`

- `ComponentSpecContainer` no longer implements `ExtensiblePolymorphicDomainObjectContainer<ComponentSpec>`.
- `ComponentSpecContainer` now implements `ModelMap<ComponentSpec>`.
- All configuration done using subject of type `ComponentSpecContainer` is now deferred. In earlier versions of Gradle it was eager.

#### Changes to `ComponentSpec`
- `getSource()` now returns a `ModelMap<LanguageSourceSet>` instead of `DomainObjectSet<LanguageSourceSet>`
- `sources()` now takes a `Action<? super ModelMap<LanguageSourceSet>>` instead of `Action<? super PolymorphicDomainObjectContainer<LanguageSourceSet>>`
- `getBinaries()` now returns a `ModelMap<BinarySpec>` instead of `DomainObjectSet<BinarySpec>`
- `binaries()` now takes a `Action<? super ModelMap<BinarySpec>>` instead of `Action<? super DomainObjectSet<BinarySpec>>`
- Source sets and binaries cannot be removed

#### Changes to source sets container of `BinarySpec`
- `getSource()` now returns a `ModelMap<LanguageSourceSet>` instead of `DomainObjectSet<LanguageSourceSet>`
- `sources()` now takes a `Action<? super ModelMap<LanguageSourceSet>>` instead of `Action<? super PolymorphicDomainObjectContainer<LanguageSourceSet>>`

#### Changes in NativeBinariesTestPlugin

- `TestSuiteContainer` no longer implements `ExtensiblePolymorphicDomainObjectContainer<TestSuiteSpec>`.
- `TestSuiteContainer` now implements `ModelMap<TestSuiteSpec>`.
- All configuration done using subject of type `TestSuiteContainer` is now deferred. In earlier versions of Gradle it was eager.

#### ManagedSet renamed to ModelSet

The, incubating, `org.gradle.model.collection.ManagedSet` type has been renamed to `org.gradle.model.ModelSet`.

### Maven publishing

The [maven-publish](userguide/publishing_maven.html) and [maven](userguide/maven_plugin.html) plugins
no longer use the Maven 2 based [Maven ant tasks](https://maven.apache.org/ant-tasks/) libraries to publish artifacts.
Both plugins now use the newer Maven 3 `org.apache.maven` and Aether libraries.
Whilst the API's exposed by both plugins remain unchanged, the underlying publishing libraries have been upgraded.

### Java annotation processing of Groovy code is now disabled by default

[Gradle 2.4 introduced the ability to use Java annotation processors on Groovy sources](https://docs.gradle.org/2.4/release-notes#support-for-“annotation-processing”-of-groovy-code).
If annotation processors were present on the classpath, they were implicitly applied to Groovy source.
This caused problems in situations where a separate “processing” strategy was required for joint compiled Groovy source (GRADLE-3300).

Java annotation processing of Groovy source is now disabled by default.
It can be enabled by setting the
[`javaAnnotationProcessing` property of `GroovyCompileOptions`](dsl/org.gradle.api.tasks.compile.GroovyCompileOptions.html#org.gradle.api.tasks.compile.GroovyCompileOptions:javaAnnotationProcessing)

    compileGroovy {
        groovyOptions.javaAnnotationProcessing = true
    }

### Registering test progress listeners through the Tooling API

The incubating API `org.gradle.tooling.LongRunningOperation.addTestProgressListener(org.gradle.tooling.events.test.TestProgressListener)` and the associated listener type
`org.gradle.tooling.events.test.TestProgressListener` have been removed. If you still want to listen exclusively to test progress events, you can use the new API
`org.gradle.tooling.LongRunningOperation.addProgressListener(org.gradle.tooling.events.ProgressListener, java.util.Set<org.gradle.tooling.events.OperationType>)` and pass in
`java.util.Collections.singleton(OperationType.TEST)` for the set of operations.

### Changes in IDE classpath generation

Project files generated by the Gradle Idea and Eclipse plugins are responsible for deriving the classpath from the declared list of dependencies in the build file.
So far the behaviour of classpath generation for the IDE metadata files could cause conflicts on the classpath.
The Let's assume project A and B. Both projects are part of a multi-project build. Project B declares a project dependency on project A. The generated classpath
of project B is a union of the classpath of project A (the generated JAR file plus its dependencies) and its own declared top-level dependencies and transitive dependencies. Classpath
ordering matters. In practice this means the following: given that project A and B depend on a specific library with different versions, the "exported" dependency versions win as they happens
to be listed first in classpath of project B. This behavior might lead to compilation and runtime issues in the IDE as no conflict-resolution takes place across projects.

To fix the behaviour described above, we changed the IDE classpath generation to reflect the classpath as it is used by Gradle:

- all transitive external and project dependencies of a project are listed as direct dependencies in the eclipse project.
    - different versions of a library are resolved by the Gradle conflict resolution.
- all dependencies in projects are marked as `exported = false`.



## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Daniel Lacasse](https://github.com/Shad0w1nk) - Support GoogleTest for testing C++ binaries
* [Lóránt Pintér](https://github.com/lptr), [Daniel Vigovszky](https://github.com/vigoo) and [Mark Vujevits](https://github.com/vujevits) - implement dependency substitution for projects
* [Larry North](https://github.com/LarryNorth) - Build improvements.
* [Tobias Schulte](https://github.com/tschulte) - Documentation improvements.
* [Stefan Oehme](https://github.com/oehme) - Addition of `Project.copy(Action)` and `Project.copySpec(Action)`.
* [Mikolaj Izdebski](https://github.com/mizdebsk) - Upgrade of the Maven publishing mechanisms to use Aether libraries.
* [Lorin Hochstein](https://github.com/lorin) - Improvements to the ANTLR plugin documentation.

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
