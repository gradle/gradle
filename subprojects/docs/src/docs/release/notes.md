The Gradle team is pleased to announce Gradle 4.9.

First and foremost, this version of Gradle features experimental new _lazy task API_.
In a nutshell, the new API allows builds to avoid the cost of creating and configuring tasks during Gradle's [configuration phase](userguide/build_lifecycle.html) when those tasks will never be executed.
This makes _every_ Gradle invocation faster, including IDE syncs.

Gradle projects that have adopted these new APIs have seen 10-15% faster configuration times. This chart shows the performance impact for a few projects.

![lazy task perf chart](data:image/png;base64,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)

You will see configuration times reduced more and more as Gradle tasks are converted to use them.
Gradle task and plugin authors: we need your feedback on this new API!
Please read [the documentation](userguide/task_configuration_avoidance.html), try it out in non-production environments, and [file issues](https://github.com/gradle/gradle/issues) on GitHub or discuss on [the forum](https://discuss.gradle.org).

Next, publishing tools get some more love: projects that publish auxiliary publications (e.g. test fixtures) through `maven-publish` and `ivy-publish` can now be [depended upon by other projects](https://github.com/gradle/gradle/issues/1061) in the same build.
There is also a [new Publishing Overview chapter](userguide/publishing_overview.html) in the user manual and updates throughout the documentation regarding publishing artifacts using Maven and Ivy.

In addition to lazy tasks use, Kotlin DSL build scripts are evaluated faster with version 0.18.4, included in this version of Gradle.
IntelliJ IDEA and Android Studio user experience is also improved.
See details in the [Kotlin DSL v0.18.x release notes](https://github.com/gradle/kotlin-dsl/releases/tag/v0.18.4).

You can now pass arguments to `JavaExec` tasks [directly from the command-line](#command-line-args-supported-by-javaexec) using `--args`:

    ❯ gradle run --args 'foo --bar'
    
No more need to hard-code arguments in your build scripts. 
Consult the documentation for the [Application Plugin](userguide/application_plugin.html#sec:application_usage) for more information.

Last but not least, this version of Gradle has an _improved dependency insight report_. Read the [details further on](#improved-dependency-insight-report).   

We hope you will build happiness with Gradle 4.9, and we look forward to your feedback [via Twitter](https://twitter.com/gradle) or [on GitHub](https://github.com/gradle).

## Upgrade Instructions

Switch your build to use Gradle 4.9 RC1 quickly by updating your wrapper properties:

`./gradlew wrapper --gradle-version=4.9-rc-1`

Standalone downloads are available at [gradle.org/release-candidate](https://gradle.org/release-candidate). 

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Command line args supported by JavaExec

Command line arguments can be passed to `JavaExec` with `--args`. For example, if you want to launch the application with command line arguments `foo --bar`,
you don't need to hardcode it into the build script - you can just run `gradle run --args 'foo --bar'`.
See the [Application Plugin documentation](userguide/application_plugin.html#sec:application_usage) for more information.

### Improved dependency insight report

The [dependency insight report](userguide/inspecting_dependencies.html#sec:identifying_reason_dependency_selection) is the distant ancestor of [build scans](https://scans.gradle.com) and helps you diagnose dependency management problems locally.
This release of Gradle implements several improvements:

- using `failOnVersionConflict()` no longer fails the dependency insight report in case of conflict
- all participants of conflict resolution are shown
- modules which were rejected by a rule are displayed
- modules which didn't match the version selector but were considered in selection are shown
- all custom reasons for a component selection are shown
- ability to restrict the report to one path to each dependency, for readability
- resolution failures are displayed in the report

### Continuing development of Native ecosystem

[The Gradle Native project continues](https://github.com/gradle/gradle-native/blob/master/docs/RELEASE-NOTES.md#changes-included-in-gradle-49) to improve and evolve the native ecosystem support for Gradle.

### Faster clean checkout builds

Gradle now stores more state in the Gradle user home instead of the project directory. Clean checkout builds on CI should now be faster as long as the user home is preserved.

### Java and Groovy compiler no longer leak file descriptors

The Java and Groovy compilers both used to leak file descriptors when run in-process (which is the default).
This could lead to "cannot delete file" exceptions on Windows and "too many open file descriptors" on Unix.
These leaks have been fixed.  If you had switched to forking mode because of this problem, it is now safe to switch back to in-process compilation.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Dependency insight report

The dependency insight report is now considered stable.

### Tooling API types and methods

Many types and methods that were previously marked `@Incubating` are now considered stable. 

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### `EclipseProject` tasks defined for `gradle eclipse` may now run in Buildship

The [EclipseClasspath](dsl/org.gradle.plugins.ide.eclipse.model.EclipseClasspath.html) and [EclipseProject](dsl/org.gradle.plugins.ide.eclipse.model.EclipseProject.html) tasks both accept `beforeMerged` and `whenMerged` closures, for advanced Eclipse-specific customisation.

Previous versions of Gradle did not execute the closures defined in `EclipseProject` when invoked from Buildship (only those in `EclipseClasspath`). Now Gradle executes them both, similarly to when invoked from the command-line.

This leads to a potential change of behavior in this scenario:
 - These closures were defined for use with `gradle eclipse`
 - The gradle project was later imported into Eclipse, but these definitions were not removed.

The code in these closures will now become active in the `Gradle -> Refresh Gradle Project` action.

<!--
### Example breaking change
-->

### Using Groovy GPath with `tasks.withType()`

In previous versions of Gradle, it was sometimes possible to use a [GPath](http://docs.groovy-lang.org/latest/html/documentation/#gpath_expressions) expression with a project's task collection to build a list of a single property for all tasks.

For instance, `tasks.withType(SomeTask).name` would create a list of `String`s containing all of the names of tasks of type `SomeTask`. This was only possible with the method [`TaskCollection.withType(Class)`](javadoc/org/gradle/api/tasks/TaskCollection.html#withType-java.lang.Class-).

Plugins or build scripts attempting to do this will now get a runtime exception.  The easiest fix is to explicitly use the [spread operator](http://docs.groovy-lang.org/latest/html/documentation/#_spread_operator).

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Luke Usherwood](https://github.com/lukeu) Fix `ClassCastException` when generating Eclipse files for Gradle (gradle/gradle#5278)
- [Luke Usherwood](https://github.com/lukeu) Make Buildship's "Refresh Gradle Project" action honour more of the `EclipseProject` task (eclipse/buildship#694)
- [Theodore Ni](https://github.com/tjni) Reduce string allocations when working with paths. (gradle/gradle#5543)
- [Theodore Ni](https://github.com/tjni) Suppress redundant warning message (gradle/gradle#5544)
- [Lars Grefer](https://github.com/larsgrefer) Remove dependencies between `javadoc` tasks of dependent Java projects (gradle/gradle#5221)
- [Aaron Hill](https://github.com/Aaron1011) Continue executing tests if irreplaceable security manager is installed (gradle/gradle#5324)
- [Jonathan Leitschuh](https://github.com/JLLeitschuh) Throw `UnknownDomainObjectException` instead of `NullPointerException` when extension isn't found (gradle/gradle#5547)
- [thc202](https://github.com/thc202) Fix typo in TestKit chapter (gradle/gradle#5691)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
