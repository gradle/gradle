## New and noteworthy

Here are the new features introduced in this Gradle release.

### Faster Gradle builds due to in-memory caching of dependency metadata.

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

### TestNG parameters included in test reports

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

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* Some Person - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
