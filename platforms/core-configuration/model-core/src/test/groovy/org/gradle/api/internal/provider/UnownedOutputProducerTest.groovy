/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.internal.provider.ValueSupplier.ValueProducer
import org.gradle.internal.state.ModelObject
import spock.lang.Specification

class UnownedOutputProducerTest extends Specification implements ProviderAssertions {
    def task = Stub(Task)
    def otherTask = Stub(Task)

    def "fails when producer tasks are visited and no task has taken ownership of the output"() {
        def producer = ValueProducer.unownedOutput { "no task" }

        expect:
        producer.known
        producer.hasUnownedOutput()

        when:
        producer.visitProducerTasks { }

        then:
        def e = thrown(IllegalStateException)
        e.message == "no task"
    }

    def "does not report content producers when no task has taken ownership of the output"() {
        def producer = ValueProducer.unownedOutput { "no task" }

        expect:
        contentProducersOf(producer) == []
    }

    def "task that takes ownership of the output produces its content"() {
        def producer = ValueProducer.unownedOutput { "no task" }.withOutputOwner(task)

        expect:
        !producer.hasUnownedOutput()
        tasksOf(producer) == [task]
        contentProducersOf(producer) == [task]
    }

    def "#description producer is not affected by ownership"() {
        expect:
        !producer.hasUnownedOutput()
        producer.withOutputOwner(Stub(Task)).is(producer)

        where:
        description | producer
        "missing"   | ValueProducer.noProducer()
        "unknown"   | ValueProducer.unknown()
    }

    def "task producers are not affected by ownership"() {
        def content = ValueProducer.task(otherTask)
        def state = ValueProducer.taskState(otherTask)
        def combined = content.plus(state)

        expect:
        content.withOutputOwner(task).is(content)
        state.withOutputOwner(task).is(state)
        !combined.hasUnownedOutput()
        combined.withOutputOwner(task).is(combined)
    }

    def "task takes ownership of the unowned outputs of combined producers"() {
        def producer = ValueProducer.unownedOutput { "no task" }.plus(ValueProducer.task(otherTask))

        expect:
        producer.hasUnownedOutput()
        def owned = producer.withOutputOwner(task)
        !owned.hasUnownedOutput()
        tasksOf(owned) == [task, otherTask]
    }

    def "task takes ownership of the unowned outputs of zipped providers"() {
        def zipped = unownedOutput("a").zip(ProviderTestUtil.withProducer(String, otherTask, "b")) { a, b -> a + b }

        expect:
        zipped.producer.hasUnownedOutput()
        assertHasProducer(taskProvider().flatMap { zipped }, task, otherTask)
    }

    def "task takes ownership of the unowned outputs of merged providers"() {
        def merged = new MergeProvider([unownedOutput("a"), ProviderTestUtil.withProducer(String, otherTask, "b")])

        expect:
        merged.producer.hasUnownedOutput()
        assertHasProducer(taskProvider().flatMap { merged }, task, otherTask)
    }

    def "task takes ownership of the unowned output that has an alternative"() {
        def withAlternative = unownedOutput("a").orElse(ProviderTestUtil.withProducer(String, otherTask, "b"))

        expect:
        withAlternative.producer.hasUnownedOutput()
        assertHasProducer(taskProvider().flatMap { withAlternative }, task)
    }

    def "task takes ownership of the unowned output that is the alternative"() {
        def noValue = new DefaultProperty<String>(PropertyHost.NO_OP, String)
        def withAlternative = noValue.orElse(unownedOutput("a"))

        expect:
        withAlternative.producer.hasUnownedOutput()
        assertHasProducer(taskProvider().flatMap { withAlternative }, task)
    }

    def "task takes ownership of the unowned outputs of the elements of a list property"() {
        def list = new DefaultListProperty<String>(PropertyHost.NO_OP, String)
        list.add(unownedOutput("a"))
        list.add(ProviderTestUtil.withProducer(String, otherTask, "b"))
        list.add("c")

        expect:
        list.producer.hasUnownedOutput()
        assertHasProducer(taskProvider().flatMap { list }, task, otherTask)
    }

    def "task takes ownership of the unowned outputs of the entries of a map property"() {
        def map = new DefaultMapProperty<String, String>(PropertyHost.NO_OP, String, String)
        map.put("a", unownedOutput("a"))
        map.put("b", ProviderTestUtil.withProducer(String, otherTask, "b"))

        expect:
        map.producer.hasUnownedOutput()
        assertHasProducer(taskProvider().flatMap { map }, task, otherTask)
    }

    private ProviderInternal<String> taskProvider() {
        return ProviderTestUtil.withTaskState(task, "task")
    }

    /**
     * A property that is declared as an output of an object that is not attached to a task.
     */
    private ProviderInternal<String> unownedOutput(String value) {
        def owner = Mock(ModelObject)
        def property = new DefaultProperty<String>(PropertyHost.NO_OP, String)
        property.set(value)
        property.attachProducer(owner)
        return property
    }

    private static List<Task> tasksOf(ValueProducer producer) {
        def tasks = []
        producer.visitProducerTasks { tasks.add(it) }
        return tasks
    }

    private static List<Task> contentProducersOf(ValueProducer producer) {
        def tasks = []
        producer.visitContentProducerTasks { tasks.add(it) }
        return tasks
    }
}
