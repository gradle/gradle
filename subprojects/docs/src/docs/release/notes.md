## New and noteworthy

Here are the new features introduced in this Gradle release.

### Continuous build (i)

The new continuous build support allows Gradle to automatically start building in response to file system changes.
When you run with the `--continuous` or `-t` command line options, Gradle will not exit at the end of a build.
Instead, Gradle will wait for files that are processed by the build to change.
When changes are detected, Gradle will re-run the previous build with the same task selection.

For instance, if you run `gradle -t build` in a typical Java project, main and test sources will be built and tests will be run.
If changes are made to the project's main sources, Gradle will rebuild the main Java sources and re-run the project's tests.
If changes are made to the project's test sources, Gradle will only rebuild the test Java sources and re-run the project's tests.

This is just the initial iteration of this feature, which will improve in coming releases.
Future releases will increase the scope of “changes” to include more than just local input files.
For example, the scope of considered “changes” may expand in the future to encompass dependencies in remote repositories along with other types of inputs that affect the build result.

For more information, please see the [new User Guide chapter](userguide/continuous_build.html).

### Dependency substitution rules (i)

Previous versions of Gradle allowed an external dependency to be replaced with another using a ['Dependency Resolve Rule'](https://docs.gradle.org/1.4/release-notes#dependency-resolve-rules).
With the introduction of 'Dependency Substitution Rules' this behaviour has been enhanced and extended to allow external dependencies and project dependencies to be replaced interchangeably.

When combined with a configurable Gradle settings file, these dependency substitution rules permit some powerful new ways of working with Gradle:

* While developing a patch for an external library, use a local project dependency instead of a module dependency.
* For a large multi-project build, only develop with a subset of the projects locally, downloading the rest from an external repository.
* Extensions to Gradle such as [Prezi Pride](https://github.com/prezi/pride) no longer require a custom dependency syntax.

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

It is also possible to substitute a project dependency with another, or a module dependency with another.
(The latter provides the same functionality as `eachDependency`, with a more convenient syntax).

Note: adding a dependency substitution rule to a `Configuration` used as a task input changes the timing of when that configuration is resolved.
Instead of being resolved on first use, the `Configuration` is instead resolved when the task graph is being constructed.
This can have unexpected consequences if the configuration is being further modified during task execution,
or if the configuration relies on modules that are published during execution of another task.

For more information consult the [User Guide](userguide/dependency_management.html#dependency_substitution_rules)
and the [DSL Reference](dsl/org.gradle.api.artifacts.DependencySubstitutions.html).

This feature was contributed by [Lóránt Pintér](https://github.com/lptr) and the team at [Prezi](https://prezi.com).

### Progress events via the Tooling API (i)

It is now possible to receive progress events for various build operations.
Listeners can be provided to the [`BuildLauncher`](javadoc/org/gradle/tooling/BuildLauncher.html) via the
[`addProgressListener(ProgressListener)`](javadoc/org/gradle/tooling/LongRunningOperation.html#addProgressListener\(org.gradle.tooling.events.ProgressListener\)) method.
Events are fired when the task graph is being populated, when each task is executed, when tests are executed, etc.

All operations are part of a single-root hierarchy that can be traversed through the operation descriptors via
[`ProgressEvent.getDescriptor()`](javadoc/org/gradle/tooling/events/ProgressEvent.html#getDescriptor\(\)) and
[`OperationDescriptor.getParent()`](javadoc/org/gradle/tooling/events/OperationDescriptor.html#getParent\(\)).

If you are only interested in the progress events for a sub-set of all available operations, you can use
[`LongRunningOperation.addProgressListener(ProgressListener, Set<OperationType>)`](javadoc/org/gradle/tooling/LongRunningOperation.html#addProgressListener\(org.gradle.tooling.events.ProgressListener,%20java.util.Set\)).
For example, you may elect to only receive events for the execution of tasks.

One potential use of this new capability would be to provide interesting visualisations of the build execution.

### Simpler default dependencies (i)

Many Gradle plugins allow the user to specify a dependency for a particular tool, supplying a default version only if none is provided by the user.
A common implementation of this involves using a `beforeResolve()` hook to check if the configuration has any dependencies, adding the default dependency if not.
The new [`defaultDependencies()`](dsl/org.gradle.api.artifacts.Configuration.html#org.gradle.api.artifacts.Configuration:defaultDependencies\(org.gradle.api.Action\))
method has been introduced to make this simpler and more robust.

The use of `beforeResolve()` to specify default dependencies will continue to work,
but will emit a deprecation warning if the configuration has already participated in dependency resolution when it is first resolved (see below).

Specifying a default dependency with `beforeResolve` (deprecated):

    def util = dependencies.create("org.gradle:my-util:1.0")
    conf.incoming.beforeResolve {
        if (conf.dependencies.empty) {
            conf.dependencies.add(util)
        }
    }

Specifying a default dependency with `defaultDependencies` (recommended):

    def util = dependencies.create("org.gradle:my-util:1.0")
    conf.defaultDependencies { dependencies ->
        dependencies.add(util)
    }

See the <a href="dsl/org.gradle.api.artifacts.Configuration.html#org.gradle.api.artifacts.Configuration:defaultDependencies(org.gradle.api.Action)">DSL reference</a> for more details.

### Precompiled header support (i)

Precompiled headers are a performance optimization for native builds that allows commonly used headers to be parsed only once rather than for each file that includes the headers.
Precompiled headers are now supported for C, C++, Objective-C and Objective-C++ projects.

To use a precompiled header, a header file needs to created containing all of the headers that should be precompiled.
This header file is then declared in the build script as a precompiled header.

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

Each source set can have a single precompiled header defined.
Any source file that includes this header file as the first header will be compiled using the precompiled header.
Otherwise, the precompiled header will be ignored and the source file will be compiled in the normal manner.

Please see the [User Guide](userguide/nativeBinaries.html#native_binaries:preCompiledHeaders) for further information.

### Google Test support (i)

The new Gradle `google-test` plugin provides support for compiling and executing [GoogleTest](https://code.google.com/p/googletest/) tests in your native binary project.
You simply need to include your Google Test test sources in a conventional location (i.e. `src/«component name»Test/cpp`).

Gradle will build a test executable and run your tests.

<pre><tt>> gradle -q runFailingOperatorsTest

[==========] Running 2 tests from 1 test case.
[----------] Global test environment set-up.
[----------] 2 tests from OperatorTests
[ RUN      ] OperatorTests.test_plus
src/operatorsTest/cpp/test_plus.cpp:8: Failure
Value of: plus(0, -2) == -2
 Actual: false
Expected: true
[  FAILED  ] OperatorTests.test_plus (0 ms)
[ RUN      ] OperatorTests.test_minus
[       OK ] OperatorTests.test_minus (0 ms)
[----------] 2 tests from OperatorTests (0 ms total)

[----------] Global test environment tear-down
[==========] 2 tests from 1 test case ran. (0 ms total)
[  PASSED  ] 1 test.
[  FAILED  ] 1 test, listed below:
[  FAILED  ] OperatorTests.test_plus

1 FAILED TEST</tt></pre>

See the [User Guide](userguide/nativeBinaries.html#native_binaries:google_test) to learn more.
Expect deeper integration with Google Test (and other native testing tools) in the future.

### Obtaining a task's group via the Tooling API

It is now possible to obtain the “group” of a task via [`org.gradle.tooling.model.Task.getGroup()`](javadoc/org/gradle/tooling/model/GradleTask.html#getGroup\(\)).

### New model improvements

The model report for [Rule based model configuration](userguide/new_model.html) has been enhanced to display string representations of some values.
This allows the effective values of the build model to be visualised, not just the structure as was the case previously.

[`@Managed`](javadoc/org/gradle/model/Managed.html) models can now have managed model properties of type `java.io.File`.

[`@Managed`](javadoc/org/gradle/model/Managed.html) types can now implement the [`Named`](javadoc/org/gradle/api/Named.html) interface.
The `name` property will be automatically populated based on the objects location in the model graph.

It is now possible for [`@Managed`](javadoc/org/gradle/model/Managed.html) types to declare properties of type [`ModelMap<T>`](javadoc/org/gradle/model/ModelMap.html).

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

### Deprecations in Eclipse model
As part of the changes in the IDE classpath generation, the following properties have been deprecated:

- `EclipseClasspath#noExportConfigurations`
- `ProjectDependency#declaredConfigurationName`
- `AbstractLibrary#declaredConfigurationName`

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

#### Changes in NativeBinariesTestPlugin

- `TestSuiteContainer` no longer implements `ExtensiblePolymorphicDomainObjectContainer<TestSuiteSpec>`.
- `TestSuiteContainer` now implements `ModelMap<TestSuiteSpec>`.
- All configuration done using subject of type `TestSuiteContainer` is now deferred. In earlier versions of Gradle it was eager.

### Maven publishing

The [maven-publish](userguide/publishing_maven.html) and [maven](userguide/maven_plugin.html) plugins
no longer use the Maven 2 based [Maven ant tasks](https://maven.apache.org/ant-tasks/) libraries to publish artifacts.
Both plugins now use the newer Maven 3 and Aether libraries.
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

The incubating API `BuildLauncher.addTestProgressListener()` and the associated listener type have been removed.
This API has been superseded by the new [progress listener API](#progress-events-via-the-tooling-api).

### Changes in IDE classpath generation

Project files generated by the Gradle Idea and Eclipse plugins are responsible for deriving the classpath from the declared list of dependencies in the build file.
Some incorrect behavior, resulting in incorrect classpaths in the IDE, has been rectified in this Gradle version.

Let's assume project A and B are part of a multi-project build.
Project B declares a project dependency on project A.
The generated classpath of project B is a union of the classpath of project A (the generated JAR file plus its dependencies) and its own declared top-level dependencies and transitive dependencies.
In practice, this means that when project A and B depend on a specific library with different versions, the "exported" dependency version wins as it happens to be listed first in the classpath of project B.
This behaviour might lead to compilation and runtime issues in the IDE as no conflict-resolution takes place across projects.

To avoid the situation just described, the IDE classpath generation now more closely reflects the classpath as it is used by Gradle:

- All transitive external and project dependencies of a project are listed as direct dependencies in the project.
    - Different versions of a library are resolved by Gradle conflict resolution.
- All dependencies in projects are marked as `exported = false`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Daniel Lacasse](https://github.com/Shad0w1nk) - Support Google Test for testing C++ binaries
* [Lóránt Pintér](https://github.com/lptr), [Daniel Vigovszky](https://github.com/vigoo) and [Mark Vujevits](https://github.com/vujevits) - implement dependency substitution for projects
* [Larry North](https://github.com/LarryNorth) - Build improvements.
* [Tobias Schulte](https://github.com/tschulte) - Documentation improvements.
* [Stefan Oehme](https://github.com/oehme) - Addition of `Project.copy(Action)` and `Project.copySpec(Action)`.
* [Mikolaj Izdebski](https://github.com/mizdebsk) - Upgrade of the Maven publishing mechanisms to use Aether libraries.
* [Lorin Hochstein](https://github.com/lorin) - Improvements to the ANTLR plugin documentation.

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
