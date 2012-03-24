/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.testing.classloading

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.JUnitTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test

public class CustomSystemClassLoaderIntegrationTest extends AbstractIntegrationTest {
    @Rule public final TestResources resources = new TestResources()

    @Test
    public void canRunTestsWithCustomSystemClassLoader() {
        executer.withTasks('test').run();

        JUnitTestExecutionResult result = new JUnitTestExecutionResult(testDir)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('ok')
    }
}