

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

This change improves build script compilation by adding some caching in critical points in the classloader hierarchy.
This affects, for example, first time users of a build, build authors, and those upgrading a build to a new Gradle version.

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
Previously it was only visible on the page for the test _class_.

This is also necessary for effective use of the Jenkins [JUnit Attachments Plugin](https://wiki.jenkins-ci.org/display/JENKINS/JUnit+Attachments+Plugin) that allows
associating test attachments (e.g. Selenium screen shots) with test execution in the Jenkins UI.

### Generate Gradle wrapper files without touching your build script (i)

It is now possible to [Gradle Wrapper](userguide/gradle_wrapper.html) enable a project without having to create a `Wrapper` task in your build.
That is, you do not need to edit a build script to enable the Wrapper.

To Wrapper enable any project with Gradle 1.7, simply run:

    gradle wrapper

The Wrapper files are installed and configured to use the Gradle version that was used when running the task. 

To customize the wrapper task you can modify the task in your build script:

    wrapper {
        gradleVersion '1.6'
    }

If there is already an explicitly defined task of type `Wrapper` in your build script, this task will be used when running `gradle wrapper`; 
otherwise the new implicit default task will be used.

### Generate a Java library Gradle project skeleton (i)

The `build-setup` plugin now supports declaring a project type when setting up a build, 
laying the foundations for creating different types of project starting points conveniently. 
Gradle 1.7 comes with the `java-library` type, which generates:

* A simple build file with the java plugin applied
* A sample production class (and directories)
* A sample JUnit test class (and directories)
* Gradle Wrapper files

To create a new Java library project, you can execute the following in a directory (no `build.gradle` needed):

    gradle setupBuild --type java-library
    
See the chapter on the [Build Setup plugin](userguide/build_setup_plugin.html) for more info, including future directions.

### Pattern based file copy configuration (i)

Gradle 1.7 adds the ability to specify fine grained configuration of _how_ certain files should be copied by targeting configuration with “Ant Patterns”.

Gradle has a unified API for file copying operations, by way of [`CopySpec`](javadoc/org/gradle/api/file/CopySpec.html), which includes creating archives (e.g. zips).
This new feature makes this API more powerful.

    task copyFiles(type: Copy) {
        from "src/files"
        into "$buildDir/copied-files"
        
        // Replace the version number variable in only the text files
        filesMatching("**/*.txt") {
            expand version: "1.0"
        }
    }

The [`filesMatching()`](javadoc/org/gradle/api/file/CopySpec.html#filesMatching%28java.lang.String%2C%20org.gradle.api.Action%29) method can be 
called with a closure and configures an instance of [`FileCopyDetails`](javadoc/org/gradle/api/file/FileCopyDetails.html).
There is also an inverse variation, 
[`filesNotMatching()`](javadoc/org/gradle/api/file/CopySpec.html#filesNotMatching%28java.lang.String%2C%20org.gradle.api.Action%29), that allows
configuration to be specified for all files that do not match the given pattern. 

### Duplicate file handling for copy and archive operations (i)

When copying files or creating archives, it is possible to do so in such a way that effectively creates duplicates at the destination.
It is now possible to specify a strategy to use when this occurs to avoid duplicates.

    task zip(type: Zip) {
        from 'dir1'
        from 'dir2'
        duplicatesStrategy 'exclude'
    }

There are two possible strategies: `include` and `exclude`. 

The `include` strategy is equivalent to Gradle's existing behaviour. 
For copy operations, the last file copied to the duplicated destination is used. However, a warning is now issued when this occurs. 
For archive creation (e.g. zip, jar), duplicate entries will be created in the archive.

The `exclude` strategy effectively ignores duplicates. 
The first thing copied to a location is used and all subsequent attempts to copy something to the same location are ignored.
This means that for copy operations, the first file copied into place is always used. 
For archive operations, the same is true and duplicate entries will _not_ be created.

It is also possible to specify the duplicates strategy on a very fine grained level using the flexibility of the Gradle API for 
specifying copy operations (incl. archive operations).

    task zip(type: Zip) {
        duplicatesStrategy 'exclude' // default strategy
        from ('dir1') {
            filesMatching("**/*.xml") {
                duplicatesStrategy 'include'
            }
        } 
        from ('dir2') {
            duplicatesStrategy 'include'
        }
    }

### Major improvements to C++ project support (i)

Gradle has had basic support for C++ projects for some time. This is now expanding with the goal of positioning Gradle as the best build
system available for native code projects. 

This includes:

- Creating and linking to static libraries
- Building with different C++ toolchains (Visual C++, GCC, etc)
- Building multiple variants of a single binary with different target architectures, build types (debug vs release), operating systems etc.
- Variant-aware dependency resolution
- Much more … (read the [design spec](https://github.com/gradle/gradle/blob/master/design-docs/continuous-delivery-for-c-plus-plus.md) for more info) 

Some of these features are included in Gradle 1.7 (see below), while others can be expected in the upcoming releases.

#### Improved native component model

A key part of improving C++ support is an [improved component model](userguide/cpp.html#N15643) which supports building multiple binary outputs for
a single defined [native component](dsl/org.gradle.nativecode.base.NativeComponent.html).
Using this model Gradle can now produce both a static and shared version of any [library component](dsl/org.gradle.nativecode.base.Library.html).

#### Static libraries from C++ sources

For any library declared in your C++ build, it is now possible to either compile and link the object files into a shared library,
or compile and archive the object files into a static library (or both). For any library 'lib' added to your project,
Gradle will create a 'libSharedLibrary' task to link the shared library, as well as a 'libStaticLibrary' task to create the static library.

Please refer to the [User Guide chapter](userguide/cpp.html) and the included C++ samples for more details.

#### Per binary configuration

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

#### Cygwin support

The C++ plugins now support using g++ when running Gradle under Cygwin.

#### Improved incremental build

The incremental build support offered by the C++ plugins has been improved in this release, making incremental build very accurate:

- Detects changes to compiler and linker settings, in addition to changes in source and header files.
- No longer recompiles source files when linker settings change.
- Detects changes to dependencies of a binary and recompiles or relinks as appropriate.
- Detects changes to the toolchain used to build a binary and recompiles and relinks.
- Removes stale object files when source files are removed or renamed.
- Removes stale output files when compiler and linker settings change. For example, removes stale debug files when debug is disabled.

### Specify default JVM arguments for the Application plugin (i)

Thanks to a contribution by [Olaf Klischat](https://github.com/multi-io), the [Application Plugin](userguide/application_plugin.html) now provides the ability to specify 
default JVM arguments to include in the generated launcher scripts.

    apply plugin: "application"
    
    applicationDefaultJvmArgs = ["-Dfile.encoding=UTF=8"]

### Customise publication identity with new publishing plugins (i)

It is now possible to explicitly set the identity of a publication with the new publishing plugins. 
Previously the identity was assumed to be the same of the project.

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

### Improved OSGi support through Bnd library update

The [OSGi plugin](userguide/osgi_plugin.html) uses the [Bnd](http://www.aqute.biz/Bnd/Bnd) tool to generate bundle manifests.
The version used has changed from `1.50.0` to `2.1.0` with this release.

The most significant improvement obtained through this upgrade is the improved accuracy of generated manifests for Java code that uses the “invokedynamic” byte code instruction.

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

### dependencies are checked once per build, now that dependency metadata is cached in memory

Local-repo dependencies and expired snapshots are no longer loaded from the repository with each resolve.
During a single build, a resolved dependency is loaded from a given repository once (regardless of how many subprojects or configurations the build has).
For more details, please refer to the section of 'Faster Gradle builds' that describes the in-memory dependency metadata cache.

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

### Test task is skipped when there are no tests

GRADLE-2702 is fixed and now the test task behavior is more correct: it is skipped when there are no inputs (no tests).
Previously, in the no-tests scenario, the test task was still executed, testCompile and testRuntime configurations were resolved and an empty html report was generated.
This change affects the end users only in a good way (faster builds!) but nevertheless it needs to be mentioned as a potentially breaking change
(no more empty reports, task skipped when no inputs).

### Bnd library used by OSGi plugin updated

The [OSGi plugin](userguide/osgi_plugin.html) uses the [Bnd](http://www.aqute.biz/Bnd/Bnd) tool to generate bundle manifests.
The version used has changed from `1.50.0` to `2.1.0` with the 1.7 release. 
While this should be completely backwards compatible, it is a significant upgrade.
 
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
* [Wujek Srujek](https://github.com/wujek-srujek) - Handling of -g command line option for wrapper install location (GRADLE-2802).
* [Guillaume Laforge](https://github.com/glaforge) - Update of Bnd library used by OSGi plugin (GRADLE-2802).

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
