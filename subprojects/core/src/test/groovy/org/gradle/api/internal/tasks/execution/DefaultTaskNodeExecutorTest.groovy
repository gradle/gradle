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
package org.gradle.api.internal.tasks.execution

import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.execution.TaskActionListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsEnterpriseInternal
import org.gradle.api.internal.changedetection.TaskExecutionMode
import org.gradle.api.internal.changedetection.TaskExecutionModeResolver
import org.gradle.api.internal.changedetection.changes.DefaultTaskExecutionMode
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.internal.tasks.properties.TaskProperties
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.MissingTaskDependencyDetector
import org.gradle.execution.plan.MutationInfo
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.TaskNode
import org.gradle.execution.taskgraph.TaskListenerInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.Try
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.internal.execution.Execution
import org.gradle.internal.execution.ExecutionContext
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkValidationContext
import org.gradle.internal.execution.WorkOutput
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.file.ReservedFileSystemLocationRegistry
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.logging.StandardOutputCapture
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import org.gradle.internal.work.AsyncWorkTracker
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_AND_REACQUIRE_PROJECT_LOCKS
import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_PROJECT_LOCKS

/**
 * Tests {@link DefaultTaskNodeExecutor}.
 */
class DefaultTaskNodeExecutorTest extends Specification {

    def projectId = ProjectIdentity.forRootProject(Path.ROOT, "root")
    def projectScriptSource = Mock(ScriptSource)
    def project = Mock(ProjectInternal) {
        getProjectIdentity() >> projectId
        getBuildScriptSource() >> projectScriptSource
    }

    def taskIdentity = TestTaskIdentities.create("name", DefaultTask.class, project)
    DescribingAndSpec<Task> onlyIfSpec = Mock()
    def taskOutputs = Mock(TaskOutputsEnterpriseInternal)
    def state = new TaskStateInternal()
    List<InputChangesAwareTaskAction> taskActions = new ArrayList<>()
    def standardOutputCapture = Mock(StandardOutputCapture)
    def task = Mock(TaskInternal) {
        getTaskIdentity() >> taskIdentity
        getProject() >> project
        getOnlyIf() >> onlyIfSpec
        getOutputs() >> taskOutputs
        getState() >> state
        getIdentityPath() >> taskIdentity.buildTreePath
        getTaskActions() >> taskActions
        hasTaskActions() >> { !taskActions.isEmpty() }
        getStandardOutputCapture() >> standardOutputCapture
    }

    def taskProperties = Stub(TaskProperties) {
        getInputFileProperties() >> ImmutableSortedSet.of()
        getOutputFileProperties() >> ImmutableSortedSet.of()
    }
    Set<Node> dependencyNodes = new LinkedHashSet<>()
    def node = Stub(LocalTaskNode) {
        getTask() >> task
        getTaskProperties() >> taskProperties
        getValidationContext() >> Stub(WorkValidationContext)
        getMutationInfo() >> MutationInfo.EMPTY
        getDependencySuccessors() >> { dependencyNodes }
    }

    def action1 = Mock(InputChangesAwareTaskAction) {
        getActionImplementation(_ as ClassLoaderHierarchyHasher) >> ImplementationSnapshot.of("Action1", TestHashCodes.hashCodeFrom(1234))
    }
    def action2 = Mock(InputChangesAwareTaskAction) {
        getActionImplementation(_ as ClassLoaderHierarchyHasher) >> ImplementationSnapshot.of("Action2", TestHashCodes.hashCodeFrom(1234))
    }

    def buildOperationRunner = new TestBuildOperationRunner()
    def taskExecutionListener = Mock(TaskExecutionListener)
    def taskListener = Mock(TaskListenerInternal)
    def taskExecutionModeResolver = Mock(TaskExecutionModeResolver) {
        getExecutionMode(_, _) >> DefaultTaskExecutionMode.incremental()
    }
    def asyncWorkTracker = Mock(AsyncWorkTracker)
    def actionListener = Stub(TaskActionListener)
    def taskCacheabilityResolver = Stub(TaskCacheabilityResolver) {
        shouldDisableCaching(_) >> Optional.empty()
    }
    def classloaderHierarchyHasher = new ClassLoaderHierarchyHasher() {
        @Override
        HashCode getClassLoaderHash(ClassLoader classLoader) {
            return TestHashCodes.hashCodeFrom(1234)
        }
    }
    def executionEngine = Mock(ExecutionEngine)
    def listenerManager = Stub(ListenerManager)
    def reservedFileSystemLocationRegistry = Stub(ReservedFileSystemLocationRegistry)
    def missingTaskDependencyDetector = Stub(MissingTaskDependencyDetector)

