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

import org.gradle.api.BuildCancelledException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.BuildCancellationToken
import spock.lang.Specification

class TaskPathProjectEvaluatorTest extends Specification {
    private cancellationToken = Mock(BuildCancellationToken)
    private project = Mock(ProjectInternal)
    private evaluator = new TaskPathProjectEvaluator(cancellationToken)

    def "project configuration fails when cancelled"() {
        given:
        cancellationToken.cancellationRequested >> true

        when:
        evaluator.configure(project)

        then:
        thrown(BuildCancelledException)

        and:
        0 * project._
    }

    def "project hierarchy configuration fails when cancelled"() {
        def child1 = Mock(ProjectInternal)
        def child2 = Mock(ProjectInternal)
        def subprojects = [child1, child2]

        given:
        project.subprojects >> subprojects
        cancellationToken.cancellationRequested >>> [false, false, true]

        when:
        evaluator.configureHierarchy(project)

        then:
        def e = thrown BuildCancelledException

        and:
        1 * project.evaluate()
        1 * child1.evaluate()
        0 * child2._
    }
}
