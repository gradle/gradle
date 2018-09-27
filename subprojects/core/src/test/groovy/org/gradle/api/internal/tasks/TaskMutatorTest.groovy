/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks

import org.gradle.api.internal.TaskInternal
import spock.lang.Specification

class TaskMutatorTest extends Specification {
    def state = Stub(TaskStateInternal)
    def task = Stub(TaskInternal) {
        getState() >> state
        toString() >> "<task>"
    }
    def nagger = new TaskMutator(task)

    def "executes mutation action when in configurable state"() {
        def action = Mock(Runnable)

        given:
        state.configurable >> true

        when:
        nagger.mutate("Task.thing()", action)

        then:
        1 * action.run()
    }

    def "mutation action fails when not in configurable state"() {
        def action = Mock(Runnable)

        given:
        state.configurable >> false

        when:
        nagger.mutate("Task.thing()", action)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot call Task.thing() on <task> after task has started execution.'
    }
}
