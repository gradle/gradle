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
import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.service.ServiceRegistry
import org.gradle.tooling.internal.protocol.test.InternalJvmTestExecutionDescriptor
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest
import org.gradle.tooling.internal.provider.PayloadSerializer
import org.gradle.tooling.internal.provider.TestExecutionRequestAction
import spock.lang.Specification

class TestExecutionRequestActionRunnerTest extends Specification {

    def "does not handle non TestExecutionRequestAction"(){
        given:
        def runner = new TestExecutionRequestActionRunner()
        BuildAction buildAction = Mock(BuildAction)
        BuildController buildController= Mock(BuildController)
        when:
        runner.run(buildAction, buildController)
        then:
        0 * buildController._
        0 * buildAction._
    }

    def "configures tasks to run from passed TestExecutionDescriptors"(){
        given:
        def runner = new TestExecutionRequestActionRunner()

        TestExecutionRequestAction testExecutionRequestAction = Mock(TestExecutionRequestAction)
        BuildController buildController= Mock(BuildController)

        GradleInternal gradleInternal = newGradleInternal()
        InternalTestExecutionRequest testExecutionRequest = Mock()

        1 * buildController.gradle >> gradleInternal
        StartParameter startParameter = new StartParameter();
        1 * testExecutionRequestAction.startParameter >> startParameter
        1 * testExecutionRequestAction.testExecutionRequest >> testExecutionRequest
        1 * testExecutionRequest.testExecutionDescriptors >> [testExecutionDescriptor("testTask", "TestClass", "testMethod")]

        when:
        runner.run(testExecutionRequestAction, buildController)
        then:
        startParameter.taskNames == ["testTask"]
        1 * buildController.run()
        1 * buildController.setResult(_)
    }

    private GradleInternal newGradleInternal() {
        GradleInternal gradleInternal = Mock(GradleInternal)
        ServiceRegistry serviceRegistry = Mock()
        _ * gradleInternal.getServices() >> serviceRegistry
        PayloadSerializer payloadSerializer = Mock()
        1 * serviceRegistry.get(PayloadSerializer.class) >> payloadSerializer
        gradleInternal
    }

    private InternalJvmTestExecutionDescriptor testExecutionDescriptor(String taskPath, String className, String methodName) {
        InternalJvmTestExecutionDescriptor testExecutionDescriptor = Mock(InternalJvmTestExecutionDescriptor)
        _ * testExecutionDescriptor.taskPath >> taskPath
        _ * testExecutionDescriptor.className >> className
        _ * testExecutionDescriptor.methodName >> methodName
        testExecutionDescriptor
    }
}
