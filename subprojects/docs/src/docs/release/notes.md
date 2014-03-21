
Gradle 1.12 will be the last release before Gradle 2.0

## New and noteworthy

Here are the new features introduced in this Gradle release.

### CUnit integration (i)

The new Gradle `cunit` plugin provides support for compiling and executing CUnit tests in your native-binary project.

You simply need to include your CUnit test sources, and provide a hook for Gradle to register the suites and tests defined.

    #include <CUnit/Basic.h>
    #include "gradle_cunit_register.h"
    #include "operator_tests.h"

    void gradle_cunit_register() {
        CU_pSuite mySuite = CU_add_suite("operator tests", suite_init, suite_clean);
        CU_add_test(mySuite, "test plus", test_plus);
        CU_add_test(mySuite, "test minus", test_minus);
    }

Gradle will then generate the required boiler-plate CUnit code, build a test executable, and run your tests.

    > gradle -q runFailingOperatorsTest

    There were test failures:
      1. /home/user/gradle/samples/native-binaries/cunit/src/operatorsTest/cunit/test_plus.c:6  - plus(0, -2) == -2
      2. /home/user/gradle/samples/native-binaries/cunit/src/operatorsTest/cunit/test_plus.c:7  - plus(2, 2) == 4

    BUILD FAILED