    def executor = new DefaultTaskNodeExecutor(
        buildOperationRunner,
        taskExecutionListener,
        taskListener,
        taskExecutionModeResolver,
        Mock(ExecutionHistoryStore),
        asyncWorkTracker,
        actionListener,
        taskCacheabilityResolver,
        classloaderHierarchyHasher,
        executionEngine,
        Mock(InputFingerprinter),
        listenerManager,
        reservedFileSystemLocationRegistry,
        TestFiles.fileCollectionFactory(),
        TestFiles.taskDependencyFactory(),
        Stub(PathToFileResolver),
        missingTaskDependencyDetector
    )

    def doesNothingWhenTaskHasNoActions() {
        when:
        executor.execute(node)

        then:
        state.outcome == TaskExecutionOutcome.UP_TO_DATE
        !state.actionable
        0 * executionEngine._
        0 * standardOutputCapture._
    }

    def skipsTaskWithNoActionsAndMarksUpToDateIfAllItsDependenciesWereSkipped() {
        given:
        dependencyNodes.add(dependencyTaskNode(true))

        when:
        executor.execute(node)

        then:
        state.outcome == TaskExecutionOutcome.UP_TO_DATE
        !state.actionable
        0 * executionEngine._
        0 * standardOutputCapture._
    }

    def skipsTaskWithNoActionsAndMarksOutOfDateDateIfAnyOfItsDependenciesWereNotSkipped() {
        given:
        dependencyNodes.add(dependencyTaskNode(false))

        when:
        executor.execute(node)

        then:
        state.outcome == TaskExecutionOutcome.EXECUTED
        !state.didWork
        !state.executing
        !state.actionable
        0 * executionEngine._
        0 * standardOutputCapture._
    }

    def "dependency nodes that are not tasks do not affect the outcome of a task with no actions"() {
        given:
        dependencyNodes.add(dependencyTaskNode(true))
        dependencyNodes.add(Stub(Node))

        when:
        executor.execute(node)

        then:
        state.outcome == TaskExecutionOutcome.UP_TO_DATE
        !state.actionable
        0 * executionEngine._
        0 * standardOutputCapture._
    }

