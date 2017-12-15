package org.gradle.testing.junit5

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class JUnitPlatformIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def setup() {
        executer.noExtraLogging()
    }

    def executesAllTestsInClasspathRoots() {
        when:
        executer.withTasks('build').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'junit')
        result.assertTestClassesExecuted('org.gradle.SimpleTest', 'org.gradle.OtherTest')
        result.testClass('org.gradle.SimpleTest').assertTestPassed('myTest1')
        result.testClass('org.gradle.SimpleTest').assertTestPassed('myTest2')
        result.testClass('org.gradle.OtherTest').assertTestPassed('otherTest1')
        result.testClass('org.gradle.OtherTest').assertTestPassed('otherTest2')
    }
}
