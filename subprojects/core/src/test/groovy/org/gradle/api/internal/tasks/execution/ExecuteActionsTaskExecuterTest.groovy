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

import com.google.common.collect.ImmutableSortedMap
import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.execution.TaskActionListener
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsEnterpriseInternal
import org.gradle.api.internal.changedetection.changes.DefaultTaskExecutionMode
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.internal.tasks.properties.TaskProperties
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.execution.FileCollectionFingerprinterRegistry
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.WorkInputListeners
import org.gradle.internal.execution.WorkValidationContext
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.execution.history.OutputsCleaner
import org.gradle.internal.execution.history.OverlappingOutputDetector
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChangeDetector
import org.gradle.internal.execution.impl.DefaultExecutionEngine
import org.gradle.internal.execution.impl.DefaultInputFingerprinter
import org.gradle.internal.execution.impl.DefaultOutputSnapshotter
import org.gradle.internal.execution.impl.DefaultWorkValidationContext
import org.gradle.internal.execution.steps.AssignWorkspaceStep
import org.gradle.internal.execution.steps.CancelExecutionStep
import org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep
import org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep
import org.gradle.internal.execution.steps.ExecuteStep
import org.gradle.internal.execution.steps.IdentifyStep
import org.gradle.internal.execution.steps.IdentityCacheStep
import org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep
import org.gradle.internal.execution.steps.RemovePreviousOutputsStep
import org.gradle.internal.execution.steps.ResolveCachingStateStep
import org.gradle.internal.execution.steps.ResolveChangesStep
import org.gradle.internal.execution.steps.ResolveInputChangesStep
import org.gradle.internal.execution.steps.SkipEmptyWorkStep
import org.gradle.internal.execution.steps.SkipUpToDateStep
import org.gradle.internal.execution.steps.ValidateStep
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.file.ReservedFileSystemLocationRegistry
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.id.UniqueId
import org.gradle.internal.logging.StandardOutputCapture
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.snapshot.impl.ClassImplementationSnapshot
import org.gradle.internal.snapshot.impl.DefaultValueSnapshotter
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import org.gradle.internal.work.AsyncWorkTracker
import spock.lang.Specification

import java.util.function.Supplier

import static java.util.Collections.emptyList
import static org.gradle.api.internal.file.TestFiles.deleter
import static org.gradle.api.internal.file.TestFiles.fileCollectionFactory
import static org.gradle.api.internal.file.TestFiles.fileSystem
import static org.gradle.api.internal.file.TestFiles.fileSystemAccess
import static org.gradle.api.internal.file.TestFiles.virtualFileSystem
import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_AND_REACQUIRE_PROJECT_LOCKS
import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_PROJECT_LOCKS

class ExecuteActionsTaskExecuterTest extends Specification {
    private final DocumentationRegistry documentationRegistry = new DocumentationRegistry()
    def task = Mock(TaskInternal)
    def taskOutputs = Mock(TaskOutputsEnterpriseInternal)
    def action1 = Mock(InputChangesAwareTaskAction) {
        getActionImplementation(_ as ClassLoaderHierarchyHasher) >> ImplementationSnapshot.of("Action1", TestHashCodes.hashCodeFrom(1234))
    }
    def action2 = Mock(InputChangesAwareTaskAction) {
        getActionImplementation(_ as ClassLoaderHierarchyHasher) >> ImplementationSnapshot.of("Action2", TestHashCodes.hashCodeFrom(1234))
    }
    def state = new TaskStateInternal()
    def taskProperties = Stub(TaskProperties) {
        getInputFileProperties() >> ImmutableSortedSet.of()
        getOutputFileProperties() >> ImmutableSortedSet.of()
    }
    def previousState = Stub(PreviousExecutionState) {
        getInputProperties() >> ImmutableSortedMap.of()
        getInputFileProperties() >> ImmutableSortedMap.of()
        getImplementation() >> Stub(ClassImplementationSnapshot)

        getOutputFilesProducedByWork() >> ImmutableSortedMap.of()
    }
    def validationContext = new DefaultWorkValidationContext(documentationRegistry, WorkValidationContext.TypeOriginInspector.NO_OP)
    def executionContext = Mock(TaskExecutionContext)
    def scriptSource = Mock(ScriptSource)
    def standardOutputCapture = Mock(StandardOutputCapture)
    def buildOperationExecutorForTaskExecution = Mock(BuildOperationExecutor)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def asyncWorkTracker = Mock(AsyncWorkTracker)

    def virtualFileSystem = virtualFileSystem()
    def fileSystemAccess = fileSystemAccess(virtualFileSystem)
    def fileCollectionSnapshotter = new DefaultFileCollectionSnapshotter(fileSystemAccess, fileSystem())
    def outputSnapshotter = new DefaultOutputSnapshotter(fileCollectionSnapshotter)
    def fingerprinter = new AbsolutePathFileCollectionFingerprinter(DirectorySensitivity.DEFAULT, fileCollectionSnapshotter, FileSystemLocationSnapshotHasher.DEFAULT)
    def fingerprinterRegistry = Stub(FileCollectionFingerprinterRegistry) {
        getFingerprinter(_) >> fingerprinter
    }
    def executionHistoryStore = Mock(ExecutionHistoryStore)
    def buildId = UniqueId.generate()

