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

package org.gradle.execution.plan

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.invocation.Gradle
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.concurrent.DefaultWorkerLimits
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.WorkerLeaseService
import spock.lang.Specification

class DefaultPlanExecutorTest extends Specification {
    def workSource = Mock(WorkSource)
    def worker = Mock(Action)
    def executorFactory = Mock(ExecutorFactory)
    def cancellationHandler = Mock(BuildCancellationToken)
    def coordinationService = new DefaultResourceLockCoordinationService()
    def workerLeaseService = Mock(WorkerLeaseService)
    def workerLease = Mock(WorkerLeaseRegistry.WorkerLease)
    def executor = new DefaultPlanExecutor(new DefaultWorkerLimits(1), executorFactory, workerLeaseService, cancellationHandler, coordinationService, new DefaultInternalOptions([:]))

    def "executes tasks until no further tasks remain"() {
        def gradle = Mock(Gradle)
        def project = Mock(Project)
        def node = Mock(LocalTaskNode)
        def task = Mock(TaskInternal)
        def state = Mock(TaskStateInternal)
        project.gradle >> gradle
        task.project >> project
        task.state >> state

        when:
        def result = executor.process(workSource, worker)

        then:
        result.failures.empty

        1 * workerLeaseService.currentWorkerLease >> workerLease

        then:
        1 * cancellationHandler.isCancellationRequested() >> false
        1 * workerLease.tryLock() >> true
        1 * workSource.executionState() >> WorkSource.State.MaybeWorkReadyToStart
        1 * workSource.selectNext() >> WorkSource.Selection.of(node)
        1 * worker.execute(node)
        1 * workSource.finishedExecuting(node, null)

        then:
        1 * cancellationHandler.isCancellationRequested() >> false
        1 * workSource.executionState() >> WorkSource.State.NoMoreWorkToStart

        then:
        1 * workerLease.tryLock() >> true
        3 * workSource.allExecutionComplete() >> true
        1 * workSource.collectFailures([])
        0 * workSource._
    }

    def "execution is canceled when cancellation requested"() {
        def gradle = Mock(Gradle)
        def project = Mock(Project)
        def node = Mock(LocalTaskNode)
        def task = Mock(TaskInternal)
        def state = Mock(TaskStateInternal)
        project.gradle >> gradle
        task.project >> project
        task.state >> state

        when:
        def result = executor.process(workSource, worker)

        then:
        result.failures.empty
        1 * workerLeaseService.currentWorkerLease >> workerLease

        then:
        1 * cancellationHandler.isCancellationRequested() >> false
        1 * workSource.executionState() >> WorkSource.State.MaybeWorkReadyToStart
        1 * workerLease.tryLock() >> true
        1 * workSource.selectNext() >> WorkSource.Selection.of(node)
        1 * worker.execute(node)
        1 * workSource.finishedExecuting(node, null)

        then:
        1 * cancellationHandler.isCancellationRequested() >> true
        1 * workSource.cancelExecution()
        1 * workSource.executionState() >> WorkSource.State.NoMoreWorkToStart

        then:
        1 * workerLease.tryLock() >> true
        3 * workSource.allExecutionComplete() >> true
        1 * workSource.collectFailures([])
        0 * workSource._
    }
}
