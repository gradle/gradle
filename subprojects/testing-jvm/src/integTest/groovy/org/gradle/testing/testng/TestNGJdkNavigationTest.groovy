package org.gradle.testing.testng

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

@Issue("GRADLE-1682")
class TestNGJdkNavigationTest extends AbstractIntegrationTest {

    @Rule public TestResources resources = new TestResources(testDirectoryProvider)

    @Before
    public void before() {
        executer.noExtraLogging()
    }

    @Test
    void shouldNotNavigateIntoJdkClasses() {
        executer.withTasks('test').run()

        new DefaultTestExecutionResult(testDirectory).testClass('org.grade.Test').assertTestPassed('shouldPass')
    }

}
