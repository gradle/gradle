package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import spock.lang.Issue

@Issue("GRADLE-1682")
class JUnitJdkNavigationTest extends AbstractIntegrationSpec {


    def setup() {
        executer.noExtraLogging()
    }

    def "should not navigate through JDK classes"() {
        given:
        buildFile

        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.Test')
        result.testClass('org.gradle.Test').assertTestPassed('shouldPass')
    }

}
