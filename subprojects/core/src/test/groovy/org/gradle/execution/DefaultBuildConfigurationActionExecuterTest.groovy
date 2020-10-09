/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.api.internal.project.ProjectStateRegistry
import spock.lang.Specification

class DefaultBuildConfigurationActionExecuterTest extends Specification {
    final GradleInternal gradleInternal = Mock()
    final ProjectStateRegistry projectStateRegistry = Stub()

    def setup() {
        _ * projectStateRegistry.withMutableStateOfAllProjects(_) >> { Runnable r -> r.run() }
    }

    def "select method calls configure method on first configuration action"() {
        BuildConfigurationAction configurationAction = Mock()
        BuildConfigurationAction taskSelectionAction = Mock()

        given:
        def buildExecution = new DefaultBuildConfigurationActionExecuter([configurationAction], [taskSelectionAction], projectStateRegistry)

        when:
        buildExecution.select(gradleInternal)

        then:
        1 * configurationAction.configure(!null)
        0 * _._
    }

    def "calls next action in chain when configuration action calls proceed"() {
        BuildConfigurationAction configurationAction = Mock()
        BuildConfigurationAction taskSelectionAction = Mock()

        given:
        def buildExecution = new DefaultBuildConfigurationActionExecuter([configurationAction], [taskSelectionAction], projectStateRegistry)

        when:
        buildExecution.select(gradleInternal)

        then:
        1 * configurationAction.configure(!null) >> { it[0].proceed() }

        and:
        1 * taskSelectionAction.configure(!null)
    }

    def "does nothing when last configuration action calls proceed"() {
        BuildConfigurationAction action1 = Mock()

        given:
        def buildExecution = new DefaultBuildConfigurationActionExecuter([action1],[], projectStateRegistry)

        when:
        buildExecution.select(gradleInternal)

        then:
        1 * action1.configure(!null) >> { it[0].proceed() }
        0 * _._
    }

    def "makes Gradle instance available to actions"() {
        BuildConfigurationAction configurationAction = Mock()

        given:
        def buildExecution = new DefaultBuildConfigurationActionExecuter([configurationAction],[], projectStateRegistry)

        when:
        buildExecution.select(gradleInternal)

        then:
        1 * configurationAction.configure(!null) >> {
            assert it[0].gradle ==gradleInternal
        }
    }

    def "can overwrite default task selectors"() {
        setup:
        BuildConfigurationAction configAction1 = Mock()
        BuildConfigurationAction configAction2 = Mock()
        BuildConfigurationAction givenTaskSelector2 = Mock()
        BuildConfigurationAction givenTaskSelector1 = Mock()

        BuildConfigurationAction newTaskSelector = Mock()


        def buildExecution = new DefaultBuildConfigurationActionExecuter([configAction1, configAction2],[givenTaskSelector1, givenTaskSelector2], projectStateRegistry)

        when:
        buildExecution.setTaskSelectors([newTaskSelector])
        buildExecution.select(gradleInternal)

        then:

        0 * givenTaskSelector1.configure(!null)
        0 * givenTaskSelector2.configure(!null)
        1 * configAction1.configure(!null) >> {it[0].proceed()}
        1 * configAction2.configure(!null) >> {it[0].proceed()}

        1 * newTaskSelector.configure(!null) >> {
            assert it[0].gradle ==gradleInternal

        }
    }
}
