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
import org.junit.Rule
import spock.lang.Unroll

class SamplesCustomPluginIntegrationTest extends AbstractSampleIntegrationTest {
    @Rule public final Sample sample = new Sample(temporaryFolder)

    @UsesSample("customPlugin/plugin")
    def "can test plugin and task implementation"() {
        when:
        executer.inDirectory(sample.dir).withTasks('check').run()

        then:
        def result = new DefaultTestExecutionResult(sample.dir)
        result.assertTestClassesExecuted('org.gradle.GreetingTaskTest', 'org.gradle.GreetingPluginTest')
    }

    @Unroll
    @UsesSample("customPlugin")
    def "can publish and use plugin and test implementations for #producerName producer"() {
        given:
        def producerDir = sample.dir.file(producerName)
        executer.inDirectory(producerDir).withTasks('publish').run()
        executer.beforeExecute {
            inDirectory(sample.dir.file('consumer'))
            withArgument("-PproducerName=$producerName")
        }

        when:
        succeeds('greeting')

        then:
        outputContains('howdy!')

        when:
        succeeds('hello')

        then:
        outputContains('hello from GreetingTask')

        where:
        producerName << ['plugin', 'javaGradlePlugin']
    }
}
