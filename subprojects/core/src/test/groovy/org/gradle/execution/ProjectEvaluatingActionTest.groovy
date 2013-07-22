/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.internal.project.DefaultProject
import spock.lang.Specification

class ProjectEvaluatingActionTest extends Specification {

    private evaluator = Mock(TaskPathProjectEvaluator)
    private context = Mock(BuildExecutionContext)
    private startParameter = Mock(StartParameter)
    private gradle = Mock(GradleInternal)
    private project = Mock(DefaultProject)

    private action = new ProjectEvaluatingAction(evaluator)

    def setup() {
        context.gradle >> gradle
        gradle.startParameter >> startParameter
        gradle.defaultProject >> project
    }

    def "evaluates projects by task paths and proceeds"() {
        when:
        action.configure(context)

        then:
        startParameter.taskNames >> ['foo', "bar:baz"]

        1 * context.proceed()
        1 * evaluator.evaluateByPath(project, 'foo')
        1 * evaluator.evaluateByPath(project, 'bar:baz')
        0 * project.evaluate()
        0 * evaluator._
    }

    def "evaluates the default project when the task names are empty"() {
        when:
        action.configure(context)

        then:
        startParameter.taskNames >> []

        1 * context.proceed()
        1 * project.evaluate()
        0 * project._
        0 * evaluator._
    }
}
