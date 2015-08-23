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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestFilter
import org.gradle.execution.BuildExecutionContext
import org.gradle.execution.TaskGraphExecuter
import org.gradle.tooling.internal.protocol.test.InternalTestMethod
import org.gradle.tooling.internal.provider.ProviderInternalTestExecutionRequest
import org.gradle.tooling.internal.provider.events.DefaultTestDescriptor
import spock.lang.Specification
import spock.lang.Unroll

class TestExecutionBuildConfigurationActionTest extends Specification {

    public static final String TEST_CLASS_NAME = "TestClass"
    public static final String TEST_METHOD_NAME = "testMethod"
    public static final String TEST_TASK_NAME = ":test"

    ProjectInternal projectInternal
    Test testTask
    TaskContainerInternal tasksContainerInternal
    TestFilter testFilter
    TaskOutputsInternal outputsInternal
    GradleInternal gradleInternal
    BuildExecutionContext buildContext
    TaskGraphExecuter taskGraphExecuter
    ProviderInternalTestExecutionRequest testExecutionRequest

    def setup() {
        outputsInternal = Mock()
        projectInternal = Mock()
        gradleInternal = Mock()
        buildContext = Mock()
        tasksContainerInternal = Mock()
        taskGraphExecuter = Mock()
        testExecutionRequest = Mock()
        testTask = Mock()
        testFilter = Mock()

        1 * gradleInternal.getTaskGraph() >> taskGraphExecuter
        _ * projectInternal.getAllprojects() >> [projectInternal]
    }

    def "configures taskgraph"() {
        emptyTestRequest()
        setup:
        def buildConfigurationAction = new TestExecutionBuildConfigurationAction(testExecutionRequest, gradleInternal);
        when:
        buildConfigurationAction.configure(buildContext)
        then:
        1 * taskGraphExecuter.addTasks(_)
    }

    @Unroll
    def "sets test filter with information from #requestType"() {
        setup:
        testTask()
        _ * testExecutionRequest.getTestExecutionDescriptors() >> descriptors
        _ * testExecutionRequest.getExplicitRequestedTestClassNames(_) >> testclasses
        _ * testExecutionRequest.getTestMethods(_) >> testMethods
        def buildConfigurationAction = new TestExecutionBuildConfigurationAction(testExecutionRequest, gradleInternal);
        when:
        buildConfigurationAction.configure(buildContext)
        then:
        1 * testFilter.includeTest(expectedClassFilter, expectedMethodFilter)

        1 * testTask.setIgnoreFailures(true)
        1 * testFilter.setFailOnNoMatchingTests(false)
        1 * outputsInternal.upToDateWhen(Specs.SATISFIES_NONE)
        where:
        requestType        | descriptors        | testclasses       | testMethods    | expectedClassFilter | expectedMethodFilter
        "test descriptors" | [testDescriptor()] | []                | []             | TEST_CLASS_NAME     | TEST_METHOD_NAME
        "test classes"     | []                 | [TEST_CLASS_NAME] | []             | TEST_CLASS_NAME     | null
        "test methods"     | []                 | []                | [testMethod()] | TEST_CLASS_NAME     | TEST_METHOD_NAME
    }

    def testMethod() {
        InternalTestMethod testMethod = Mock()
        _ * testMethod.getClassName() >> TEST_CLASS_NAME
        _ * testMethod.getMethodName() >> TEST_METHOD_NAME
        testMethod
    }

    private void emptyTestRequest() {
        1 * testExecutionRequest.getTestExecutionDescriptors() >> []
        1 * testExecutionRequest.getExplicitRequestedTestClassNames(_) >> []
        1 * testExecutionRequest.getTestMethods(_) >> []
    }

    private void testTask() {
        _ * buildContext.getGradle() >> gradleInternal
        _ * projectInternal.getTasks() >> tasksContainerInternal
        _ * testTask.getFilter() >> testFilter
        _ * tasksContainerInternal.findByPath(TEST_TASK_NAME) >> testTask
        TaskCollection<Test> testTaskCollection = Mock()
        _ * testTaskCollection.iterator() >> [testTask].iterator()
        _ * testTaskCollection.toArray() >> [testTask].toArray()
        _ * tasksContainerInternal.withType(Test) >> testTaskCollection
        _ * testTask.getOutputs() >> outputsInternal
        _ * gradleInternal.getRootProject() >> projectInternal
    }

    private DefaultTestDescriptor testDescriptor() {
        new DefaultTestDescriptor(1, "test1", "test 1", "ATOMIC", "test suite", TEST_CLASS_NAME, TEST_METHOD_NAME, 0, TEST_TASK_NAME)
    }

}
