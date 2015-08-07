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
import org.gradle.execution.BuildExecutionContext
import org.gradle.execution.TaskGraphExecuter
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequestVersion2
import spock.lang.Specification

class TestExecutionBuildConfigurationActionTest extends Specification {

    def "configures taskgraph"() {
        GradleInternal gradleInternal = Mock()
        BuildExecutionContext buildContext = Mock()
        TaskGraphExecuter taskGraphExecuter = Mock()
        InternalTestExecutionRequestVersion2 testExecutionRequest = Mock()
        1 * testExecutionRequest.getTestExecutionDescriptors() >> []
        1 * testExecutionRequest.getTestClassNames() >> []
        1 * testExecutionRequest.getTestMethods() >> []
        setup:
        def buildConfigurationAction = new TestExecutionBuildConfigurationAction(testExecutionRequest, gradleInternal);
        when:
        buildConfigurationAction.configure(buildContext)
        then:
        1 * gradleInternal.getTaskGraph() >> taskGraphExecuter
        1 * taskGraphExecuter.addTasks(_)
    }
}
