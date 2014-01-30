
TestNG supports [parameterising test executions](http://testng.org/doc/documentation-main.html#parameters). 
This allows a particular test method to be executed multiple times with different inputs.
Our current reporting infrastructure is oblivious to these parameters and in no way differentiates the iterations of the test method.
This means that if the test method fails for a particular iteration, there is no way based on the reports that we produce to identify which iteration failed.
This includes the output XML file that is read by CI servers.

# Use cases

TestNG user using test parameterisation is able to differentiate iterations of the same test method in Gradle's HTML test report and in their CI servers test summary (i.e. JUnit XML output file).

# Implementation plan

## Story 1: All parameterised TestNG methods include parameter values for each of their iterations in the HTML and XML reports 

Given the following test case:

    package org;
    
    import org.testng.annotations.*;
    import static org.testng.Assert.*;

    public class ParameterizedTest {

        @Test(dataProvider = "1")
    	public void parameterized1(String var1, String var2) {
            System.out.println("var1 is: " + var1);
            System.out.println("var2 is: " + var2);
           	assertEquals(var1, "1");
    	}

    	@DataProvider(name = "1")
    	public Object[][] parameterized1Provider() {
    		return new Object[][] {
    		   {"1", "2"},
    		   {"3", "4"}
    	    };
    	}

    }

The output XML file will look something like:

    <?xml version="1.1" encoding="UTF-8"?>
    <testsuite name="org.ParameterizedTest" tests="2" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="hasdrubal.local" time="1.366305747839E9">
      <properties/>
      <testcase name="parameterized1" classname="org.ParameterizedTest" time="0.0"/>
      <testcase name="parameterized1" classname="org.ParameterizedTest" time="0.0"/>
      <system-out><![CDATA[var1 is: 1
    var2 is: 2
    var1 is: 3
    var2 is: 4
    ]]></system-out>
      <system-err><![CDATA[]]></system-err>
    </testsuite>
    
To include the parameter info, it should look like:

    <?xml version="1.1" encoding="UTF-8"?>
    <testsuite name="org.ParameterizedTest" tests="2" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="hasdrubal.local" time="1.366305747839E9">
      <properties/>
      <testcase name="parameterized1(1, 2)" classname="org.ParameterizedTest" time="0.0"/>
      <testcase name="parameterized1(3, 4)" classname="org.ParameterizedTest" time="0.0"/>
      <system-out><![CDATA[var1 is: 1
    var2 is: 2
    var1 is: 3
    var2 is: 4
    ]]></system-out>
      <system-err><![CDATA[]]></system-err>
    </testsuite>
    
The general format will be: `«methodName»(«param1.toString()[, «paramN.toString()…])`

This format is the most logical as it resembles a method invocation.

If the `toString()` representation of the parameter is very long (e.g. a long string) it may obscure the test case name. To remedy this, `toString()` values will be abbreviated to 10 characters. 10 characters should be enough to differentiate an input. In the case where it is not, the author can use a sentinel counter value or similar.

> Another idea would be to include a counter value in the method name: `«methodName»[«iteration number»](«param1.toString()[, «paramN.toString()…])`
    
### User visible changes

None. Internal change.

### Sad day cases

* Some tooling may be requiring a 1-1 mapping between the name of the Java method and the entry in the JUnit XML file (none known at this time though)
* A TestNG data provider method may error for an iteration

### Test coverage

Enhancing the existing `TestNGXmlResultAndHtmlReportIntegrationTest`

#### Cases

* A parameter's `toString()` impl throws an exception

A token message can be used: `parameter threw exception: $e.toString()`

* A parameter is null

The string `null` will be displayed as the value

* A parameter has a very large `toString()` value
* A parameter's `toString()` value contains sensitive XML characters (e.g. [<"]) - (should be correctly encoded in XML file)
* A parameter's `toString()` value contains unicode characters

### Implementation approach

The translation between the TestNG model and our internal model happens [here](https://github.com/gradle/gradle/blob/master/subprojects/plugins/src/main/groovy/org/gradle/api/internal/tasks/testing/testng/TestNGTestResultProcessorAdapter.java#L69).

This method receives an [ITestResult](http://testng.org/javadoc/org/testng/ITestResult.html), which has access to the parameters for the method.
If there are parameters, they can be used to build the string representation described above and passed to the `DefaultTestMethodDescriptor` constructor.

This will effect both the XML and HTML reports.

## Story 2: A TestNG user can disable parameters being represented in the reports Gradle generates

# Open issues

This stuff is never done. This section is to keep track of assumptions and things we haven't figured out yet.
