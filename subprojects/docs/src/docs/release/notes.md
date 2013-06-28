

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Faster Gradle builds

Gradle 1.7 is the fastest version of Gradle yet. Here are the highlights:

- Dependency resolution is now faster (improving many aspects of most builds).
- Test execution is now faster (particularly for tests that generate a lot of logging output).
- Build script compilation is much faster (75% faster than Gradle 1.6).
- Parallel execution mode is now faster.

As always, the performance improvements that you actually see for your build depends on many factors.

#### Faster dependency resolution due to in-memory caching of artifact meta-data

With this change, the dependency resolution is much faster. Typically, the larger the project is the more configurations and dependencies are resolved during the build.
By caching the artifact meta-data in memory Gradle avoids parsing the descriptor when the same dependency is requested multiple times in a build.

An incremental build for a large project should be tangibly faster with Gradle 1.7.
A full build may be much faster, too. The level of performance improvement depends on the build.
If a large portion of the build time is taken by slow integration tests, the performance improvements are smaller.
Nevertheless, some of the large builds that were used for benchmarking show up to 30% speed increase.

Caching the artifact metadata in-memory is very important for local repositories, such as `mavenLocal()` and for resolution of snapshots / dynamic versions.
Prior to Gradle 1.7, every time a local dependency was resolved, Gradle would load the dependency metadata directly from the local repository.
With the in-memory caching of dependency metadata, this behavior now *changes*.
During a single build, a given dependency will be loaded once only and will not be reloaded again from the repository.

This may be a breaking change for builds that depend the on the fact that certain dependencies are reloaded from the repository during each resolution.
Bear in mind that the vast majority of builds will enjoy faster dependency resolution offered by the in-memory caching.
If your project requires reloading of snapshots or local dependencies during the build please let us know so that Gradle can better understand your scenario and model it correctly.

You can also turn off the in-memory dependency metadata cache via a system property:

    //gradle.properties
    systemProp.org.gradle.resolution.memorycache=false

To avoid increased heap consumption, the in-memory dependency metadata cache may clear the cached data when there is heap pressure.

#### Improved multiprocess locking

This change improves the mechanism that Gradle uses to coordinate multi-process access to the Gradle caches. This new mechanism means that the Gradle process now
requires far fewer operations on the file system and can make better use of in-memory caching, even in the presence of multiple Gradle processes accessing the
caches concurrently.

The caches used for dependency resolution and for incremental build up-to-date checks are affected by this change, meaning faster dependency resolution and incremental
build checks.

Coupled with this change are some improvements to the synchronization of worker threads within a given Gradle process, which means parallel execution mode is now
more efficient.