    def actionListener = Stub(TaskActionListener)
    def outputChangeListener = Stub(OutputChangeListener)
    def inputListeners = Stub(WorkInputListeners)
    def cancellationToken = new DefaultBuildCancellationToken()
    def changeDetector = new DefaultExecutionStateChangeDetector()
    def taskCacheabilityResolver = Mock(TaskCacheabilityResolver)
    def buildCacheController = Stub(BuildCacheController)
    def listenerManager = Stub(ListenerManager)
    def classloaderHierarchyHasher = new ClassLoaderHierarchyHasher() {
        @Override
        HashCode getClassLoaderHash(ClassLoader classLoader) {
            return TestHashCodes.hashCodeFrom(1234)
        }
    }
    def valueSnapshotter = new DefaultValueSnapshotter([], classloaderHierarchyHasher)
    def inputFingerprinter = new DefaultInputFingerprinter(fileCollectionSnapshotter, fingerprinterRegistry, valueSnapshotter)
    def reservedFileSystemLocationRegistry = Stub(ReservedFileSystemLocationRegistry)
    def overlappingOutputDetector = Stub(OverlappingOutputDetector)
    def fileCollectionFactory = fileCollectionFactory()
    def deleter = deleter()
    def validationWarningReporter = Stub(ValidateStep.ValidationWarningRecorder)
    def buildOutputCleanupRegistry = Stub(BuildOutputCleanupRegistry)
    def outputsCleanerFactory = { new OutputsCleaner(deleter, buildOutputCleanupRegistry.&isOutputOwnedByBuild, buildOutputCleanupRegistry.&isOutputOwnedByBuild) } as Supplier<OutputsCleaner>

    // @formatter:off
    def executionEngine = new DefaultExecutionEngine(documentationRegistry,
        new IdentifyStep<>(buildOperationExecutor,
        new IdentityCacheStep<>(
        new AssignWorkspaceStep<>(
        new LoadPreviousExecutionStateStep<>(
        new SkipEmptyWorkStep(outputChangeListener, inputListeners, outputsCleanerFactory,
        new CaptureStateBeforeExecutionStep<>(buildOperationExecutor, classloaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector,
        new ValidateStep<>(virtualFileSystem, validationWarningReporter, new DefaultProblems(Mock(BuildOperationProgressEventEmitter)),
        new ResolveCachingStateStep<>(buildCacheController, false,
        new ResolveChangesStep<>(changeDetector,
        new SkipUpToDateStep<>(
        new ResolveInputChangesStep<>(
        new CaptureStateAfterExecutionStep<>(buildOperationExecutor, buildId, outputSnapshotter, outputChangeListener,
        new CancelExecutionStep<>(cancellationToken,
        new RemovePreviousOutputsStep<>(deleter, outputChangeListener,
        new ExecuteStep<>(buildOperationExecutor
    ))))))))))))))))
    // @formatter:on

    def executer = new ExecuteActionsTaskExecuter(
        ExecuteActionsTaskExecuter.BuildCacheState.DISABLED,
        ExecuteActionsTaskExecuter.ScanPluginState.NOT_APPLIED,
        executionHistoryStore,
        buildOperationExecutorForTaskExecution,
        asyncWorkTracker,
        actionListener,
        taskCacheabilityResolver,
        classloaderHierarchyHasher,
        executionEngine,
        inputFingerprinter,
        listenerManager,
        reservedFileSystemLocationRegistry,
        fileCollectionFactory,
        TestFiles.taskDependencyFactory(),
        Stub(PathToFileResolver)
    )

    def setup() {
        ProjectInternal project = Mock(ProjectInternal)
        task.getProject() >> project
        task.getState() >> state
        task.getOutputs() >> taskOutputs
        task.getPath() >> "task"
        taskOutputs.setPreviousOutputFiles(_ as FileCollection)
        project.getBuildScriptSource() >> scriptSource
        task.getStandardOutputCapture() >> standardOutputCapture
        executionContext.getTaskExecutionMode() >> DefaultTaskExecutionMode.incremental()
        executionContext.getTaskProperties() >> taskProperties
        executionContext.getValidationContext() >> validationContext
        executionContext.getValidationAction() >> { { c -> } as TaskExecutionContext.ValidationAction }
        executionHistoryStore.load("task") >> Optional.of(previousState)
        taskProperties.getOutputFileProperties() >> ImmutableSortedSet.of()
    }

    void noMoreInteractions() {
        interaction {
            0 * action1._
            0 * action2._
            0 * executionContext._
            0 * standardOutputCapture._
        }
    }

