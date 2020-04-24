/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.integtests.fixtures.instantexecution.HasInstantExecutionProblemsSpec

abstract class AbstractInstantExecutionUndeclaredBuildInputsIntegrationTest extends AbstractInstantExecutionIntegrationTest {
    abstract void pluginDefinition()

    void additionalProblems(HasInstantExecutionProblemsSpec spec) {
    }

    def "reports undeclared use of system property prior to task execution from plugin"() {
        pluginDefinition()
        buildFile << """
            apply plugin: SneakyPlugin
        """
        def fixture = newInstantExecutionFixture()

        when:
        run("thing")

        then:
        outputContains("apply CI = null")
        outputContains("apply CI2 = null")
        outputContains("task CI = null")

        when:
        problems.withDoNotFailOnProblems()
        instantRun("thing")

        then:
        fixture.assertStateStored()
        // TODO - use fixture, need to be able to tweak the problem matching as build script class name is generated
        outputContains("- unknown property: read system property 'CI' from 'SneakyPlugin'")
        outputContains("- unknown property: read system property 'CI2' from '")
        outputContains("apply CI = null")
        outputContains("apply CI2 = null")
        outputContains("task CI = null")

        when:
        instantRun("thing", "-DCI=true")

        then:
        fixture.assertStateLoaded()
        problems.assertResultHasProblems(result)
        outputContains("task CI = true")
    }
}
