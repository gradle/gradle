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
package org.gradle.api.internal.changedetection

import spock.lang.Specification
import org.gradle.api.internal.TaskInternal

class CompositeUpToDateRuleTest extends Specification {
    final UpToDateRule rule1 = Mock()
    final UpToDateRule rule2 = Mock()
    final UpToDateRule.TaskUpToDateState state1 = Mock()
    final UpToDateRule.TaskUpToDateState state2 = Mock()
    final TaskInternal task = Mock()
    final TaskExecution previous = Mock()
    final TaskExecution current = Mock()
    final CompositeUpToDateRule rule = new CompositeUpToDateRule(rule1, rule2)

    def delegatesToEachRuleInOrder() {
        when:
        def state = rule.create(task, previous, current)

        then:
        1 * rule1.create(task, previous, current) >> state1
        1 * rule2.create(task, previous, current) >> state2

        when:
        state.checkUpToDate([])

        then:
        1 * state1.checkUpToDate([])
        1 * state2.checkUpToDate([])

        when:
        state.snapshotAfterTask()

        then:
        1 * state1.snapshotAfterTask()
        1 * state2.snapshotAfterTask()
    }

    def checkUpToDateStopsAtFirstRuleWhichMarksTaskOutOfDate() {
        when:
        def state = rule.create(task, previous, current)

        then:
        1 * rule1.create(task, previous, current) >> state1
        1 * rule2.create(task, previous, current) >> state2

        when:
        state.checkUpToDate([])

        then:
        1 * state1.checkUpToDate([]) >> { args -> args[0] << 'out-of-date' }
        0 * state2.checkUpToDate(_)
    }
}
