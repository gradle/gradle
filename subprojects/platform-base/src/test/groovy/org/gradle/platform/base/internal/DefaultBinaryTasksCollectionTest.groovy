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

package org.gradle.platform.base.internal

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.tasks.Copy
import org.gradle.model.internal.core.NamedEntityInstantiator
import spock.lang.Specification

class DefaultBinaryTasksCollectionTest extends Specification {
    def binary = Mock(BinarySpecInternal)
    def taskFactory = Mock(NamedEntityInstantiator)
    def tasks = new DefaultBinaryTasksCollection(binary, taskFactory, CollectionCallbackActionDecorator.NOOP)
    def task = Mock(Task)

    def "can create task"() {
        def action = Mock(Action)
        def task = Stub(Task)

        when:
        tasks.create("foo", DefaultTask, action)

        then:
        1 * taskFactory.create("foo", DefaultTask) >> task
        1 * action.execute(task)
    }

    def "provides lifecycle task for binary"() {
        when:
        1 * binary.buildTask >> task

        then:
        tasks.build == task
    }

    def "returns null for missing single task with type"() {
        expect:
        tasks.findSingleTaskWithType(Copy) == null
    }

    def "returns single task with type"() {
        def copyTask = Mock(Copy)
        when:
        tasks.add(copyTask)

        then:
        tasks.findSingleTaskWithType(Copy) == copyTask
    }

    def "fails finding single task with type where multiple exist"() {
        def copyTask1 = Mock(Copy)
        def copyTask2 = Mock(Copy)
        when:
        tasks.add(copyTask1)
        tasks.add(copyTask2)

        and:
        tasks.findSingleTaskWithType(Copy)

        then:
        def t = thrown UnknownDomainObjectException
        t.message == "Multiple tasks with type 'Copy' found."
    }

    def "generates a task name"() {
        given:
        binary.projectScopedName >> "myLibJar"

        expect:
        tasks.taskName("compile") == "compileMyLibJar"
        tasks.taskName("compile", "java") == "compileMyLibJarJava"
    }
}
