## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Faster dependency resolution

TBD: faster for large dependency graphs
TBD: faster for dependencies from Maven repositories
TBD: faster for Android builds

### Incremental build improvements

#### Tracking changes in the order of input files

Gradle now recognizes changes in the order of files for classpath properties as a reason to mark a task like `JavaCompile` out-of-date. The new `@OrderSensitive` annotation can be used on task input properties to turn this feature on in custom tasks.

#### Better understanding of input file paths

Previously, Gradle considered the absolute path of input files when determining if a task was up-to-date: if an input file was moved or renamed, even without changing it's contents, the task would be out-of-date. With the new path sensitivity feature Gradle can now observe just the relevant part of an input file's path. For example, with an `@InputFiles` property annotated with `@PathSensitive(PathSensitivity.NAME_ONLY)` only changes to the name of the input files will mark the task as out-of-date, but moving the files around won't.

### Sync can preserve files

With the [Sync](dsl/org.gradle.api.tasks.Sync.html) task it is now possible to preserve files that already exist in the destination directory.

    task sync(type: Sync) {
        from 'source'
        into 'dest'
        preserve {
            include 'extraDir/**'
            include 'dir1/**'
            exclude 'dir1/extra.txt'
        }
    }

### The distribution type can be selected by the Wrapper task

For the [Wrapper](userguide/gradle_wrapper.html#sec:wrapper_generation) task, it is now possible to select a distribution type other than the default of `bin` by using `--distribution-type`.

    gradle wrapper --distribution-type all

### Initial support for Play 2.5.x

Initial support for [Play 2.5.x](userguide/play_plugin.html#sec:play_limitations) has been added.

### Improved IDEA code assistance performance for Kotlin based build scripts

Gradle 3.1 supports version 0.3.1 of [Gradle Script Kotlin](https://github.com/gradle/gradle-script-kotlin), a statically typed build language based on Kotlin.

This new version includes an improved dependencies DSL making it possible to configure all aspects of external module and project dependencies via a type-safe and IDE friendly DSL:

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
    }

Gradle Script Kotlin 0.3.1 also ships with Kotlin 1.1-dev-2053 greatly improving the performance of code assistance within IDEA when used together with a recent Kotlin plugin version. Please check out the full [Gradle Script Kotlin release notes](https://github.com/gradle/gradle-script-kotlin/releases/tag/v0.3.1) for details.

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

<!--
### Example deprecation
-->

## Potential breaking changes

### Ivy Publish plugin takes into account `transitive` attribute declared for dependencies when generating metadata

Previous versions of Gradle did no reflect `transitive="false"` for a dependency in the generated metadata if the `transitive` attribute was set to `false`. To illustrate the change
in behavior, let's assume the following dependency declaration.

    dependencies {
        compile "commons-dbcp:commons-dbcp:1.4", {
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
- `IdeaModuleDependency.getDependencyModule` will be `null` for a project substituted in the composite: use `IdeaModuleDependency.getTargetModuleName` instead.

TODO:DAZ Revisit if this breakage is necessary and/or reasonable. We might just deprecate these methods.

### Dependency resolution changes when a Maven module dependency is substituted with a Gradle project dependency

TBD: Previously, the result would include dependencies and artifacts from all configurations of the target project, such as test compilation and runtime dependencies.
TBD: Previously, when the target project did not have a 'runtime', 'compile' or 'master' configuration, the result would include all dependencies and artifacts from all configurations of the target project. When 'runtime' or 'compile' is not defined the 'default' configuration is used instead. A missing 'master' configuration is ignored.

### Dependency resolution changes when a Maven module depends on an Ivy module

TBD: Previously, when the target Ivy module did not define a 'runtime', 'compile' or 'master' configuration, the result would include all dependencies and artifacts from all configurations of the Ivy module. When 'runtime' or 'compile' is not defined the 'default' configuration is used instead. A missing 'master' configuration is ignored.

### Dependency resolution result changes

TBD: As a result of performance improvements, the result includes fewer `ResolvedDependency` nodes, as some nodes are merged. Previous versions of Gradle would include a `compile`, `runtime` and `master` node for transitive Maven dependencies. Gradle now includes a single `runtime` node. The same modules and artifacts are included in the result, and in the same order.

### GradleConnection removed

The incubtaing `GradleConnection` API has been removed in favor of composite builds defined in `settings.gradle`.
New methods for fetching all models from a composite will be added to `ProjectConnection` in Gradle 3.2

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
