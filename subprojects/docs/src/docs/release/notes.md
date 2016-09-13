The Gradle team is pleased to announce Gradle 3.1.

Multi-project builds are a powerful feature with one significant constraint: the projects have to be in the same directory hierarchy. This doesn't help if you want to work on code across multiple repositories, for example if you're trying to fix a bug in a third-party open-source library that one of your projects depends on.

Gradle 3.1 now supports this scenario with the introduction of **Composite Builds** for all users. It's hard to understate just how important this feature is as it provides a whole new way of organizing your projects and builds. There is more work to be done in this area and the feature is currently incubating, but we encourage you to try it out and give us your feedback!

**Incremental Build** is a similar feature in terms of impact and this release improves the control you have over its up-to-date checks. You can read about the details [further down](#incremental-build-improvements).

As with many previous Gradle releases, you will also benefit from some performance improvements, this time in the form of [**faster dependency resolution**](#faster-dependency-resolution). From testing, Android users specifically could see **up to a 50% reduction** in configuration and Android Studio sync time.

Build cancellation has improved when using the Daemon. Cancelling a build with Ctrl-C after the first build [no longer terminates the Gradle Daemon](#more-resilient-daemon). 

Our Play Framework and Kotlin build script users will also be happy as 3.1 now has (limited) support for **Play 2.5.x** and the Kotlin build script support gets a more fully-featured syntax for declaring dependencies and faster code completion.

Finally, be sure to check out the [potential breaking changes](#potential-breaking-changes) in case they affect you.


## New and noteworthy

This release includes some significant new features and major improvements for our users. Read the following sections to learn more about them.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Composite Builds

[Composite Builds](userguide/composite_builds.html) take the pain out of binary integration. They allow you to integrate independent Gradle builds, effectively giving you something like a multi-project build while keeping the builds separate. This is done by substituting project dependencies for binary dependencies at will.

They allow you to:

 - quickly verify a bug fix in a library by testing it directly against an application instead of packaging and publishing it first
 - develop and test a Gradle plugin directly against a project that uses it
 - split a large multi-project build into several smaller builds, which can be worked on independently or together

As an example, imagine you have two separate projects with independent builds: a library called _number-utils_ and an application called _my-app_. Let's say the library has this build:

    apply plugin: 'java'
    group "org.sample"
    version "1.0"

while the application's build looks like this:

    apply plugin: 'application'

    dependencies {
        compile "org.sample:number-utils:1.0"
    }

Gradle will normally just download the requested _number-utils_ jar from a repository and use it, ignoring the fact that you have the library's project source available. But now, you can force Gradle to build the _number-utils_ project and use *its* jar in place of the requested 1.0 version. Simply run the following Gradle command inside the _my-app_ project directory:

    gradle test --include-build ../number-utils

Of course, the _number-utils_ project could be located anywhere on the filesystem. You would just need to use the appropriate file path with the `--include-build` option.

This is just one technique you can use with composite build. See the [user guide](userguide/composite_builds.html) for more options.

### Faster dependency resolution

Dependency resolution is now faster for several common cases:

- When you have a large dependency graph. For example, when your build contains many projects or you use many dependencies from a repository.
- When you use dependencies from a Maven repository.
- When you depend on libraries that don't use the standard Maven `jar` packaging. For example, Android libraries use a packaging of `aar`.

Dependency resolution affects many aspects of build performance and all builds are different. Some users will see a modest drop in the resolution time while other will experience a more significant improvement. We have observed good improvements for Android builds in particular, in some cases halving the build configuration time.

### Incremental build improvements

The incremental build feature is a critical part of Gradle and for it to work properly, it needs to understand precisely when the inputs or outputs of a task have changed. Gradle 3.1 adds two more important options for specifying the rules that describe when an input is up-to-date or not, giving you more fine-grained control.

See [the table of annotations](userguide/more_about_tasks.html#sec:task_inputs_outputs) in the user guide for more details of both these features.

#### Tracking changes in the order of input files

In many cases, a collection of files is just a collection of files and tasks don't care about the order of those files. But consider the classpath for Java compilation: the compiler searches those classpath entries in the order that they're declared when looking for a class. That means the classpath is _order sensitive_.

Gradle now supports this scenario via a new `@OrderSensitive` annotation. Adding this to a property marked with `@InputFiles` or `@InputDirectory` will cause Gradle to rerun the task if the order of the files changes, even if the files themselves have not. This annotation is already in use by Gradle internally and you can also use it in your own custom task classes.

#### Better understanding of input file paths

Has a file changed if it has moved, but the content is still the same? This is an important question to answer if Gradle is to correctly determine whether a task needs to run again or not.

Previously, Gradle used the absolute path when determining whether an input file had changed or not. So if a file was moved to a different location, Gradle would run the corresponding task again. But moving a file may or may not impact the task in such a way that it needs to run again. It depends on the task.

Gradle 3.1 introduces a new path sensitivity feature that allows Gradle to observe just the relevant part of an input file's path. For example, with an `@InputFiles` property annotated with `@PathSensitive(PathSensitivity.NAME_ONLY)`, Gradle only cares about changes to the names of the input files, not their paths. So simply moving one of those files won't cause the task to run again.

### Sync can preserve files

The [Sync](dsl/org.gradle.api.tasks.Sync.html) task can now preserve files that already exist in the destination directory. Here's a demonstration of the new syntax, showing you how to specify patterns for those files you want to keep or explicitly remove:

    task sync(type: Sync) {
        from 'source'
        into 'dest'
        preserve {
            include 'extraDir/**'
            include 'dir1/**'
            exclude 'dir1/extra.txt'
        }
    }

Before this change, `Sync` would always clear the whole target directory before copying files across.

### The distribution type can be selected by the Wrapper task

The `wrapper` command has allowed you to specify a Gradle version for some time now, meaning that you only ever need to install one version of Gradle on your machine. But it always created a wrapper that downloaded the binary distribution, which excludes the Gradle source, docs, and samples.

You can now specify that you want the more complete `-all` distribution for the wrapper - particularly useful for IDEs - via the new `--distribution-type` options, like so:

    gradle wrapper --distribution-type all

The default is still the binary distribution as it's smaller.

### Initial support for Play 2.5.x

Initial support for [Play 2.5.x](userguide/play_plugin.html#sec:play_limitations) has been added.

### Improved IDEA code assistance performance for Kotlin based build scripts

Gradle 3.1 supports version 0.3.2 of [Gradle Script Kotlin](https://github.com/gradle/gradle-script-kotlin), a statically typed build language based on Kotlin.

This new version includes an improved dependencies DSL making it possible to configure all aspects of external module and project dependencies via a type-safe and IDE-friendly DSL, as shown here:

    dependencies {

        default(group = "org.gradle", name = "foo", version = "1.0") {
            isForce = true
        }

        compile(group = "org.gradle", name = "bar") {
            exclude(module = "foo")
        }

        runtime("org.gradle:baz:1.0-SNAPSHOT") {
            isChanging = true
            isTransitive = false
        }

        testCompile(group = "junit", name = "junit")

        testRuntime(project(path = ":core")) {
            exclude(group = "org.gradle")
        }

        // Client module dependencies are also supported
        runtime(
            module("org.codehaus.groovy:groovy:2.4.7") {

                // Configures the module itself
                isTransitive = false

                dependency("commons-cli:commons-cli:1.0") {
                    // Configures the external module dependency
                    isTransitive = false
                }

                module(group = "org.apache.ant", name = "ant", version = "1.9.6") {
                    // Configures the inner module dependencies
                    dependencies(
                        "org.apache.ant:ant-launcher:1.9.6@jar",
                        "org.apache.ant:ant-junit:1.9.6")
                }
            }
        )
    }

Gradle Script Kotlin 0.3.2 also ships with Kotlin 1.1-dev-2053, which greatly improves the performance of code completion within IDEA when used together with a recent Kotlin plugin version. Please check out the full [Gradle Script Kotlin v0.3.1 release notes](https://github.com/gradle/gradle-script-kotlin/releases/tag/v0.3.1), the first release to include it, for details.

Parity with the Groovy based build language was greatly increased. Taking `copySpec` as an example. Before Gradle Script Kotlin v0.3.2 one could write:

    copySpec {
        it.from("src/data")
        it.include("*.properties")
    }

With v0.3.2 it should now read:

    copySpec {
        from("src/data")
        include("*.properties")
    }

Please note that this is a _breaking change_ as many configuration patterns that previously required a qualifying `it` reference no longer do. This behavior is only enabled for non-generic Gradle API methods under the `org.gradle.api` package at this point. Subsequent releases will increasingly cover the full API.

Again for the full scoop please check out the full [Gradle Script Kotlin release notes](https://github.com/gradle/gradle-script-kotlin/releases/tag/v0.3.2).

### More resilient Daemon

In previous Gradle versions, if the Daemon client process improperly disconnected from the Daemon while a build was running, the Daemon process would exit and a subsequent build would have to start a new Daemon. In Gradle 3.1, the Daemon will now remain running and attempt to cancel the build instead. This allows subsequent builds to reuse the Daemon and reap the performance benefits of a warm Daemon. This behavior will be further improved in Gradle 3.2.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

- `org.gradle.plugins.ide.eclipse.model.ProjectDependency.setGradlePath()` has been deprecated without replacement.
- `org.gradle.plugins.ide.eclipse.model.ProjectDependency.getGradlePath()` has been deprecated without replacement.
- `org.gradle.plugins.ide.eclipse.model.ProjectDependency(String, String)` has been deprecated. Please use `org.gradle.plugins.ide.eclipse.model.ProjectDependency(String)` instead

<!--
### Example deprecation
-->

## Potential breaking changes

### Ivy Publish plugin takes into account `transitive` attribute declared for dependencies when generating metadata

Previous versions of Gradle did not reflect the `transitive` attribute for a dependency in the generated metadata if the `transitive` attribute was set to `false`. To illustrate the change
in behavior, let's assume the following dependency declaration.

    dependencies {
        compile ("commons-dbcp:commons-dbcp:1.4") {
            transitive = false
        }
    }

The generated `ivy.xml` will now properly reflect that the given dependency should not resolve any of its transitive dependencies.

    <dependencies>
        <dependency org="commons-dbcp" name="commons-dbcp" rev="1.4" conf="runtime-&gt;default" transitive="false" />
    </dependencies>

Projects consuming published artifacts generated by the Ivy Publish plugin might experience a different dependency graph than observed with earlier versions of Gradle.

### Maven plugin takes into account `exclude` attributes `group` and `module` for dependencies when generating metadata

Previous versions of Gradle did not add exclusions for dependencies in the generated metadata that either use the attribute `group` _or_ `module`. The following dependency declaration
demonstrates those use cases.

    dependencies {
        compile('org.apache.camel:camel-jackson:2.15.3') {
            exclude group: 'org.apache.camel'
        }
        compile('commons-beanutils:commons-beanutils:1.8.3') {
            exclude module: 'commons-logging'
        }
    }

The generated `pom.xml` will now properly reflect the exclusions in the generated metadata. Projects consuming published artifacts generated by the Maven plugin might
experience a different dependency graph than observed with earlier versions of Gradle.

### JNA library has been removed from Gradle distribution

We do not need [JNA](https://github.com/java-native-access/jna) anymore so we removed this library from
the Gradle distribution. Plugin authors relying on the library being present now need to ship their own.

### Tooling API models have missing dependency information when importing composite builds

When a composite build is imported via the Gradle Tooling API, then certain fields may not be populated:

- Removed incubating `HierarchicalEclipseProject.getIdentifier()` and `EclipseProjectDependency.getTarget()`
- Removed incubating `IdeaModule.getIdentifier()` and `IdeaModuleDependency.getTarget()`
- `IdeaModuleDependency.getDependencyModule()` will be `null` for a project substituted in the composite: use `IdeaModuleDependency.getTargetModuleName()` instead.
- `EclipseProjectDependency.getTarget()` will be `null` for a project substituted in the composite: use `EclipseProjectDependency.getPath()` instead.

### Dependency resolution changes when a Maven module dependency is substituted with a Gradle project dependency

In previous Gradle versions, when a Maven module is substituted with a Gradle project during dependency resolution, the result includes dependencies and artifacts from all public configurations of the target project, such as any custom configurations defined by the project's build script, when the project does not define any of the `compile`, `runtime` or `master` configurations. The Java plugin does not define a `master` configuration, which means most projects using the Java plugin would be affected by this behaviour when the target of a dependency substitution rule.

In Gradle 3.1, this has been changed so that when the target project does not define a `runtime` or `compile` configuration, the `default` configuration is used instead. The `master` configuration is included if it is defined by the project and ignored if not.

### Dependency resolution changes when a Maven module depends on an Ivy module

In previous Gradle versions, when a Maven module depends on an Ivy module and the target Ivy module does not define any of the `runtime`, `compile` or `master` configurations, the result includes all dependencies and artifacts from all public configurations of the Ivy module.

In Gradle 3.1, this has been changed so that when the target Ivy module does not define a `runtime` or `compile` configuration, the `default` configuration is used instead. The `master` configuration is included if it is defined by the Ivy module and ignored if not.

### Dependency resolution result changes

As a result of performance improvements, the dependency graph returned by the `ResolvedConfiguration.getFirstLevelModuleDependencies()` API now includes fewer `ResolvedDependency` nodes, as some nodes are merged to reduce the size of the dependency graph.

The dependency graph reported by this API for previous versions of Gradle include a `compile`, `runtime` and `master` node for a Maven module that is included as a dependency of some other Maven module. In Gradle 3.1 the dependency graph reported by this API includes a single `runtime` node for these modules.

Gradle 3.1 includes the same modules and artifacts in the result, presented in the same order, as previous Gradle versions.

### GradleConnection removed

The incubating `GradleConnection` API has been removed in favor of composite builds defined in `settings.gradle`.
New methods for fetching all models from a composite will be added to `ProjectConnection` soon.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Emmanuel Bourg](https://github.com/ebourg) - Fixed usage of deprecated constructor in signing module
- [Greg Bowyer](https://github.com/GregBowyer) - Initial support for play 2.5.x
- [Mohan Kornipati](https://github.com/mkornipati) - Publishing with the legacy Ivy support doesn't add excludes to generated metadata (GRADLE-3440)
- [Thomas Broyer](https://github.com/tbroyer) - Updating spec about annotation processing.
- [Sebastian Schuberth](https://github.com/sschuberth) - Add an option to choose the distribution type for the `Wrapper` task.
- [Sebastian Schuberth](https://github.com/sschuberth) - Fix file path to be Windows 10-compatible for Gradle build
- [Sebastian Schuberth](https://github.com/sschuberth) - Update jansi to 1.13
- [Martin Mosegaard Amdisen](https://github.com/martinmosegaard) - Checkstyle should not output XML report when only the HTML report is enabled (GRADLE-3490)
- [Stefan Neuhaus](https://github.com/stefanneuhaus) - Copy task: filesMatching and filesNotMatching should support multiple patterns
- [Johnny Lim](https://github.com/izeye) - Fix typos in documentation for Maven Publishing plugin
- [Vladislav Bauer](https://github.com/vbauer) - Update Gson library from 2.2.4 to 2.7
- [Valdis Rigdon](https://github.com/valdisrigdon) - Support for preserving files in the destination dir for Sync task

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
