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

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Task
import org.gradle.api.internal.provider.ValueSupplier.ValueProducer
import org.gradle.api.tasks.TaskState
import org.gradle.internal.state.ModelObject
import spock.lang.Specification

/**
 * An output property that does not know which task declares it uses the task that it was reached through.
 */
class TaskContextTest extends Specification implements ProviderAssertions {
    def task = Stub(Task)
    def otherTask = Stub(Task)

    def "value that is calculated from the state of a task has the task as context"() {
        expect:
        ValueProducer.taskState(task).taskContext == task
    }

    def "value whose content is produced by a task has no task context"() {
        expect:
        ValueProducer.task(task).taskContext == null
    }

    def "value with #description producer has no task context"() {
        expect:
        producer.taskContext == null

        where:
        description | producer
        "missing"   | ValueProducer.noProducer()
        "unknown"   | ValueProducer.unknown()
    }

    def "combined producers have the task context that the producers agree on"() {
        def state = ValueProducer.taskState(task)
        def content = ValueProducer.task(otherTask)

        expect:
        state.plus(content).taskContext == task
        content.plus(state).taskContext == task
        state.plus(ValueProducer.taskState(task)).taskContext == task
        state.plus(ValueProducer.taskState(otherTask)).taskContext == null
    }

    def "result of transformation has the task context of the source and is not produced by that task"() {
        def result = taskProvider().flatMap { Providers.of("value") }

        expect:
        result.producer.taskContext == task
        tasksOf(result.producer) == []
    }

    def "result of transformation keeps the task context of the provider that the transformation returns"() {
        def result = taskProvider().flatMap { ProviderTestUtil.withTaskState(otherTask, "other") }

        expect:
        result.producer.taskContext == otherTask
    }

    def "mapped provider keeps the task context"() {
        expect:
        taskProvider().map { it + "!" }.producer.taskContext == task
        taskProvider().flatMap { Providers.of("value") }.map { it + "!" }.producer.taskContext == task
    }

    def "output that does not know its task is produced by the task of the transformation that returns it"() {
        def output = unownedOutput("a")

        expect:
        assertHasProducer(taskProvider().flatMap { output }, task)
        assertHasProducer(taskProvider().flatMap { Providers.of("ignored").flatMap { output } }, task)
        assertHasProducer(taskProvider().map { "ignored" }.flatMap { output }, task)
    }

    def "output that does not know its task is produced by the task context of the source of the transformation"() {
        def output = unownedOutput("a")
        def holder = new DefaultProperty<String>(PropertyHost.NO_OP, String)
        holder.set(taskProvider().flatMap { Providers.of("held") })

        expect:
        assertHasProducer(taskProvider().flatMap { Providers.of("ignored") }.flatMap { output }, task)
        assertHasProducer(taskProvider().flatMap { Providers.of("ignored") }.map { it }.flatMap { output }, task)
        assertHasProducer(holder.flatMap { output }, task)
    }

    def "output that does not know its task is produced by the task of the innermost transformation"() {
        def output = unownedOutput("a")
        def otherTaskProvider = ProviderTestUtil.withTaskState(otherTask, "other")

        expect:
        assertHasProducer(taskProvider().flatMap { otherTaskProvider.flatMap { output } }, otherTask)
        assertHasProducer(taskProvider().flatMap { otherTaskProvider }.flatMap { output }, otherTask)
    }

    def "task produces the outputs of zipped providers"() {
        def zipped = unownedOutput("a").zip(ProviderTestUtil.withProducer(String, otherTask, "b")) { a, b -> a + b }

        expect:
        assertHasProducer(taskProvider().flatMap { zipped }, task, otherTask)
    }

    def "task produces the outputs of merged providers"() {
        def merged = new MergeProvider([unownedOutput("a"), ProviderTestUtil.withProducer(String, otherTask, "b")])

        expect:
        assertHasProducer(taskProvider().flatMap { merged }, task, otherTask)
    }

    def "task produces the output that has an alternative"() {
        def withAlternative = unownedOutput("a").orElse(ProviderTestUtil.withProducer(String, otherTask, "b"))

        expect:
        assertHasProducer(taskProvider().flatMap { withAlternative }, task)
    }

    def "task produces the output that is the alternative"() {
        def noValue = new DefaultProperty<String>(PropertyHost.NO_OP, String)
        def withAlternative = noValue.orElse(unownedOutput("a"))

        expect:
        assertHasProducer(taskProvider().flatMap { withAlternative }, task)
    }

    def "task produces the outputs that are elements of a list property"() {
        def list = new DefaultListProperty<String>(PropertyHost.NO_OP, String)
        list.add(unownedOutput("a"))
        list.add(ProviderTestUtil.withProducer(String, otherTask, "b"))
        list.add("c")

        expect:
        assertHasProducer(taskProvider().flatMap { list }, task, otherTask)
    }

    def "task produces the outputs that are entries of a map property"() {
        def map = new DefaultMapProperty<String, String>(PropertyHost.NO_OP, String, String)
        map.put("a", unownedOutput("a"))
        map.put("b", ProviderTestUtil.withProducer(String, otherTask, "b"))

        expect:
        assertHasProducer(taskProvider().flatMap { map }, task, otherTask)
    }

    def "fails when output does not know its task and the source of the transformation has no task context"() {
        def output = unownedOutput("a")
        def noTask = Providers.of("value")
        // content is produced by the task, as opposed to a value that is calculated from the state of the task
        def taskOutput = ProviderTestUtil.withProducer(String, task, "value")
        def twoTasks = taskProvider().zip(ProviderTestUtil.withTaskState(otherTask, "other")) { a, b -> a + b }

        when:
        noTask.flatMap { output }.producer

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("but does not have a task associated with it.")

        when:
        taskOutput.flatMap { output }.producer

        then:
        def e2 = thrown(IllegalStateException)
        e2.message.contains("but does not have a task associated with it.")

        when:
        twoTasks.flatMap { output }.producer

        then:
        def e3 = thrown(IllegalStateException)
        e3.message.contains("but does not have a task associated with it.")
    }

    def "cannot transform the content of an output that does not know its task before the task of the transformation has executed"() {
        def output = unownedOutput("a")
        def inside = taskProvider().flatMap { output.map { it + "!" } }
        def nested = taskProvider().flatMap { Providers.of("ignored").flatMap { output.map { it + "!" } } }

        when:
        inside.get()

        then:
        def e = thrown(InvalidUserCodeException)
        e.message.contains("before ${task} has completed is not supported")

        when:
        nested.get()

        then:
        def e2 = thrown(InvalidUserCodeException)
        e2.message.contains("before ${task} has completed is not supported")
    }

    def "can transform the content of an output that does not know its task after the task of the transformation has executed"() {
        def state = Stub(TaskState) {
            getExecuted() >> true
        }
        task.getState() >> state
        def output = unownedOutput("a")

        expect:
        taskProvider().flatMap { output.map { it + "!" } }.get() == "a!"
    }

    def "can query the value of an output that does not know its task before the task of the transformation has executed"() {
        def output = unownedOutput("a")

        expect:
        taskProvider().flatMap { output }.get() == "a"
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
}
