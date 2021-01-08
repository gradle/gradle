/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.internal.project.ProjectInternal
import spock.lang.Specification

class DelayedConfigurationActionsTest extends Specification {
    final container = Mock(ProjectConfigurationActionContainer)
    final project = Stub(ProjectInternal) {
        getConfigurationActions() >> container
    }
    final action = new DelayedConfigurationActions()

    def "runs actions and discards actions when finished"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)

        given:
        container.actions >> [action1, action2]

        when:
        action.execute(project)

        then:
        1 * action1.execute(project)
        1 * action2.execute(project)

        and:
        1 * container.finished()
    }

    def "discards actions on failure"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def failure = new RuntimeException()

        given:
        container.actions >> [action1, action2]

        when:
        action.execute(project)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * action1.execute(project) >> { throw failure }
        0 * action2._

        and:
        1 * container.finished()
    }
}
