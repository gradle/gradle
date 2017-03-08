/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.execution.taskgraph

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch

import static org.gradle.internal.progress.BuildOperationExecutor.*

class DefaultProjectLockServiceTest extends ConcurrentSpec {
    ProjectLockService projectLockService = new DefaultProjectLockService()

    def "can cleanly lock and unlock a project"() {
        def task = task(":task1", ":project1")

        when:
        projectLockService.lockProject(task)

        then:
        projectLockService.hasLock(task)

        then:
        projectLockService.unlockProject(task)

        then:
        !projectLockService.hasLock(task)

        then:
        !projectLockService.unlockProject(task)
    }

    def "accurately reflects whether a project is locked"() {
        def task1 = task(":task1", ":project1")
        def task2 = task(":task2", ":project1")

        when:
        projectLockService.lockProject(task1)

        then:
        projectLockService.isLocked(task1.project.path)

        when:
        projectLockService.unlockProject(task1)

        then:
        !projectLockService.isLocked(task1.project.path)

        when:
        projectLockService.lockProject(task2)

        then:
        projectLockService.isLocked(task1.project.path)
        projectLockService.isLocked(task2.project.path)

        when:
        projectLockService.unlockProject(task2)

        then:
        !projectLockService.isLocked(task1.project.path)
        !projectLockService.isLocked(task2.project.path)
    }

    def "can lock with the same task multiple times"() {
        def task = task(":task1", ":project1")

        when:
        projectLockService.lockProject(task)
        projectLockService.lockProject(task)
        projectLockService.lockProject(task)

        then:
        projectLockService.hasLock(task)

        and:
        noExceptionThrown()
    }

    def "multiple tasks can coordinate locking of a project"() {
        def tasks = []
        10.times { i ->
            tasks << task(":task${i}", "project1")
        }
        def started = new CountDownLatch(tasks.size())

        when:
        async {
            tasks.each { Task task ->
                start {
                    started.countDown()
                    projectLockService.lockProject(task)
                    try {
                        thread.blockUntil.releaseAll
                        assert projectLockService.hasLock(task)
                    } finally {
                        assert projectLockService.unlockProject(task)
                    }
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    def "locks on different projects do not affect each other"() {
        def tasks = []
        10.times { i ->
            tasks << task(":task${i}", "project${i}")
        }
        def started = new CountDownLatch(tasks.size())

        when:
        async {
            tasks.each { Task task ->
                start {
                    projectLockService.lockProject(task)
                    try {
                        started.countDown()
                        thread.blockUntil.releaseAll
                        assert projectLockService.hasLock(task)
                    } finally {
                        assert projectLockService.unlockProject(task)
                    }
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    def "can safely attempt to unlock a project locked by another task"() {
        def task1 = task(":task1", ":project1")
        def task2 = task(":task2", ":project1")

        when:
        projectLockService.lockProject(task1)

        then:
        ! projectLockService.unlockProject(task2)

        then:
        projectLockService.isLocked(task1.project.path)

        and:
        projectLockService.hasLock(task1)
    }

    def "cannot unlock a project from a different thread"() {
        def task = task(":task1", ":project1")

        when:
        async {
            start {
                projectLockService.lockProject(task)
                instant.locked
            }
            start {
                thread.blockUntil.locked
                projectLockService.unlockProject(task)
            }
        }

        then:
        thrown(IllegalMonitorStateException)
    }

    def "can unlock and lock a project by operation inside withProjectLock"() {
        def task = task(":task1", ":project1")
        def operation = Stub(Operation)

        when:
        projectLockService.withProjectLock(task, operation) {
            assert projectLockService.isLocked(task.project.path)
            assert projectLockService.hasLock(operation)

            assert projectLockService.unlockProject(operation)

            assert !projectLockService.isLocked(task.project.path)
            assert !projectLockService.hasLock(operation)

            projectLockService.lockProject(operation)

            assert projectLockService.isLocked(task.project.path)
            assert projectLockService.hasLock(operation)
        }

        then:
        noExceptionThrown()

        and:
        !projectLockService.isLocked(task.project.path)
    }

    def "exception is thrown when locking by operation outside of withProjectLock"() {
        def operation = Stub(Operation)

        when:
        projectLockService.lockProject(operation)

        then:
        thrown(IllegalStateException)

        when:
        projectLockService.unlockProject(operation)

        then:
        thrown(IllegalStateException)

        when:
        assert !projectLockService.hasLock(operation)

        then:
        noExceptionThrown()
    }

    def "exception is thrown when locking by operation used previously in withProjectLock"() {
        def task = task(":task1", ":project1")
        def operation = Stub(Operation)

        given:
        projectLockService.withProjectLock(task, operation) {
            assert projectLockService.isLocked(task.project.path)
        }

        when:
        projectLockService.lockProject(operation)

        then:
        thrown(IllegalStateException)

        when:
        projectLockService.unlockProject(operation)

        then:
        thrown(IllegalStateException)

        when:
        assert !projectLockService.hasLock(operation)

        then:
        noExceptionThrown()
    }

    def "multiple operations can be used with the same task for withProjectLock"() {
        def task = task(":task1", ":project1")
        def operation1 = Stub(Operation)
        def operation2 = Stub(Operation)
        def operation3 = Stub(Operation)

        when:
        projectLockService.withProjectLock(task, operation1) {
            assert projectLockService.isLocked(task.project.path)
        }
        projectLockService.withProjectLock(task, operation2) {
            assert projectLockService.isLocked(task.project.path)
        }
        projectLockService.withProjectLock(task, operation3) {
            assert projectLockService.isLocked(task.project.path)
        }

        then:
        noExceptionThrown()
    }

    def "multiple tasks can coordinate unlocking/locking using withProjectLock"() {
        def tasks = []
        10.times { i ->
            tasks << task(":task${i}", "project1")
        }
        def started = new CountDownLatch(tasks.size())

        when:
        async {
            tasks.each { Task task ->
                def operation = Stub(Operation)
                start {
                    started.countDown()
                    projectLockService.withProjectLock(task, operation) {
                        thread.blockUntil.releaseAll
                        assert projectLockService.hasLock(operation)
                        println "${task.path} has lock"
                        projectLockService.unlockProject(operation)
                        assert !projectLockService.hasLock(operation)
                        println "${task.path} released lock"
                        projectLockService.lockProject(operation)
                        println "${task.path} reacquired lock"
                        assert projectLockService.hasLock(operation)
                        println "${task.path} finished"
                    }
                    assert !projectLockService.hasLock(task)
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    Task task(String taskPath, String projectPath) {
        Project project = Stub(Project)
        _ * project.getPath() >> projectPath
        Task task = Stub(Task)
        _ * task.getPath() >> taskPath
        _ * task.getProject() >> project
        return task
    }
}
