/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provider

import org.gradle.api.Task
import spock.lang.Specification

class ValueProducerTest extends Specification {
    def "combining producers preserves the distinction between task state and content"() {
        given:
        def stateTask = Mock(Task)
        def contentTask = Mock(Task)
        def producer = ValueSupplier.ValueProducer.taskState(stateTask).plus(ValueSupplier.ValueProducer.task(contentTask))
        def tasks = []
        def contentTasks = []

        when:
        producer.visitProducerTasks { tasks.add(it) }
        producer.visitContentProducerTasks { contentTasks.add(it) }

        then:
        tasks == [stateTask, contentTask]
        contentTasks == [contentTask]
    }

    def "nested combinations of task state producers do not report content producers"() {
        given:
        def a = Mock(Task)
        def b = Mock(Task)
        def c = Mock(Task)
        def producer = ValueSupplier.ValueProducer.taskState(a).plus(
            ValueSupplier.ValueProducer.taskState(b).plus(ValueSupplier.ValueProducer.taskState(c)))
        def tasks = []

        when:
        producer.visitContentProducerTasks { tasks.add(it) }

        then:
        tasks.empty
    }
}