See the [user guide chapter](docs/userguide/nativeBinaries.html#native_binaries:cunit) and the cunit sample (`samples/native-binaries/cunit`)
in the distribution to learn more. Expect deeper integration with CUnit (and other native testing tools) in the future.

### Component metadata rules can control whether a component version is considered changing (i)

Component metadata rules can now control whether a component version is considered changing, or in other words, whether the contents
of one and the same component version may change over time. (A common example for a changing component version is a Maven snapshot dependency.)
This makes it possible to implement custom strategies for deciding if a component version is changing. In the following example, every
component version whose group is `my.company` and whose version number ends in `-dev` will be considered changing:

    dependencies {
        components {
            eachComponent { ComponentMetadataDetails details ->
                details.changing =
                    details.id.group == "my.company" &&
                        details.id.version.endsWith("-dev")
            }
        }
    }

This feature is especially useful when dealing with Ivy repositories, as it is a generalized form of Ivy's `changingPattern` concept.

### Tooling API exposes information on a project's publications (i)

Tooling API clients can now get basic information on a project's publications:

    def projectConnection = ...
    def projectPath = ':myProject'
    def action = { BuildController bc ->
        BasicGradleProject project = bc.buildModel.getProjects().find { it -> projectPath.equals(it.getPath()) }
        bc.getModel(project, ProjectPublications.class).getPublications()
    } as BuildAction
    def publications = projectConnection.action(action).run()
    for (publication in project.publications) {
        println publication.id.group
        println publication.id.name
        println publication.id.version
    }

Both publications declared in the old (`artifacts` block, `Upload` task) and new (`publishing.publications` block) way are reflected in the result.

### Tooling API exposes more information on how to launch a Gradle build (i)

A new model BuildInvocations is added for Tooling API clients to find objects that can be used when launching a build.
Currently tasks and task selectors (also called aggregated tasks [GRADLE-2434]).


### New API for artifact resolution (i)

Gradle 1.12 introduces a new, incubating API for resolving component artifacts. With this addition, Gradle now offers separate dedicated APIs for resolving
components and artifacts. (Component resolution is mainly concerned with computing the dependency graph, whereas artifact resolution is
mainly concerned with locating and downloading artifacts.) The entry points to the component and artifact resolution APIs are `configuration.incoming` and
`dependencies.createArtifactResolutionQuery()`, respectively.

Here is an example usage of the new API:

```
def query = dependencies.createArtifactResolutionQuery()
    .forComponent("org.springframework", "spring-core", "3.2.3.RELEASE")
    .forArtifacts(JvmLibrary)

def result = query.execute() // artifacts are downloaded at this point

for (component in result.components) {
    assert component instanceof JvmLibrary
    println component.id
    component.sourceArtifacts.each { println it.file }
    component.javadocArtifacts.each { println it.file }
}

assert result.unresolvedComponents.isEmpty()
```

Artifact resolution can be limited to selected artifact types:

```
def query = dependencies.createArtifactResolutionQuery()
    .forComponent("org.springframework", "spring-core", "3.2.3.RELEASE")
    .forArtifacts(JvmLibrary, JvmLibrarySourcesArtifact)

def result = query.execute()

for (component in result.components) {
    assert !component.sourceArtifacts.isEmpty()
    assert component.javadocArtifacts.isEmpty()
}
```

Artifacts for many components can be resolved together:

```
def query = dependencies.createArtifactResolutionQuery()
    .forComponents(setOfComponentIds)
    .forArtifacts(JvmLibrary)
```

So far, only one component type (`JvmLibrary`) is available, but others will follow, also for platforms other than the JVM.

### Easier to determine ignored tests in HTML test report

The HTML test report now has a dedicated tab for ignored tests, at the overview and package level.
This makes it much easier to see which tests were ignored at a glance.
Thanks to [Paul Merlin](https://github.com/eskatos) for this improvement.

### Support for building large zips

It is now possible to build zips with the [Zip64 extension](http://en.wikipedia.org/wiki/Zip_\(file_format\)#ZIP64), enabling building large zip files.

    task largeZip(type: Zip) {
        from 'lotsOfLargeFiles'
        zip64 = true
    }

The zip standard does not support containing more than 65535 files, containing any file greater than 4GB or being greater than 4GB compressed.
If your zip file meets any of these criteria, then the zip must be built with the
[`zip64` property](dsl/org.gradle.api.tasks.bundling.Zip.html#org.gradle.api.tasks.bundling.Zip:zip64) set to `true` (it is `false` by default).
This flag also applies to all JARs, WARs, EARs and anything else that uses the Zip format.

However, not all Zip readers support the Zip64 extensions.
Notably, the `ZipInputStream` JDK class does not support Zip64 for versions earlier than Java 7.
This means you should not enable this property if you are building JARs to be used with Java 6 and earlier runtimes.

Thanks to [Jason Gauci](https://github.com/MisterTea) for this improvement.

### Consumption of active POM profile information

Publishing artifacts to a Maven repository including their profile information for relying on properties, dependencies or dependency management defaults
is not a recommended practice. Reality is that a significant number of published artifacts make use of this practice which leads to unresolvable transitive
dependencies. Gradle is now able to consume this metadata for profiles that are marked as [active by default](https://maven.apache.org/pom.html#Activation).
The following profile metadata is parsed and evaluated:

* Properties elements
* Dependency management elements

### Customise Clang tool chain (i)

TODO - You can now configure the Clang tool chain in the same way as the GCC tool chain, using the `cCompiler`, `cppCompiler` etc properties.

### Updated Visual Studio project file generation (i)

A few minor tweaks to the VS project files generated by the 'visual-studio' plugin.

* Visual studio log files are generated into `.vs` instead of the project directory.
* Project files are named by `ProjectNativeComponent.name` instead of `ProjectNativeComponent.baseName`.
* Header files co-located with source files are now include in the generated project.

### Updated mapping of dependencies to IDEA classpath scopes

There were changes in IDEA project mapping related to bug reports [GRADLE-2017] and [GRADLE-2231].

* Projects generated by [IDEA plugin](userguide/idea_plugin.html) now better map project dependencies to classpath scopes in IDEA modules.
* IDEA integration will not need to depend on internal implementation details thus can provide more stable support across Gradle releases.

### Easier debugging of JVM `Test` and `JavaExec` processes (i)

The [`Test`](dsl/org.gradle.api.tasks.testing.Test.html) and [`JavaExec`](dsl/org.gradle.api.tasks.JavaExec.html) tasks both now support a `--debugJvm` invocation time switch, which is equivalent
to setting the `debug` property of these tasks to `true`.

This makes it easy, for example, to launch the application in debug mode when using the [Application plugin](userguide/application_plugin.html)…

<pre><tt>gradle run --debugJvm</tt></pre>

This starts the JVM process in debug mode, and halts the process until a debugger attaches on port 5005.
The same can be done for any [`Test`](dsl/org.gradle.api.tasks.testing.Test.html) task.

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
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

### Deprecations in Tooling API communication

* Using Tooling API to connect to provider using older distribution than Gradle 1.0-milestone-8 is now deprecated and scheduled for removal in version Gradle 2.0.
* Using Tooling API client version older than 1.2 to connect to a provider from current distribution is now deprecated and scheduled for removal in version Gradle 2.0.

## Potential breaking changes

### Incremental Scala compilation

The version of 'zinc' tool that Gradle uses to perform incremental scala compilation has been upgraded to a newer version (0.3.0).
This might be a breaking change for users who explicitly configured some low version of zinc in their build scripts (there should be very few such users, if any).

### Changes to incubating native support

* '-Xlinker' is no longer automatically added to linker args for GCC or Clang. If you want to pass an argument directly to 'ld' you need to add this escape yourself.
* Tasks for windows resource compilation are now named 'compileXXXX' instead of 'resourceCompileXXX'.

### Change to JUnit XML file for skipped tests

The way that skipped/ignored tests are represented in the JUnit XML output file produced by the `Test` task.
Gradle now produces the same output, with regard to skipped tests, as Apache Ant and Apache Maven.
This format is accepted, and expected, by all major Continuous Integration servers.

This change is described as follows:

1. The `testsuite` element now contains a `skipped` attribute, indicating the number of skipped tests (may be 0)
2. The element representing a test case is now always named `testcase` (previously it was named `ignored-testcase` if it was a skipped test)
3. If a test case was skipped, a child `<skipped/>` element will be present

No changes are necessary to build scripts or Continuous Integration server configuration to accommodate this change.

### Ordering of dependencies in imported Ant builds

The ordering of Ant target dependencies is now respected when possible.
This may cause tasks of imported Ant builds to executed in a different order from this version of Gradle on.

Given…

    <target name='a' depends='d,c,b'/>

A shouldRunAfter [task ordering](userguide/more_about_tasks.html#sec:ordering_tasks) will be applied to the dependencies so that,
`c.shouldRunAfter d` and `b.shouldRunAfter c`.

This is in alignment with Ant's ordering of target dependencies.

### Invalid large zip files now fail the build

If a zip file is built without the `zip64` flag (new in this release) set to true that surpasses the file size and count limits
of the zip format, Gradle will now fail the build.
Previously, it may have silently created an invalid zip.

To allow the large zip to be correctly built, set the `zip64` property of the task to `true`.

### Change to signature of `Test.filter(Closure)`

The incubating `Test.filter(Closure)` method introduced in 1.10 for configuring the `TestFilter` has been changed to be more consistent with other configuration methods.
This method now accepts an `Action` and no longer returns the `TestFilter`.
This change should not require any adjustments to build scripts as this method can still be called with a `Closure`, upon which it will be implicitly converted into an `Action`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Marcin Erdmann](https://github.com/erdi)
    * dependency ordering of imported Ant targets [GRADLE-1102]
    * fixes for excluding tasks [GRADLE-2974] & [GRADLE-3031]
* [Jesse Glick](https://github.com/jglick) - enabling newlines in option values passed to build
* [Zeeke](https://github.com/zeeke) - documentation improvements
* [Kamil Szymański](https://github.com/kamilszymanski) - documentation improvements
* [Jakub Kubryński](https://github.com/jkubrynski) - handling of empty string proxy system property values
* [Lee Symes](https://github.com/leesdolphin) & [Greg Temchenko](https://github.com/soid) - fix skipped test representation in JUnit XML result files [GRADLE-2731]
* [Ivan Vyshnevskyi](https://github.com/sainaen) - Fixes to HTML test report
* [Vincent Cantin](https://github.com/green-coder) - documentation improvements
* [Sterling Greene](https://github.com/big-guy) - Support for developing Gradle in Eclipse
* [Matthew Michihara](https://github.com/matthewmichihara) - Documentation improvements
* [Andrew Oberstar](https://github.com/ajoberstar) - Improved Sonar support on Java 1.5 [GRADLE-3005]
* [Mark Johnson](https://github.com/elucify) - documentation improvements
* [Paul Merlin](https://github.com/eskatos) - ignored tests tab for HTML test report
* [Jason Gauci](https://github.com/MisterTea) - Support for large zips
* [Zsolt Kúti](https://github.com/tinca) - Fixes for Gradle on FreeBSD

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