    def doesNothingWhenTaskHasNoActions() {
        given:
        task.getTaskActions() >> emptyList()
        task.hasTaskActions() >> false

        when:
        executer.execute(task, state, executionContext)

        then:
        noMoreInteractions()

        state.outcome == TaskExecutionOutcome.UP_TO_DATE
        !state.didWork
        !state.executing
        state.actionable
    }

    def executesEachActionInOrder() {
        given:
        task.getTaskActions() >> [action1, action2]
        task.hasTaskActions() >> true

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
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
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
        then:
        1 * action2.execute(task)
        then:
        1 * action2.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_PROJECT_LOCKS)
        then:
        1 * standardOutputCapture.stop()
        then:
        noMoreInteractions()

        !state.executing
        state.didWork
        state.outcome == TaskExecutionOutcome.EXECUTED
        !state.failure
        state.actionable
    }

    def executeDoesOperateOnNewActionListInstance() {
        given:
        interaction {
            task.getActions() >> [action1]
            task.getTaskActions() >> [action1]
            task.hasTaskActions() >> true
        }

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * standardOutputCapture.start()

        then:
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
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
        then:
        noMoreInteractions()
    }

    def stopsAtFirstActionWhichThrowsException() {
        given:
        task.getTaskActions() >> [action1, action2]
        task.hasTaskActions() >> true
        def failure = new RuntimeException("failure")
        action1.execute(task) >> {
            throw failure
        }

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
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

        TaskExecutionException wrappedFailure = (TaskExecutionException) state.failure
        wrappedFailure instanceof TaskExecutionException
        wrappedFailure.task == task
        wrappedFailure.message.startsWith("Execution failed for ")
        wrappedFailure.cause.is(failure)
    }

    def stopsAtFirstActionWhichThrowsStopExecutionException() {
        given:
        task.getTaskActions() >> [action1, action2]
        task.hasTaskActions() >> true

        when:
        executer.execute(task, state, executionContext)

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
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
        then:
        1 * standardOutputCapture.stop()
        state.didWork
        !state.executing
        state.outcome == TaskExecutionOutcome.EXECUTED
        !state.failure
        noMoreInteractions()
    }

    def skipsActionWhichThrowsStopActionException() {
        given:
        task.getTaskActions() >> [action1, action2]
        task.hasTaskActions() >> true

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
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
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
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

        noMoreInteractions()
    }

    def "captures exceptions from async work"() {
        given:
        task.getTaskActions() >> [action1, action2]
        task.hasTaskActions() >> true

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
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

        TaskExecutionException wrappedFailure = (TaskExecutionException) state.failure
        wrappedFailure instanceof TaskExecutionException
        wrappedFailure.task == task
        wrappedFailure.message.startsWith("Execution failed for ")
        wrappedFailure.cause instanceof MultiCauseException
        wrappedFailure.cause.causes.size() == 2
        wrappedFailure.cause.causes.any { it.message == "failure 1" }
        wrappedFailure.cause.causes.any { it.message == "failure 2" }
    }

    def "captures exceptions from both task action and async work"() {
        given:
        task.getTaskActions() >> [action1, action2]
        task.hasTaskActions() >> true
        action1.execute(task) >> {
            throw new RuntimeException("failure from task action")
        }

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
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

        TaskExecutionException wrappedFailure = (TaskExecutionException) state.failure
        wrappedFailure instanceof TaskExecutionException
        wrappedFailure.task == task
        wrappedFailure.message.startsWith("Execution failed for ")
        wrappedFailure.cause instanceof MultiCauseException
        wrappedFailure.cause.causes.size() == 3
        wrappedFailure.cause.causes.any { it.message == "failure 1" }
        wrappedFailure.cause.causes.any { it.message == "failure 2" }
        wrappedFailure.cause.causes.any { it.message == "failure from task action" }
    }

    def "a single exception from async work is not wrapped in a multicause exception"() {
        given:
        task.getTaskActions() >> [action1, action2]
        task.hasTaskActions() >> true
        def failure = new RuntimeException("failure 1")

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * standardOutputCapture.start()
        then:
        1 * buildOperationExecutorForTaskExecution.run(_ as RunnableBuildOperation) >> { args -> args[0].run(Stub(BuildOperationContext)) }
        then:
        1 * action1.clearInputChanges()
        then:
        1 * asyncWorkTracker.waitForCompletion(_, RELEASE_AND_REACQUIRE_PROJECT_LOCKS) >> {
            throw new DefaultMultiCauseException("mock failures", failure)
        }
        then:
        1 * standardOutputCapture.stop()

        !state.executing
        state.didWork
        state.outcome == TaskExecutionOutcome.EXECUTED

        TaskExecutionException wrappedFailure = (TaskExecutionException) state.failure
        wrappedFailure instanceof TaskExecutionException
        wrappedFailure.task == task
        wrappedFailure.message.startsWith("Execution failed for ")
        wrappedFailure.cause.is(failure)
    }
}
