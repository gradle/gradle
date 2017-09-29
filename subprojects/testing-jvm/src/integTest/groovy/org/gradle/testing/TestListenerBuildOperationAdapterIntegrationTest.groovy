/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule


class TestListenerBuildOperationAdapterIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def executesTestsInCorrectEnvironment() {
        given:
        buildFile << """
        test {
            systemProperties.isJava9 = ${JavaVersion.current().isJava9Compatible()}
        }
        """.stripIndent()

        when:
        executer.withTasks('build').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.OkTest', 'org.gradle.OtherTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('ok')
        result.testClass('org.gradle.OtherTest').assertTestPassed('ok')
    }

}
