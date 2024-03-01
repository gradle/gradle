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
package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

class SamplesCustomExternalTaskIntegrationTest extends AbstractSampleIntegrationTest {
    @Rule
    public final Sample sample = new Sample(temporaryFolder)

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "Requires a Gradle distribution on the test-under-test classpath, but gradleApi() does not offer the full distribution")
    @UsesSample("base/customExternalTask")
    def "can test task implementation with #dsl dsl"() {
        when:
        TestFile dslDir = sample.dir.file("$dsl/task")
        executer.inDirectory(dslDir).withTasks('check').run()

        then:
        def result = new DefaultTestExecutionResult(dslDir)
        result.assertTestClassesExecuted('org.gradle.GreetingTaskTest')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("base/customExternalTask")
    def "can publish and use task implementations for #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        TestFile producerDir = dslDir.file('task')
        executer.inDirectory(producerDir).withTasks('publish').run()
        executer.beforeExecute {
            inDirectory(dslDir.file('consumer'))
        }

        when:
        succeeds('greeting')

        then:
        outputContains('howdy!')

        where:
        dsl << ['groovy', 'kotlin']
    }
}
