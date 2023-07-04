/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.junit.Rule

class JUnit4TestFilteringSamplesIntegrationTest extends AbstractIntegrationSpec {

    @Rule Sample sample = new Sample(temporaryFolder, 'testing/filtering/groovy')

    def setup() {
        executer.withRepositoryMirrors()
    }

    def "uses test filter"() {
        when:
        inDirectory(sample.dir)
        run("test")

        then:
        def result = new DefaultTestExecutionResult(sample.dir)
        result.assertTestClassesExecuted("SomeIntegTest", "SomeOtherTest")
        result.testClass("SomeIntegTest").assertTestsExecuted("test1", "test2")
        result.testClass("SomeOtherTest").assertTestsExecuted("quickUiCheck")
    }
}
