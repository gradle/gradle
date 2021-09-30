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
import org.gradle.execution.plan.ExecutionPlan
import spock.lang.Specification

class DefaultBuildConfigurationActionExecuterTest extends Specification {
    final GradleInternal gradleInternal = Mock()
    final ExecutionPlan executionPlan = Mock()
    final ProjectStateRegistry projectStateRegistry = Stub()

    def setup() {
        _ * projectStateRegistry.withMutableStateOfAllProjects(_) >> { Runnable r -> r.run() }
    }

    def "select method calls configure method on first action"() {
        BuildConfigurationAction action1 = Mock()
        BuildConfigurationAction action2 = Mock()

        given:
        def buildExecution = new DefaultBuildConfigurationActionExecuter([action1, action2], projectStateRegistry)

        when:
        buildExecution.select(gradleInternal, executionPlan)

        then:
        1 * action1.configure(!null)
        0 * _._
    }

    def "calls next action in chain when action calls proceed"() {
        BuildConfigurationAction action1 = Mock()
        BuildConfigurationAction action2 = Mock()

        given:
        def buildExecution = new DefaultBuildConfigurationActionExecuter([action1, action2], projectStateRegistry)

        when:
        buildExecution.select(gradleInternal, executionPlan)

        then:
        1 * action1.configure(!null) >> { BuildExecutionContext context -> context.proceed() }

        and:
        1 * action2.configure(!null)
    }

    def "does nothing when last configuration action calls proceed"() {
        BuildConfigurationAction action1 = Mock()

        given:
        def buildExecution = new DefaultBuildConfigurationActionExecuter([action1], projectStateRegistry)

        when:
        buildExecution.select(gradleInternal, executionPlan)

        then:
        1 * action1.configure(!null) >> { BuildExecutionContext context -> context.proceed() }
        0 * _._
    }

    def "makes parameters available to actions"() {
        BuildConfigurationAction action1 = Mock()

        given:
        def buildExecution = new DefaultBuildConfigurationActionExecuter([action1], projectStateRegistry)

        when:
        buildExecution.select(gradleInternal, executionPlan)

        then:
        1 * action1.configure(!null) >> { BuildExecutionContext context ->
            assert context.gradle == gradleInternal
            assert context.executionPlan == executionPlan
        }
    }

    def "can overwrite default task selectors"() {
        setup:
        BuildConfigurationAction action1 = Mock()
        BuildConfigurationAction action2 = Mock()

        BuildConfigurationAction newTaskSelector = Mock()


        def buildExecution = new DefaultBuildConfigurationActionExecuter([action1, action2], projectStateRegistry)

        when:
        buildExecution.setTaskSelectors([newTaskSelector])
        buildExecution.select(gradleInternal, executionPlan)

        then:

        0 * action1.configure(!null)
        0 * action2.configure(!null)

        1 * newTaskSelector.configure(!null) >> { BuildExecutionContext context ->
            assert context.gradle == gradleInternal
        }
    }
}
