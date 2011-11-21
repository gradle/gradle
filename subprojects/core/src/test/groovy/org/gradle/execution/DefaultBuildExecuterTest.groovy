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

    def "select method calls configure method on first configuration action"() {
        BuildConfigurationAction action1 = Mock()
        BuildConfigurationAction action2 = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([action1, action2], [])

        when:
        buildExecution.select(gradleInternal)

        then:
        1 * action1.configure(!null)
        0 * _._
    }

    def "calls next action in chain when configuration action calls proceed"() {
        BuildConfigurationAction action1 = Mock()
        BuildConfigurationAction action2 = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([action1, action2], [])

        when:
        buildExecution.select(gradleInternal)

        then:
        1 * action1.configure(!null) >> { it[0].proceed() }

        and:
        1 * action2.configure(!null)
    }

    def "does nothing when last configuration action calls proceed"() {
        BuildConfigurationAction action1 = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([action1], [])

        when:
        buildExecution.select(gradleInternal)

        then:
        1 * action1.configure(!null) >> { it[0].proceed() }
        0 * _._
    }

    def "execute method calls execute method on first execution action"() {
        BuildExecutionAction action1 = Mock()
        BuildExecutionAction action2 = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([], [action1, action2])
        buildExecution.select(gradleInternal)

        when:
        buildExecution.execute()

        then:
        1 * action1.execute(!null)
        0 * _._
    }

    def "calls next action in chain when execution action calls proceed"() {
        BuildExecutionAction action1 = Mock()
        BuildExecutionAction action2 = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([], [action1, action2])
        buildExecution.select(gradleInternal)

        when:
        buildExecution.execute()

        then:
        1 * action1.execute(!null) >> { it[0].proceed() }

        and:
        1 * action2.execute(!null)
    }

    def "does nothing when last execution action calls proceed"() {
        BuildExecutionAction action1 = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([], [action1])
        buildExecution.select(gradleInternal)

        when:
        buildExecution.execute()

        then:
        1 * action1.execute(!null) >> { it[0].proceed() }
        0 * _._
    }

    def "makes Gradle instance available to actions"() {
        BuildConfigurationAction configurationAction = Mock()
        BuildExecutionAction executionAction = Mock()

        given:
        def buildExecution = new DefaultBuildExecuter([configurationAction], [executionAction])

        when:
        buildExecution.select(gradleInternal)
        buildExecution.execute()

        then:
        1 * configurationAction.configure(!null) >> {
            assert it[0].gradle ==gradleInternal
        }
        1 * executionAction.execute(!null) >> {
            assert it[0].gradle ==gradleInternal
        }
    }
}
