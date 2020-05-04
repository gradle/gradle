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

package org.gradle.instantexecution.inputs.undeclared

import org.gradle.instantexecution.AbstractInstantExecutionIntegrationTest
import spock.lang.Unroll

abstract class AbstractUndeclaredBuildInputsIntegrationTest extends AbstractInstantExecutionIntegrationTest {
    abstract void buildLogicApplication()

    List<String> getAdditionalProperties() {
        return []
    }

    @Unroll
    def "reports undeclared use of system property #property prior to task execution from plugin"() {
        buildLogicApplication()
        def fixture = newInstantExecutionFixture()

        when:
        run("thing", "-D${property}=true")

        then:
        outputContains("apply ${property} = true")
        outputContains("task ${property} = true")

        when:
        instantFails("thing", "-D${property}=true")

        then:
        // TODO - use problems fixture, need to be able to tweak the problem matching as build script class name is included in the message and this is generated
        failure.assertThatDescription(containsNormalizedString("- unknown location: read system property '${property}' from '"))

        when:
        problems.withDoNotFailOnProblems()
        instantRun("thing", "-D${property}=true")

        then:
        fixture.assertStateStored()
        // TODO - use problems fixture, need to be able to tweak the problem matching as build script class name is included in the message and this is generated
        outputContains("- unknown location: read system property '${property}' from '")
        outputContains("apply ${property} = true")
        outputContains("task ${property} = true")

        when:
        instantRun("thing", "-D${property}=false")

        then:
        fixture.assertStateLoaded() // undeclared properties are not considered build inputs, but probably should be
        problems.assertResultHasProblems(result)
        outputContains("task ${property} = false")

        where:
        property << ["GET_PROPERTY", "GET_PROPERTY_OR_DEFAULT"] + additionalProperties
    }
}
