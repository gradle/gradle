/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.execution

import org.gradle.api.internal.GradleInternal
import spock.lang.Specification

class DefaultBuildExecuterTest extends Specification {
    final GradleInternal gradleInternal = Mock()

    def "execute method calls execute method on first execution action"() {
        BuildExecutionAction action1 = Mock()
        BuildExecutionAction action2 = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([action1, action2])

        when:
        buildExecution.execute(gradleInternal)

        then:
        1 * action1.execute(!null)
        0 * _._
    }

    def "calls next action in chain when execution action calls proceed"() {
        BuildExecutionAction action1 = Mock()
        BuildExecutionAction action2 = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([action1, action2])

        when:
        buildExecution.execute(gradleInternal)

        then:
        1 * action1.execute(!null) >> { it[0].proceed() }

        and:
        1 * action2.execute(!null)
    }

    def "does nothing when last execution action calls proceed"() {
        BuildExecutionAction action1 = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([action1])

        when:
        buildExecution.execute(gradleInternal)

        then:
        1 * action1.execute(!null) >> { it[0].proceed() }
        0 * _._
    }

    def "makes Gradle instance available to actions"() {
        BuildExecutionAction executionAction = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([executionAction])

        when:
        buildExecution.execute(gradleInternal)

        then:
        1 * executionAction.execute(!null) >> {
            assert it[0].gradle ==gradleInternal
        }
    }
}