The new mechanism is biased to the case where a single Gradle process is running on a machine. There should not be any performance regressions when
multiple Gradle processes are used, but please raise a problem report via the [Gradle Forums](http://forums.gradle.org) if you observe a regression.

#### Faster build script compilation

This change improves build script compilation by adding some caching in critical points in the classloader hierarchy. This affects, for example, first time users of a build, build authors, and those upgrading a build to a new Gradle version.

### Finalizer tasks (i)

Thanks to a contribution by Marcin Erdmann, Gradle 1.7 introduces a new task ordering rule that allows a task to _finalize_ some other task.

TODO - more stuff goes here

### TestNG parameters included in test reports (i)

TestNG supports [parameterizing test methods](http://testng.org/doc/documentation-main.html#parameters), allowing a particular test method to be executed multiple times with different inputs.
Previously in Gradle's test reports, parameterized methods were listed multiple times (for each parameterized iteration) with no way to differentiate the executions.
The test reports now include the `toString()` values of each parameter for each iteration, making it easy to identify the data set for a given iteration.

Given a TestNG test case:

    import org.testng.annotations.*;

    public class ParameterizedTest {
        @Test(dataProvider = "1")
        public void aParameterizedTestCase(String var1, String var2) {
            …
        }

        @DataProvider(name = "1")
        public Object[][] provider1() {
            return new Object[][] {
               {"1", "2"},
               {"3", "4"}
            };
        }
    }

The test report will show that the following test cases were executed:

* `aParameterizedTestCase(1, 2)`
* `aParameterizedTestCase(3, 4)`

This includes Gradle's own HTML test report and the “JUnit XML” file.
The “JUnit XML” file is typically used to convey test execution information to the CI server running the automated build, which means the parameter info is also visible via the CI server.

### `Test` task implements standard `Reporting` interface

The `Reporting` interface provides a standardised way to control the reporting aspects of tasks that produce reports. The `Test` task type now implements this interface.

    apply plugin: "java"
    
    test {
        reports {
            html.enabled = false
            junitXml.destination = file("$buildDir/junit-xml")
        }
    }

The `Test` task provides a [`ReportContainer`](javadoc/org/gradle/api/reporting/ReportContainer.html) of type [`TestReports`](javadoc/org/gradle/api/tasks/testing/TestReports.html),
giving control over both the HTML report and the JUnit XML result files (these files are typically used to communicate test results to CI servers and other tools).

This brings the `Test` task into line with other tasks that produce reports in terms of API. It also allows you to completely disable the JUnit XML file generation 
if you don't need it.

### Build dashboard improvements (i)

The above change (`Test` task implements standard `Reporting` interface) means that the test reports now appear in the [build dashboard](userguide/buildDashboard_plugin.html).

Also, the `buildDashboard` task is automatically executed when any reporting task is executed (by way of the new “Finalizer Task” mechanism mentioned earlier).
 
### Record test output per test case in JUnit XML result files (i)

This change facilitates better reporting of test execution on CI servers, notably [Jenkins](http://jenkins-ci.org/).

The JUnit XML file format is a de-facto standard for communicating test execution results between systems. 
CI servers typically use this file as the source of test execution information. 
It was originally conceived by the “JUnit Ant Tasks” that quickly appeared after the introduction of JUnit and became widely used, without a specification ever forming.

This file also captures the system output (`System.out` and `System.err`) that occurs during test execution. Traditionally, the output has been recorded at the _class level_.
That is, output is not associated with the individual test cases (i.e. methods) within the class but with the class as a whole.
You can now enable “output per test case” mode in Gradle to get better reporting.

    test {
        reports {
            junitXml.outputPerTestCase = true
        }
    }

With this mode enabled, the XML report will associate output to the particular test case that created it. 
The Jenkins CI server provides a UI for inspecting the result of a particular test case of class. 
With `outputPerTestCase = true`, output from that test case will be shown on that screen.

This is also necessary for effective use of the Jenkins [JUnit Attachments Plugin](https://wiki.jenkins-ci.org/display/JENKINS/JUnit+Attachments+Plugin) that allows
associating test attachments (e.g. Selenium screen shots) with test execution in the Jenkins UI.

### Generate Gradle wrapper files without touching your build script (i)

In Gradle 1.7 all files necessary to run your build with the Gradle Wrapper can be generated without explicitly declaring a task of type `Wrapper` in your build scripts.
By just running

    gradle wrapper

The Gradle Wrapper files are generated pointing to the gradle version used to generate the wrapper files. To customize the wrapper task you can easily modify the task in your build script:

    wrapper{
        gradleVersion = '1.6'
    }

If you already defined a task of type `Wrapper` in your build script, this task will be used when running `gradle wrapper`; otherwise the implicit default task will be used.

### Generate a Gradle project from scratch (i)

The `build-setup` plugin now supports declaring a project type when setting up a build. With version 1.7 Gradle, now supports `java-library` as a setup project type
which generates a simple build file with the java plugin applied, a sample junit test class and a sample production code class if no sources already exist.
To declare the project type you have to specify a `--type` command line argument:

    gradle setupBuild --type java-library

### Added option to deal with duplicate files in archives and copy operations (i)

When copying files with duplicate relative paths in the target archive (or directory), you can now specify the strategy for dealing with these duplicate files by
using `FileCopyDetails`.

    task zip(type: Zip) {
        from 'dir1'
        from 'dir2'
        archiveName = 'MyZip.zip'
        eachFile { it.duplicatesStrategy = 'exclude' }
    }

TODO - include info on spec level setting

### Major improvements to C++ project support (i)

Gradle has had basic support for C++ projects for some time. This is now expanding with the goal of positioning Gradle as the best build
system available for native code projects. 

This includes:

- Creating and linking to static libraries
- Building with different C++ toolchains (Visual C++, GCC, etc)
- Building multiple variants of a single binary with different target architectures, build types (debug vs release), operating systems etc.
- Variant-aware dependency resolution
- Much more: see [https://github.com/gradle/gradle/blob/master/design-docs/continuous-delivery-for-c-plus-plus.md](https://github.com/gradle/gradle/blob/master/design-docs/continuous-delivery-for-c-plus-plus.md)

Some of these features are included in Gradle 1.7 (see below), while others can be expected in the upcoming releases.

#### Improved native component model

A key part of improving C++ support is an [improved component model](userguide/cpp.html#N15643) which supports building multiple binary outputs for
a single defined [native component](dsl/org.gradle.nativecode.base.NativeComponent.html).
Using this model Gradle can now produce both a static and shared version of any [library component](dsl/org.gradle.nativecode.base.Library.html).

#### Can build static libraries from C++ sources

For any library declared in your C++ build, it is now possible to either compile and link the object files into a shared library,
or compile and archive the object files into a static library (or both). For any library 'lib' added to your project,
Gradle will create a 'libSharedLibrary' task to link the shared library, as well as a 'libStaticLibrary' task to create the static library.

Please refer to the [User Guide chapter](userguide/cpp.html) and the included C++ samples for more details.

#### Can specify defines, compilerArgs and linkerArgs for each C++ binary produced

Each binary to be produced from a C++ project is associated with a set of compiler and linker command-line arguments, as well as macro definitions.
These settings can be applied to all binaries, an individual binary, or selectively to a group of binaries based on some criteria.

    binaries.all {
        // Define a preprocessor macro for every binary
        define "NDEBUG"

        compilerArgs "-fconserve-space"
        linkerArgs "--export-dynamic"
    }
    binaries.withType(SharedLibraryBinary) {
        define "DLL_EXPORT"
    }

Each binary is associated with a particular C++ [tool chain](dsl/org.gradle.nativecode.base.ToolChain.html), allowing settings to be targeted based on this value.

    binaries.all {
        if (toolChain == toolChains.gcc) {
            compilerArgs "-O2", "-fno-access-control"
            linkerArgs "-S"
        }
        if (toolChain == toolChains.visualCpp) {
            compilerArgs "/Z7"
            linkerArgs "/INTEGRITYCHECK:NO"
        }
    }

More examples of how binary-specific settings can be provided are in the [user guide](userguide/cpp.html#N15789).

#### Can build C++ project with Cygwin/g++

The C++ plugins now support using g++ when running Gradle under Cygwin.

#### Improved incremental build for C++

The incremental build support offered by the C++ plugins has been improved in this release, making incremental build very accurate:

- Detects changes to compiler and linker settings, in addition to changes in source and header files.
- No longer recompiles source files when linker settings change.
- Detects changes to dependencies of a binary and recompiles or relinks as appropriate.
- Detects changes to the toolchain used to build a binary and recompiles and relinks.
- Removes stale object files when source files are removed or renamed.
- Removes stale output files when compiler and linker settings change. For example, removes stale debug files when debug is disabled.

### Specify default JVM arguments for the Application plugin (i)

Thanks to a contribution by Olaf Klischat, the application plugin now has support to specify the default JVM arguments to include in the generated
launcher scripts.

### Customise publication identity with new publishing plugins (i)

In Gradle 1.7 the new publishing plugins got a lot more powerful with the ability to directly specify the complete coordinates (or GAV) that will be used to publish.

For a `MavenPublication` you can specify the `groupId`, `artifactId` and `version` used for publishing. You can also set the `packaging` value on the `MavenPom`.

    publications {
        mavenPub(MavenPublication) {
            from components.java

            groupId "my.group.id"
            artifactId "my-publication"
            version "3.1"
            pom.packaging "pom"
        }
    }

For an `IvyPublication` you can set the `organisation`, `module` and `revision`. You can also set the `status` value on the `IvyModuleDescriptor`.

    publications {
        ivyPub(IvyPublication) {
            from components.java

            organisation "my.org"
            module "my-module"
            revision "3"
            descriptor.status "milestone"
        }
    }

This ability is particularly useful when publishing with a different `module` or `artifactId`, since these values default to the `project.name`
which cannot be modified from within the Gradle build script itself.

### Publish multiple modules from a single Gradle project (i)

Building on the ability to tweak the identity of a publication, the publishing plugins now allow you to
publish multiple modules from a single Gradle project. While this was quite tricky to achieve in the past, the `ivy-publish` and `maven-publish`
plugins now make it easy.

    project.group "org.cool.library"

    publications {
        implJar(MavenPublication) {
            artifactId "cool-library"
            version "3.1"

            artifact jar
        }
        apiJar(MavenPublication) {
            artifactId "cool-library-api"
            version "3"

            artifact apiJar
        }
    }

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

<!--
### Example deprecation
-->

## Potential breaking changes

### Caching dependency metadata in memory

Local-repo dependencies and expired snapshots are not loaded from the repository with each resolve.
During a single build, a resolved dependency is not loaded again from the repository.
For more details, please refer to the section about the in-memory dependency metadata cache.

### Incubating JaCoCo plugin changes

- `JacocoTaskExtension.destPath` renamed to `destinationFile`
- `JacocoTaskExtension.classDumpPath` renamed to `classDumpFile`
- `JacocoMerge.destFile` renamed to `destinationFile`

### Incubating BuildSetup plugin changes

- `ConvertMaven2Gradle`, `GenerateBuildScript` and `GenerateSettingsScript` have been removed. The according logic is now part of the `buildSetup` task
which has now the type`SetupBuild` task.
- The plugin creates different set of tasks, with different types and names depending on the build-setup type
- The `setupWrapper` task is now called `wrapper`.

### Changed task name in incubating ivy-publish plugin

- For consistency with the maven-publish plugin, the task for generating the ivy.xml file for an IvyPublication has changed.
  This task is now named `generateDescriptorFileFor${publication.name}Publication`.

### Default 'status' value of IvyPublication is 'integration' and no longer defaults to 'project.status' (i)

- In order to continue decoupling the Gradle project model from the Ivy publication model, the 'project.status' value is no longer used
  when publishing an IvyPublication with the `ivy-publish` plugin.
- If no status value is set on the `IvyModuleDescriptor` of an `IvyPublication`, then the default ivy status ('integration') will be used.
  Previously, 'release' was used, being the default value for 'project.status'.

### Major changes to C++ support

The incubating C++ support in Gradle is undergoing a major update. Many existing plugins, tasks, API classes and the DSL have been being given an overhaul.
It's likely that all but the simplest existing C++ builds will need to be updated to accommodate these changes.

If you want your existing C++ build to continue working with Gradle, you have 2 options.
- Remain on Gradle 1.6 for the next few releases until the C++ support stabilises, and then perform a single migration.
- Keep your build updated for the latest changes, being aware that further changes will be required for subsequent releases.

### `ConfigureableReport` renamed to `ConfigurableReport`

The (incubating) class `org.gradle.api.reporting.ConfigureableReport` was renamed to `org.gradle.api.reporting.ConfigurableReport` as the original name was misspelled.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Marcin Erdmann](https://github.com/erdi) - Added finalizer tasks.
* [Dan Stine](https://github.com/dstine)
    - Added `maxPriorityViolations` setting to the CodeNarc plugin (GRADLE-1742).
    - Correction in User Guide.
* [Olaf Klischat](https://github.com/multi-io) - Added support for specifying the default JVM arguments for the Application plugin (GRADLE-1456).
* [Kyle Mahan](https://github.com/kylewm) - Introduce duplicateStrategy property to archive and copy operations (GRADLE-2171).
* [Robert Kühne](https://github.com/sponiro) - Spelling correction in User Guide.
* [Björn Kautler](https://github.com/Vampire) - Correction to Build Dashboard sample.
* [Seth Goings](https://github.com/sgoings) - Correction in User Guide.
* [Scott Bennett-McLeish](https://github.com/sbennettmcleish) - Correction in User Guide.

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
