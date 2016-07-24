package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult

class JUnitJdkNavigationTest extends AbstractIntegrationSpec {


    def setup() {
        executer.noExtraLogging();
    }

    def doesntTraverseThroughJdkClasses() {
        given:
        buildFile

        when:
        executer.withTasks('test').run();

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory);
        result.assertTestClassesExecuted('org.gradle.Test');
        result.testClass('org.gradle.Test').assertTestPassed('shouldPass');
    }

}
