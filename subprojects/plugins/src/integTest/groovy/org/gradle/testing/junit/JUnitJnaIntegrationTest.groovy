/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule


class JUnitJnaIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    @Requires(TestPrecondition.WINDOWS)
    def canRunTestsUsingJna() {
        when:
        executer.withTasks('build').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('OkTest')
        result.testClass('OkTest').assertTestPassed('ok')
    }
}
