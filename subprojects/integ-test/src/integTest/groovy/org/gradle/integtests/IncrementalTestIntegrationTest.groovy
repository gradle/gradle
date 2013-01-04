/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.*

class IncrementalTestIntegrationTest extends AbstractIntegrationTest {

    @Rule public final TestResources resources = new TestResources()

    @Before
    public void before() {
        executer.allowExtraLogging = false
    }

    @Test
    public void doesNotRunStaleTests() {
        executer.withTasks('test').runWithFailure().assertTestsFailed()

        file('src/test/java/Broken.java').assertIsFile().delete()

        executer.withTasks('test').run()
    }

    @Test
    public void executesTestsWhenSourceChanges() {
        executer.withTasks('test').run()

        // Change a production class
        file('src/main/java/MainClass.java').assertIsFile().copyFrom(file('NewMainClass.java'))

        executer.withTasks('test').run().assertTasksNotSkipped(':compileJava', ':classes', ':compileTestJava', ':testClasses', ':test')
        executer.withTasks('test').run().assertTasksNotSkipped()
        
        // Change a test class
        file('src/test/java/Ok.java').assertIsFile().copyFrom(file('NewOk.java'))

        executer.withTasks('test').run().assertTasksNotSkipped(':compileTestJava', ':testClasses', ':test')
        executer.withTasks('test').run().assertTasksNotSkipped()
    }

    @Test
    public void executesTestsWhenSelectedTestsChange() {
        executer.withTasks('test').run()

        def result = new DefaultTestExecutionResult(testWorkDir)
        result.assertTestClassesExecuted('JUnitTest')

        // Include more tests
        file('build.gradle').append 'test.include "**/*Extra*"\n'

        executer.withTasks('test').run().assertTasksNotSkipped(':test')
        result.assertTestClassesExecuted('JUnitTest', 'JUnitExtra')

        executer.withTasks('test').run().assertTasksNotSkipped()

        // Use single test execution
        executer.withTasks('test').withArguments('-Dtest.single=Ok').run().assertTasksNotSkipped(':test')
        executer.withTasks('test').run().assertTasksNotSkipped(':test')
        executer.withTasks('test').run().assertTasksNotSkipped()

        // Switch test framework
        file('build.gradle').append 'test.useTestNG()\n'

        //TODO this exposes a possible problem: When changing the test framework stale xml result files from former test framework are still present.
        executer.withTasks('cleanTest', 'test').run().assertTasksNotSkipped(':cleanTest',':test')

        result = new DefaultTestExecutionResult(testWorkDir)
        result.assertTestClassesExecuted('TestNGTest')

        executer.withTasks('test').run().assertTasksNotSkipped()
    }

    @Test @Ignore
    public void executesTestsWhenPropertiesChange() {
        Assert.fail()
    }
}
