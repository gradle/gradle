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

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.GradleDistribution
import org.junit.Test
import static org.hamcrest.Matchers.*
import org.junit.Assert
import org.junit.Ignore

class IncrementalTestIntegrationTest {
    @Rule public final GradleDistribution distribution = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources resources = new TestResources()

    @Test
    public void doesNotRunStaleTests() {
        def failure = executer.withTasks('test').runWithFailure()
        failure.assertThatCause(startsWith('There were failing tests.'))

        distribution.testFile('src/test/java/Broken.java').assertIsFile().delete()

        executer.withTasks('test').run()
    }

    @Test
    public void executesTestsWhenSourceChanges() {
        executer.withTasks('test').run()

        // Change a production class
        distribution.testFile('src/main/java/MainClass.java').assertIsFile().copyFrom(distribution.testFile('NewMainClass.java'))

        executer.withTasks('test').run().assertTasksSkipped(':processResources', ':processTestResources')

        executer.withTasks('test').run().assertTasksSkipped(':compileJava', ':processResources', ':classes', ':compileTestJava', ':processTestResources', ':testClasses', ':test')
        
        // Change a test class
        distribution.testFile('src/test/java/Ok.java').assertIsFile().copyFrom(distribution.testFile('NewOk.java'))

        executer.withTasks('test').run().assertTasksSkipped(':compileJava', ':processResources', ':classes', ':processTestResources')

        executer.withTasks('test').run().assertTasksSkipped(':compileJava', ':processResources', ':classes', ':compileTestJava', ':processTestResources', ':testClasses', ':test')
    }

    @Test @Ignore
    public void executesTestsWhenSelectedTestsChange() {
        Assert.fail()
    }

    @Test @Ignore
    public void executesTestsWhenPropertiesChange() {
        Assert.fail()
    }

    @Test @Ignore
    public void executesTestsWhenTestFrameworkChanges() {
        Assert.fail()
    }
}
