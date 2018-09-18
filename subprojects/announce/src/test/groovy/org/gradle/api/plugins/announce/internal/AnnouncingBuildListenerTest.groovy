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
package org.gradle.api.plugins.announce.internal

import spock.lang.Specification
import org.gradle.api.plugins.announce.Announcer
import org.gradle.api.Task
import org.gradle.BuildResult
import org.gradle.api.tasks.TaskState

class AnnouncingBuildListenerTest extends Specification {
    final Announcer announcer = Mock()
    final AnnouncingBuildListener listener = new AnnouncingBuildListener(announcer)

    def "announces build successful with task count"() {
        Task task = task()
        TaskState taskState = taskSuccessful()
        BuildResult result = buildSuccessful()

        given:
        listener.beforeExecute(task)
        listener.afterExecute(task, taskState)

        when:
        listener.buildFinished(result)

        then:
        1 * announcer.send("Build successful", "1 task executed")
    }

    def "announces build failed when no tasks failed"() {
        Task task = task()
        TaskState taskState = taskSuccessful()
        BuildResult result = buildFailed()

        given:
        listener.beforeExecute(task)
        listener.afterExecute(task, taskState)

        when:
        listener.buildFinished(result)

        then:
        1 * announcer.send("Build failed", "1 task executed")
    }

    def "announces build failed when 1 task failed"() {
        Task task = task()
        TaskState taskState = taskFailed()
        BuildResult result = buildFailed()

        given:
        listener.beforeExecute(task)
        listener.afterExecute(task, taskState)

        when:
        listener.buildFinished(result)

        then:
        1 * announcer.send("Build failed", "task 'task' failed\n1 task executed")
    }

    def "announces build failed when multiple tasks failed"() {
        Task task1 = task('task1')
        Task task2 = task('task2')
        TaskState taskState = taskFailed()
        BuildResult result = buildFailed()

        given:
        listener.beforeExecute(task1)
        listener.afterExecute(task1, taskState)

        when:
        listener.beforeExecute(task2)
        listener.afterExecute(task2, taskState)

        then:
        1 * announcer.send("task 'task1' failed", "1 task failed")

        when:
        listener.buildFinished(result)

        then:
        1 * announcer.send("Build failed", "2 tasks failed\n2 tasks executed")
    }

    def task(String name = 'task') {
        Task task = Mock()
        task.toString() >> "task '$name'"
        return task
    }

    def taskSuccessful() {
        TaskState state = Mock()
        return state
    }

    def taskFailed() {
        TaskState state = Mock()
        state.failure >> new RuntimeException()
        return state
    }

    def buildSuccessful() {
        BuildResult result = Mock()
        return result
    }

    def buildFailed() {
        BuildResult result = Mock()
        result.failure >> new RuntimeException()
        return result
    }
}
