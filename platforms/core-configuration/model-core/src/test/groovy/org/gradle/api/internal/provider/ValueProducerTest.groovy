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
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.WorkNodeAction
import spock.lang.Specification

/**
 * Tests the {@link ValueProducer} implementations.
 */
class ValueProducerTest extends Specification implements ProviderAssertions {

    def task = Stub(Task)
    def otherTask = Stub(Task)
    def work = Stub(WorkNodeAction)

    def "task producer visits its task as content"() {
        def producer = ValueProducer.task(task)

        expect:
        producer.known
        getDependencies(producer::visitDependencies) == [task]
        getDependencies(producer::visitContentDependencies) == [task]
    }

    def "task state producer does not visit its task as content"() {
        def producer = ValueProducer.taskState(task)

        expect:
        producer.known
        getDependencies(producer::visitDependencies) == [task]
        getDependencies(producer::visitContentDependencies) == []
    }

    def "delegating producer visits the dependencies of its container as content"() {
        def producer = ValueProducer.from(container(task, work))

        expect:
        producer.known
        getDependencies(producer::visitDependencies) == [task, work]
        getDependencies(producer::visitContentDependencies) == [task, work]
    }

    def "no producer is known and visits nothing"() {
        def producer = ValueProducer.noProducer()

        expect:
        producer.known
        getDependencies(producer::visitDependencies) == []
        getDependencies(producer::visitContentDependencies) == []
    }

    def "unknown producer is not known and visits nothing"() {
        def producer = ValueProducer.unknown()

        expect:
        !producer.known
        getDependencies(producer::visitDependencies) == []
        getDependencies(producer::visitContentDependencies) == []
    }

    def "plus producer is known when either side is known"() {
        def producer = new ValueSupplier.PlusProducer(producer(left), producer(right))

        expect:
        producer.known == known

        where:
        left      | right     | known
        "known"   | "known"   | true
        "known"   | "unknown" | true
        "unknown" | "known"   | true
        "unknown" | "unknown" | false
    }

    def "plus producer visits the dependencies and content dependencies of both sides"() {
        def producer = new ValueSupplier.PlusProducer(ValueProducer.taskState(task), ValueProducer.from(container(otherTask, work)))

        expect:
        getDependencies(producer::visitDependencies) == [task, otherTask, work]
        getDependencies(producer::visitContentDependencies) == [otherTask, work]
    }

    def "plus drops no producer next to a known producer and does not combine a producer with itself"() {
        def some = ValueProducer.task(task)

        expect:
        ValueProducer.noProducer().plus(some).is(some)
        some.plus(ValueProducer.noProducer()).is(some)
        some.plus(some).is(some)
    }

    def "plus with no producer and an unknown producer is known, like a plus producer"() {
        def unknown = ValueProducer.unknown()

        expect:
        ValueProducer.noProducer().plus(unknown).known
        unknown.plus(ValueProducer.noProducer()).known
        new ValueSupplier.PlusProducer(ValueProducer.noProducer(), unknown).known
    }

    def "composite producer is known when any part is known"() {
        def producer = ValueProducer.composite(parts.collect { producer(it) })

        expect:
        producer.known == known

        where:
        parts                              | known
        []                                 | false
        ["unknown"]                        | false
        ["unknown", "unknown"]             | false
        ["unknown", "known"]               | true
        ["known", "known"]                 | true
    }

    def "composite producer visits the dependencies and content dependencies of all parts"() {
        def producer = ValueProducer.composite([ValueProducer.taskState(task), ValueProducer.unknown(), ValueProducer.from(container(otherTask, work))])

        expect:
        getDependencies(producer::visitDependencies) == [task, otherTask, work]
        getDependencies(producer::visitContentDependencies) == [otherTask, work]
    }

    def "dependencies as content producer visits all dependencies as content"() {
        def producer = new ValueSupplier.DependenciesAsContentProducer(new ValueSupplier.PlusProducer(ValueProducer.taskState(task), ValueProducer.from(container(otherTask, work))))

        expect:
        producer.known
        getDependencies(producer::visitDependencies) == [task, otherTask, work]
        getDependencies(producer::visitContentDependencies) == [task, otherTask, work]
    }

    def "dependencies as content producer is known when its delegate is known"() {
        expect:
        new ValueSupplier.DependenciesAsContentProducer(ValueProducer.noProducer()).known
        !new ValueSupplier.DependenciesAsContentProducer(ValueProducer.unknown()).known
    }

    private ValueProducer producer(String kind) {
        return kind == "known" ? ValueProducer.task(Stub(Task)) : ValueProducer.unknown()
    }

    private static TaskDependencyContainer container(Object... dependencies) {
        return { context -> dependencies.each { context.add(it) } } as TaskDependencyContainer
    }

}
