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

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import spock.lang.Specification

import static java.util.Collections.emptySet

class ExcludedTaskFilteringBuildConfigurationActionTest extends Specification {
    final BuildExecutionContext context = Mock()
    final StartParameter startParameter = Mock()
    final TaskGraphExecuter taskGraph = Mock()
    final TaskSelector selector = Mock()
    final GradleInternal gradle = Mock()
    final ExcludedTaskFilteringBuildConfigurationAction action = new ExcludedTaskFilteringBuildConfigurationAction(selector)

    def setup() {
        _ * context.gradle >> gradle
        _ * gradle.startParameter >> startParameter
        _ * gradle.taskGraph >> taskGraph
    }

    def "calls proceed when there are no excluded tasks defined"() {
        given:
        _ * startParameter.excludedTaskNames >> []

        when:
        action.configure(context)

        then:
        1 * context.proceed()
    }

    def "applies a filter for excluded tasks before proceeding"() {
        given:
        _ * startParameter.excludedTaskNames >> ['a']

        when:
        action.configure(context)

        then:
        1 * selector.init(gradle)
        1 * selector.getSelection('a') >> new TaskSelector.TaskSelection('a', emptySet())
        1 * taskGraph.useFilter(!null)
        1 * context.proceed()
    }
}
