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

import org.gradle.api.internal.project.ProjectInternal
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 1/8/13
 */
class TaskPathProjectEvaluatorTest extends Specification {

    private finder = Mock(ProjectFinderByTaskPath)
    private project = Mock(ProjectInternal)

    private evaluator = new TaskPathProjectEvaluator(finder)

    def "evaluates task path"() {
        def foundProject = Mock(ProjectInternal)

        when:
        evaluator.evaluateByPath(project, ":foo:bar")

        then:
        1 * finder.findProject(":foo:bar", project) >> foundProject
        1 * foundProject.evaluate()
        0 * _._
    }

    def "evaluates task name"() {
        def subprojects = [Mock(ProjectInternal), Mock(ProjectInternal)]

        when:
        evaluator.evaluateByPath(project, "someTask")

        then:
        1 * project.evaluate()
        1 * project.subprojects >> subprojects
        1 * subprojects[0].evaluate()
        1 * subprojects[1].evaluate()
        0 * _._
    }
}
