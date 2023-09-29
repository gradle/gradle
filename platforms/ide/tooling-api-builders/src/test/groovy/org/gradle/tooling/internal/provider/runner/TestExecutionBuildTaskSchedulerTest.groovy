/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner

import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestFilter
import org.gradle.execution.EntryTaskSelector
import org.gradle.execution.TaskSelection
import org.gradle.execution.TaskSelectionResult
import org.gradle.execution.plan.ExecutionPlan
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.internal.build.BuildProjectRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.event.types.DefaultTestDescriptor
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.tooling.internal.protocol.test.InternalDebugOptions
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction
import spock.lang.Specification

import java.util.function.Consumer

class TestExecutionBuildTaskSchedulerTest extends Specification {

    public static final String TEST_CLASS_NAME = "TestClass"
    public static final String TEST_METHOD_NAME = "testMethod"
    public static final String TEST_TASK_NAME = ":test"

    ProjectInternal projectInternal = Mock()
    ProjectState projectState = Mock()
    Test testTask
    TaskContainerInternal tasksContainerInternal
    TestFilter testFilter
    TaskOutputsInternal outputsInternal
    GradleInternal gradleInternal = Mock()
    BuildState buildState = Mock()
    BuildProjectRegistry buildProjectRegistry = Mock()
    ExecutionPlan executionPlan
    EntryTaskSelector.Context context
    TestExecutionRequestAction testExecutionRequest
    InternalDebugOptions debugOptions

    def setup() {
        outputsInternal = Mock()
        tasksContainerInternal = Mock()
        executionPlan = Mock()
        testExecutionRequest = Mock()
        testTask = Mock()
        testFilter = Mock()
        context = Stub()

        debugOptions = Mock()
        debugOptions.isDebugMode() >> false
        testExecutionRequest.getDebugOptions() >> debugOptions
        testExecutionRequest.getTaskSpecs() >> []

        setupProject()
        setupTestTask()
    }

    private void setupProject() {
        _ * gradleInternal.owner >> buildState
        _ * buildState.projects >> buildProjectRegistry
        _ * buildProjectRegistry.allProjects >> [projectState]
        _ * projectState.applyToMutableState(_) >> { Consumer consumer -> consumer.accept(projectInternal) }
        _ * context.gradle >> gradleInternal
    }

    def "empty test execution request configures no tasks"() {
        1 * testExecutionRequest.getTestExecutionDescriptors() >> []
        1 * testExecutionRequest.getInternalJvmTestRequests() >> []
        1 * testExecutionRequest.getTaskAndTests() >> [:]

        setup:
        def buildConfigurationAction = new TestExecutionBuildConfigurationAction(testExecutionRequest)
        when:
        buildConfigurationAction.applyTasksTo(context, executionPlan)
        then:
        0 * buildProjectRegistry.allProjects
        _ * executionPlan.addEntryTasks({ args -> assert args.size() == 0 })
        _ * executionPlan.addEntryTask({ args -> assert args.size() == 0 })
    }

    def "sets test filter with information from #requestType"() {
        setup:
        _ * buildProjectRegistry.allProjects >> [projectState]
        _ * testExecutionRequest.getTestExecutionDescriptors() >> descriptors
        _ * testExecutionRequest.getInternalJvmTestRequests() >> internalJvmRequests
        _ * testExecutionRequest.getTaskAndTests() >> tasksAndTests
        def executionPlanContents = Mock(QueryableExecutionPlan) {
            getTasks() >> [testTask]
        }

        def buildConfigurationAction = new TestExecutionBuildConfigurationAction(testExecutionRequest)
        when:
        buildConfigurationAction.applyTasksTo(context, executionPlan)
        buildConfigurationAction.postProcessExecutionPlan(context, executionPlanContents)

        then:
        1 * testFilter.includeTest(expectedClassFilter, expectedMethodFilter)

        (1..2) * testFilter.setFailOnNoMatchingTests(false)
        (1..2) * outputsInternal.upToDateWhen(Specs.SATISFIES_NONE)

        where:
        requestType        | descriptors        | internalJvmRequests                                 | expectedClassFilter | expectedMethodFilter | tasksAndTests
        "test descriptors" | [testDescriptor()] | []                                                  | TEST_CLASS_NAME     | TEST_METHOD_NAME     | [:]
        "test classes"     | []                 | [jvmTestRequest(TEST_CLASS_NAME, null)]             | TEST_CLASS_NAME     | null                 | [:]
        "test methods"     | []                 | [jvmTestRequest(TEST_CLASS_NAME, TEST_METHOD_NAME)] | TEST_CLASS_NAME     | TEST_METHOD_NAME     | [:]
        "test type"        | []                 | []                                                  | TEST_CLASS_NAME     | TEST_METHOD_NAME     | [':test': [jvmTestRequest(TEST_CLASS_NAME, TEST_METHOD_NAME)]]
    }

    InternalJvmTestRequest jvmTestRequest(String className, String methodName) {
        InternalJvmTestRequest jvmTestRequest = Mock()
        _ * jvmTestRequest.getClassName() >> className
        _ * jvmTestRequest.getMethodName() >> methodName
        jvmTestRequest
    }

    private void setupTestTask() {
        def taskSelectionResult = Mock(TaskSelectionResult)
        _ * projectInternal.getTasks() >> tasksContainerInternal
        _ * testTask.getFilter() >> testFilter
        _ * tasksContainerInternal.findByPath(TEST_TASK_NAME) >> testTask
        TaskCollection<Test> testTaskCollection = Mock()
        _ * testTaskCollection.iterator() >> [testTask].iterator()
        _ * tasksContainerInternal.withType(AbstractTestTask) >> testTaskCollection
        _ * testTask.getOutputs() >> outputsInternal
        _ * testTask.getPath() >> TEST_TASK_NAME
        _ * taskSelectionResult.collectTasks(_) >> { args ->
            Collection<? super Task> tasks = args[0]
            tasks.add(testTask)
        }
        _ * context.getSelection(TEST_TASK_NAME) >> new TaskSelection(null, null, taskSelectionResult)
    }

    private DefaultTestDescriptor testDescriptor() {
        new DefaultTestDescriptor(Stub(OperationIdentifier), "test1", "test 1", "ATOMIC", "test suite", TEST_CLASS_NAME, TEST_METHOD_NAME, null, TEST_TASK_NAME)
    }

}
