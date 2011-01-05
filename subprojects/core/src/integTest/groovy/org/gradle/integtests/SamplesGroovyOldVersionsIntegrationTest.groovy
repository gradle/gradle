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

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test

class SamplesGroovyOldVersionsIntegrationTest {

    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    @Test
    public void groovy156() {
        TestFile groovyProjectDir = dist.samplesDir.file('groovy/groovy-1.5.6')
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'build').run()

        // Check tests have run
        JUnitTestExecutionResult result = new JUnitTestExecutionResult(groovyProjectDir)
        result.assertTestClassesExecuted('org.gradle.PersonTest')

        // Check jar exists
        groovyProjectDir.file("build/libs/groovy-1.5.6.jar").assertIsFile()
    }

    @Test
    public void groovy167() {
        TestFile groovyProjectDir = dist.samplesDir.file('groovy/groovy-1.6.7')
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'build').run()

        // Check tests have run
        JUnitTestExecutionResult result = new JUnitTestExecutionResult(groovyProjectDir)
        result.assertTestClassesExecuted('org.gradle.PersonTest')

        // Check jar exists
        groovyProjectDir.file("build/libs/groovy-1.6.7.jar").assertIsFile()
    }
}