    def executesEachActionInOrder() {
        given:
        expectExecution()
        taskActions.addAll([action1, action2])

        when:
        executor.execute(node)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * action1.execute(task) >> {
            assert state.executing
        }
        then:
        1 * action1.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_AND_REACQUIRE_PROJECT_LOCKS)
        then:
        1 * standardOutputCapture.stop()
        then:
        1 * standardOutputCapture.start()
        then:
        1 * action2.execute(task)
        then:
        1 * action2.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_PROJECT_LOCKS)
        then:
        1 * standardOutputCapture.stop()

        !state.executing
        state.didWork
        state.outcome == TaskExecutionOutcome.EXECUTED
        !state.failure
        state.actionable
        buildOperationRunner.operations.size() == 3
    }

    def executeDoesOperateOnNewActionListInstance() {
        given:
        expectExecution()
        task.getActions() >> [action1]
        taskActions.add(action1)

        when:
        executor.execute(node)

        then:
        1 * standardOutputCapture.start()

        then:
        1 * action1.execute(task) >> {
            task.getActions().add(action2)
        }
        then:
        1 * action1.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_PROJECT_LOCKS)
        then:
        1 * standardOutputCapture.stop()

        and:
        buildOperationRunner.operations.size() == 2
    }

    def stopsAtFirstActionWhichThrowsException() {
        given:
        expectExecution()
        taskActions.addAll([action1, action2])
        def failure = new RuntimeException("failure")

        when:
        executor.execute(node)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * action1.execute(task) >> {
            throw failure
        }
        then:
        1 * action1.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_AND_REACQUIRE_PROJECT_LOCKS)
        then:
        1 * standardOutputCapture.stop()

        !state.executing
        state.didWork
        state.outcome == TaskExecutionOutcome.EXECUTED
        state.actionable
        buildOperationRunner.operations.size() == 2

        TaskExecutionException wrappedFailure = (TaskExecutionException) state.failure
        wrappedFailure.task == task
        wrappedFailure.message.startsWith("Execution failed for")
        wrappedFailure.cause.is(failure)
    }

    def stopsAtFirstActionWhichThrowsStopExecutionException() {
        given:
        expectExecution()
        taskActions.addAll([action1, action2])

        when:
        executor.execute(node)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * action1.execute(task) >> {
            throw new StopExecutionException('stop')
        }
        then:
        1 * action1.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_AND_REACQUIRE_PROJECT_LOCKS)
        then:
        1 * standardOutputCapture.stop()
        state.didWork
        !state.executing
        state.outcome == TaskExecutionOutcome.EXECUTED
        !state.failure
        buildOperationRunner.operations.size() == 2
    }

    def skipsActionWhichThrowsStopActionException() {
        given:
        expectExecution()
        taskActions.addAll([action1, action2])
        when:
        executor.execute(node)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * action1.execute(task) >> {
            throw new StopActionException('stop')
        }
        then:
        1 * action1.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_AND_REACQUIRE_PROJECT_LOCKS)
        then:
        1 * standardOutputCapture.stop()
        then:
        1 * standardOutputCapture.start()
        then:
        1 * action2.execute(task)
        then:
        1 * action2.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_PROJECT_LOCKS)
        then:
        1 * standardOutputCapture.stop()

        state.didWork
        state.outcome == TaskExecutionOutcome.EXECUTED
        !state.executing
        !state.failure
        state.actionable
        buildOperationRunner.operations.size() == 3
    }

    def "captures exceptions from async work"() {
        given:
        expectExecution()
        taskActions.addAll([action1, action2])

        when:
        executor.execute(node)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * action1.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_AND_REACQUIRE_PROJECT_LOCKS) >> {
            throw new DefaultMultiCauseException("mock failures", new RuntimeException("failure 1"), new RuntimeException("failure 2"))
        }
        then:
        1 * standardOutputCapture.stop()

        !state.executing
        state.didWork
        state.outcome == TaskExecutionOutcome.EXECUTED
        buildOperationRunner.operations.size() == 2

        TaskExecutionException wrappedFailure = (TaskExecutionException) state.failure
        wrappedFailure.task == task
        wrappedFailure.message.startsWith("Execution failed for ")
        wrappedFailure.cause instanceof MultiCauseException
        wrappedFailure.cause.causes.size() == 2
        wrappedFailure.cause.causes.any { it.message == "failure 1" }
        wrappedFailure.cause.causes.any { it.message == "failure 2" }
    }

    def "captures exceptions from both task action and async work"() {
        given:
        expectExecution()
        taskActions.addAll([action1, action2])

        when:
        executor.execute(node)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * action1.execute(task) >> {
            throw new RuntimeException("failure from task action")
        }
        then:
        1 * action1.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_AND_REACQUIRE_PROJECT_LOCKS) >> {
            throw new DefaultMultiCauseException("mock failures", new RuntimeException("failure 1"), new RuntimeException("failure 2"))
        }
        then:
        1 * standardOutputCapture.stop()

        !state.executing
        state.didWork
        state.outcome == TaskExecutionOutcome.EXECUTED
        buildOperationRunner.operations.size() == 2

        TaskExecutionException wrappedFailure = (TaskExecutionException) state.failure
        wrappedFailure.task == task
        wrappedFailure.message.startsWith("Execution failed for ")
        wrappedFailure.cause instanceof MultiCauseException
        wrappedFailure.cause.causes.size() == 3
        wrappedFailure.cause.causes.any { it.message == "failure 1" }
        wrappedFailure.cause.causes.any { it.message == "failure 2" }
        wrappedFailure.cause.causes.any { it.message == "failure from task action" }
    }

    def "a single exception from async work is not wrapped in a multi cause exception"() {
        given:
        expectExecution()
        taskActions.addAll([action1, action2])
        def failure = new RuntimeException("failure 1")

        when:
        executor.execute(node)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * action1.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_AND_REACQUIRE_PROJECT_LOCKS) >> {
            throw new DefaultMultiCauseException("mock failures", failure)
        }
        then:
        1 * standardOutputCapture.stop()

        and:
        !state.executing
        state.didWork
        state.outcome == TaskExecutionOutcome.EXECUTED
        buildOperationRunner.operations.size() == 2

        TaskExecutionException wrappedFailure = (TaskExecutionException) state.failure
        wrappedFailure.task == task
        wrappedFailure.message.startsWith("Execution failed for ")
        wrappedFailure.cause.is(failure)
    }

    def "notifies task listeners"() {
        given:
        taskActions.add(action1)
        expectExecution()

        when:
        executor.execute(node)

        then:
        1 * taskListener.beforeExecute(taskIdentity)
        1 * taskExecutionListener.beforeExecute(task)

        then:
        1 * taskExecutionListener.afterExecute(task, state)
        1 * taskListener.afterExecute(taskIdentity, state)
        0 * taskExecutionListener._
        0 * taskListener._

        and:
        buildOperationRunner.operations[0].name == ":name"
        buildOperationRunner.operations[0].displayName == "Task :name"
        buildOperationRunner.operations[0].progressDisplayName == ":name"
        buildOperationRunner.operations[0].metadata == BuildOperationCategory.TASK
    }

    def "does not run task action when beforeExecute event fails"() {
        given:
        def failure = new RuntimeException()

        when:
        executor.execute(node)

        then:
        1 * taskListener.beforeExecute(taskIdentity)
        1 * taskExecutionListener.beforeExecute(task) >> { throw failure }
        0 * executionEngine._
        0 * standardOutputCapture._
        0 * taskExecutionListener._
        0 * taskListener._

        and:
        state.failure instanceof TaskExecutionException
        state.failure.cause == failure

        and:
        def operation = buildOperationRunner.log.records[0]
        operation.failure != null
        operation.result == null
    }

    def "notifies task listeners when task execution fails"() {
        given:
        taskActions.add(action1)
        def failure = new RuntimeException()
        expectFailure(failure)

        when:
        executor.execute(node)

        then:
        1 * taskListener.beforeExecute(taskIdentity)
        1 * taskExecutionListener.beforeExecute(task)

        then:
        1 * taskExecutionListener.afterExecute(task, state)
        1 * taskListener.afterExecute(taskIdentity, state)
        0 * taskExecutionListener._
        0 * taskListener._

        and:
        state.failure instanceof TaskExecutionException
        state.failure.cause == failure

        and:
        def operation = buildOperationRunner.log.records[0]
        operation.failure != null
    }

    def "result of build operation is set even if listener throws exception"() {
        given:
        def failure = new RuntimeException()
        taskActions.add(action1)
        expectExecution()

        when:
        executor.execute(node)

        then:
        1 * taskExecutionListener.beforeExecute(task)

        then:
        1 * taskExecutionListener.afterExecute(task, state) >> {
            throw failure
        }
        0 * taskExecutionListener._

        and:
        state.failure instanceof TaskExecutionException
        state.failure.cause == failure

        and:
        def operation = buildOperationRunner.log.records[0]
        operation.failure != null
        operation.result != null
    }

    def "result of build operation is set even if both execution and listener fail"() {
        given:
        taskActions.add(action1)
        def failure = new RuntimeException("one")
        def failure2 = new RuntimeException("two")
        expectFailure(failure)

        when:
        executor.execute(node)

        then:
        1 * taskExecutionListener.beforeExecute(task)

        then:
        1 * taskExecutionListener.afterExecute(task, state) >> {
            throw failure2
        }
        0 * taskExecutionListener._

        and:
        state.failure instanceof TaskExecutionException
        state.failure.causes == [failure, failure2]
        def operation = buildOperationRunner.log.records[0]
        operation.failure != null
        operation.result != null
    }

    def "should catch exception of execution and set the outcome to failure"() {
        given:
        taskActions.add(action1)
        def failure = new RuntimeException("Failure")

        when:
        executor.execute(node)

        then:
        1 * executionEngine.createRequest(_) >> {
            throw failure
        }
        state.failure.cause == failure
        0 * executionEngine._
        0 * standardOutputCapture._
    }

    def "skips task whose onlyIf predicate is false"() {
        when:
        executor.execute(node)

        then:
        1 * onlyIfSpec.findUnsatisfiedSpec(task) >> Mock(SelfDescribingSpec)
        0 * executionEngine._
        0 * standardOutputCapture._
        state.outcome == TaskExecutionOutcome.SKIPPED
    }

    def "handles old style onlyIf spec"() {
        given:
        Spec<Task> oldStyleSpec = Mock(Spec)
        def otherTask = Stub(TaskInternal) {
            getOnlyIf() >> oldStyleSpec
            getState() >> state
        }
        def otherNode = Stub(LocalTaskNode) {
            getTask() >> otherTask
        }

        when:
        executor.execute(otherNode)

        then:
        1 * oldStyleSpec.isSatisfiedBy(otherTask) >> false
        0 * executionEngine._
        0 * standardOutputCapture._
        state.outcome == TaskExecutionOutcome.SKIPPED
    }

    def "wraps onlyIf predicate failure"() {
        given:
        RuntimeException failure = new RuntimeException()

        when:
        executor.execute(node)

        then:
        1 * onlyIfSpec.findUnsatisfiedSpec(task) >> { throw failure }
        0 * executionEngine._
        0 * standardOutputCapture._
        state.failure.cause == failure
        state.failure.message.startsWith('Could not evaluate onlyIf predicate for')
    }

    def "resolves the task execution mode and carries its rebuild reason to the engine"() {
        given:
        TaskExecutionMode executionMode = Mock(TaskExecutionMode)
        def request = Mock(ExecutionEngine.Request)
        taskActions.add(action1)

        when:
        executor.execute(node)

        then:
        1 * taskExecutionModeResolver.getExecutionMode(task, taskProperties) >> executionMode

        then:
        1 * executionEngine.createRequest(_) >> request
        1 * executionMode.getRebuildReason() >> Optional.of("because")
        1 * request.forceNonIncremental("because")
        1 * request.execute() >> Stub(ExecutionEngine.Result) {
            getExecution() >> Try.successful(Stub(Execution) {
                getOutcome() >> Execution.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
            })
        }

        and:
        state.outcome == TaskExecutionOutcome.EXECUTED
    }

    private TaskNode dependencyTaskNode(boolean skipped) {
        Stub(TaskNode) {
            getTask() >> Stub(TaskInternal) {
                getState() >> Stub(TaskStateInternal) {
                    getSkipped() >> skipped
                }
            }
        }
    }

    void expectExecution() {
        UnitOfWork captured = null
        Execution.ExecutionOutcome outcome = null

        def execution = Stub(Execution)
        execution.outcome >> { outcome }

        def result = Stub(ExecutionEngine.Result)
        result.execution >> Try.successful(execution)

        def request = Mock(ExecutionEngine.Request)
        request.execute() >> {
            def output = captured.execute(Stub(ExecutionContext))
            outcome = output.didWork == WorkOutput.WorkResult.DID_WORK
                ? Execution.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
                : Execution.ExecutionOutcome.UP_TO_DATE
            result
        }

        1 * executionEngine.createRequest(_) >> { UnitOfWork w ->
            captured = w
            request
        }
        1 * request.withValidationContext(node.validationContext)
    }

    void expectFailure(Throwable failure) {
        def request = Mock(ExecutionEngine.Request)
        1 * executionEngine.createRequest(_) >> request
        1 * request.execute() >> Stub(ExecutionEngine.Result) {
            getExecution() >> Try.failure(failure)
        }
    }

}
