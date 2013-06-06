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

package org.gradle.configuration.project

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateInternal
import spock.lang.Specification

class ConfigureActionsProjectEvaluatorTest extends Specification {
    final def project = Mock(ProjectInternal)
    final def state = Mock(ProjectStateInternal)
    final def action1 = Mock(ProjectConfigureAction)
    final def action2 = Mock(ProjectConfigureAction)
    final def evaluator = new ConfigureActionsProjectEvaluator(action1, action2)

    def "executes all configuration actions"() {
        def project = Mock(ProjectInternal)

        when:
        evaluator.evaluate(project, state)

        then:
        1 * action1.execute(project)
        1 * action2.execute(project)
        0 * _._
    }

    def "does not continue executing actions when action fails"() {
        def project = Mock(ProjectInternal)
        def failure = new RuntimeException("Configure action failed")

        when:
        evaluator.evaluate(project, state)

        then:
        1 * action1.execute(project) >> {
            throw failure
        }
        0 * _._

        and:
        def t = thrown(RuntimeException)
        t == failure
    }
}
