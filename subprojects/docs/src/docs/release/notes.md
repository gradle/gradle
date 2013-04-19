## New and noteworthy

Here are the new features introduced in this Gradle release.

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

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* Some Person - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
