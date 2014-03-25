
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

<pre><tt>> gradle -q runFailingOperatorsTest

There were test failures:
  1. /home/user/gradle/samples/native-binaries/cunit/src/operatorsTest/cunit/test_plus.c:6  - plus(0, -2) == -2
  2. /home/user/gradle/samples/native-binaries/cunit/src/operatorsTest/cunit/test_plus.c:7  - plus(2, 2) == 4

BUILD FAILED</tt></pre>

See the [user guide chapter](docs/userguide/nativeBinaries.html#native_binaries:cunit) and the cunit sample (`samples/native-binaries/cunit`)
in the distribution to learn more. Expect deeper integration with CUnit (and other native testing tools) in the future.

### Component metadata rules can control whether a component version is considered changing (i)

Component metadata rules ([introduced in Gradle 1.8](http://www.gradle.org/docs/1.8/release-notes#component-metadata-rules)) can now be used to specify whether a component version is considered _changing_.

A _changing_ component is expected to change over time without a change to the version number.
A commonly used and well understood example of a changing component is a “`-SNAPSHOT`” dependency from an Apache Maven repository (which Gradle implicitly considers to be changing).

This new feature makes it possible to implement custom strategies for deciding if a component version is changing.
In the following example, every component version whose group is “`my.company`” and whose version number ends in “`-dev`” will be considered changing:

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

See [ComponentMetadataHandler](javadoc/org/gradle/api/artifacts/dsl/ComponentMetadataHandler.html) for more information.

### Tooling API exposes information on a project's publications (i)

The [Tooling API](userguide/embedding.html) is a mechanism for embedding Gradle and/or driving Gradle programmatically. The new [`ProjectPublications` Tooling API model type](javadoc/org/gradle/tooling/model/gradle/ProjectPublications.html) provides basic information about a project's publications.

The following example demonstrates, in Groovy, using the `ProjectPublications` model to print out the group/name/version of each publication.

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

The [Tooling API](userguide/embedding.html) is a mechanism for embedding Gradle and/or driving Gradle programmatically. The new [`BuildInvocations` Tooling API model type](javadoc/org/gradle/tooling/model/gradle/BuildInvocations.html) provides information about the possible ways to invoke the build.

It provides the invokable tasks of a project, and importantly also its applicable task _selectors_.
A task selector effectively refers to all of the tasks of a project and its children of the same name.
For example, it is common in a multi project build for all projects to have a `build` task.
Invoking the build via the Tooling API with the `build` task _selector_ would effectively build the entire multi project build.
In contrast, Invoking the build with the `build` _task_ would only build the root project (or which ever project is being targeted).

This new capability makes it easier for integrators to provide more powerful interfaces for invoking Gradle builds.

### Easier to identify ignored tests in HTML test report

The HTML test report now has a dedicated tab for ignored tests, at the overview and package level.
This makes it much easier to see which tests were ignored at a glance.

Thanks to [Paul Merlin](https://github.com/eskatos) for this improvement.

### Support for building large zips

It is now possible to build zips with the [Zip64 extension](http://en.wikipedia.org/wiki/Zip_\(file_format\)#ZIP64), enabling the building of large zip files.

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

### Support for consuming Apache Maven POMs with active profiles

Gradle now respects POM profiles that are [active by default](https://maven.apache.org/pom.html#Activation), during dependency resolution.
More specifically, the properties and dependency management information is now respected.

### Customise Clang tool chain (i)

TODO - You can now configure the Clang tool chain in the same way as the GCC tool chain, using the `cCompiler`, `cppCompiler` etc properties.

### Improved Visual Studio project file generation (i)

Gradle 1.11 add support for [generating Visual Studio configuration files](http://www.gradle.org/docs/current/release-notes#generate-visual-studio-configuration-for-a-native-binary-project).
This feature has been improved in the following ways in Gradle 1.12:

* Visual studio log files are generated into `.vs` instead of the project directory
* Project files are named by `ProjectNativeComponent.name` instead of `ProjectNativeComponent.baseName`
* Header files co-located with source files are now include in the generated project

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

### Tooling API version compatibility

The [Tooling API](userguide/embedding.html) is a mechanism for embedding Gradle and/or driving Gradle programmatically.
It is used by IDEs and other _tooling_ to integrate with Gradle.

* Connecting to 1.0-milestone-8 and earlier providers is now deprecated (in effect, the Gradle 2.0 Tooling API client will not work with 1.0-milestone-8 and earlier builds)
* Client versions older than 1.2 are now deprecated (in effect, the Gradle 1.2 and earlier Tooling API clients will not work with Gradle 2.0 and later builds)

If your project is building with Gradle 1.0-milestone-8 or earlier, you are __strongly__ encouraged to upgrade to a more recent Gradle version.
All versions of integrating tools released since the release of Gradle 1.2 (September 2012) should be using a Tooling API client newer than version 1.2.

## Potential breaking changes

### Incremental Scala compilation

The version of the Scala incremental compiler, Zinc, that Gradle uses has been upgraded to a version 0.3.0.
This might be a breaking change for users who explicitly configured a low version of zinc in their build scripts.
There should be very few such users, if any.

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
