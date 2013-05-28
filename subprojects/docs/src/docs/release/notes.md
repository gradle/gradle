## New and noteworthy

Here are the new features introduced in this Gradle release.

### Faster Gradle builds

Gradle 1.7 is the fastest version of Gradle yet. Here are the highlights:

- Dependency resolution is now faster. This affects many aspects of a build. For example, incremental build up-to-date checks usually require dependencies
  to be resolved. As does importing your build into an IDE. Or using the dependency reports.
- Test execution is now faster. In some cases, up to 50% faster for those tests that generate a lot of logging output.
- Build script compilation is much faster. This affects, for example, first time users of a build, build authors, and those upgrading a build to a new Gradle version.
  In Gradle 1.6 there was a serious regression in build script compilation time. This has been fixed in Gradle 1.7, with an added bonus. Script compilation is now
  75% faster than Gradle 1.6 and 50% faster than Gradle 1.0.

As always, the performance improvements that you actually see for your build depends on many factors.

#### Faster dependency resolution due to in-memory caching of dependency metadata.

With this change, the dependency resolution is much faster. Typically, the larger the project is the more configurations and dependencies are being resolved during the build.
By caching the dependency metadata in memory we avoid hitting the repository and parsing the descriptor when the same dependency is requested in a different resolution.
An incremental build for a large project should be tangibly faster with Gradle 1.7.
A full build may be much faster, too. The level of performance improvement depends on the build.
If a large portion of the build time is taken by slow integration tests, the performance improvements are smaller.
Nevertheless, some of the large builds we used for benchmarking show up to 30% speed increase.

Caching the dependency metadata in-memory is very important for local repositories (e.g. local filesystem like mavenLocal())
and for resolution of snapshots / dynamic versions.
Prior to Gradle 1.7, every time a local dependency was resolved, Gradle would load the dependency metadata directly from the local repository.
This behavior also applies to resolution from remote repositories, but only for expired changing modules (snapshots) or expired dynamic versions (e.g. '1.2+').
With the in-memory caching of dependency metadata, this behavior now *changes*.
During a single build, a resolved dependency will not be reloaded again from the repository.
This may be a breaking change for exotic builds that depend the on the fact that certain dependencies are reloaded from the repository during each resolution.
Bear in mind that the vast majority of builds would much better enjoy faster dependency resolution offered by the in-memory dependency metadata cache.
If your project require refreshability of snapshots or local dependencies during the build please let us know so that we can better fully your scenario and model it correctly.
You can also turn off the in-memory dependency metadata cache via a system property:

    //gradle.properties
    systemProp.org.gradle.resolution.memorycache=false

To avoid increased heap consumption, the in-memory dependency metadata cache may clear the cached data if the system is running out of heap space.

#### Faster build script compilation

TODO

#### ClassLoader caching

TODO

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

### Generate Gradle wrapper files without touching your build script (i)

In Gradle 1.7 all files necessary to run your build with the Gradle Wrapper can be generated without explicitly declaring a task of type `Wrapper` in your build scripts.
By just running

    gradle wrapper

The Gradle Wrapper files are generated pointing to the gradle version used to generate the wrapper files. To customize the wrapper task you can easily modify the task in your build script:

    wrapper{
        gradleVersion = '1.6'
    }

If you already defined a task of type `Wrapper`, the explicit declared in your build script, this task will be used when running `gradle wrapper`, otherwise the implicit default task will be used.

### Improved build-setup plugin (i)

The `build-setup` plugin now supports declaring a project type when setting up a build. With version 1.7 Gradle, now supports `java-library` as a setup project type
which generates a simple build file with the java plugin applied, a sample junit test class and a sample production code class if no sources already exist.
To declare the project type you have to specify a `--type` command line argument:

    gradle setupBuild --type java-library


### Added option to deal with duplicate files in archives and copy operations

When copying files with duplicate relative paths in the target archive (or directory), you can now specify the strategy for dealing with these duplicate files by
using `FileCopyDetails`.

    task zip(type: Zip) {
        from 'dir1'
        from 'dir2'
        archiveName = 'MyZip.zip'
        eachFile { it.duplicatesStrategy = 'exclude' }
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

### Major changes to C++ support

The incubating C++ support in Gradle is undergoing a major update, in order to add support for Static Libraries, variants and other good features. As such,
the tasks, API and DSL are being given an overhaul. While some features may remain the same, it's likely that any existing C++ builds will need to be updated
for the changes. You can read about the plan for proposed changes at https://github.com/gradle/gradle/blob/master/design-docs/continuous-delivery-for-c-plus-plus.md.

### `ConfigureableReport` renamed to `ConfigurableReport`

The (incubating) class `org.gradle.api.reporting.ConfigureableReport` was renamed to `org.gradle.api.reporting.ConfigurableReport` as the original name was misspelt. 

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Dan Stine](https://github.com/dstine) - Added maxPriorityViolations settings to the CodeNarc plugin (GRADLE-1742).
* [Olaf Klischat](https://github.com/multi-io) - Added defaultJvmOpts property Application plugin (GRADLE-1456).
* [Kyle Mahan](https://github.com/kylewm) - Introduce duplicateStrategy property to archive and copy operations (GRADLE-2171).

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
