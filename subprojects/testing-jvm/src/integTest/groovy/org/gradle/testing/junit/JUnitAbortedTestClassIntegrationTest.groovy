/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.junit.Rule

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE

@TargetCoverage({ JUNIT_VINTAGE })
class JUnitAbortedTestClassIntegrationTest extends JUnitMultiVersionIntegrationSpec {

    @Rule TestResources resources = new TestResources(temporaryFolder)

    // Note: there're some behavior changes in 4.13:
    // https://github.com/junit-team/junit4/issues/1066
    // So this test was adjusted accordingly.
    def supportsAssumptionsInRules() {
        given:
        executer.noExtraLogging()
        buildFile << """
dependencies {
    ${getDependencyBlockContents()}
}
"""

        when:
        run('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SkippingRuleTests')
        result.testClass('org.gradle.SkippingRuleTests')
            .assertTestCount(3, 0, 0)
            .assertTestsExecuted('a', 'c')
            .assertTestsSkipped('b')
    }

}
