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

package org.gradle.tooling.internal.provider

import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.invocation.BuildParameters
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction
import org.gradle.tooling.internal.provider.test.ProviderInternalTestExecutionRequest
import spock.lang.Specification

class TestExecutionRequestActionTest extends Specification {

    BuildParameters buildParameters = Stub()
    BuildEventSubscriptions buildClientSubscriptions = Mock()
    ProviderInternalTestExecutionRequest executionRequest = Mock()

    def "maps testClasses to internalJvmTestRequests if empty"(){
        given:
        1 * executionRequest.getTestExecutionDescriptors() >> []
        1 * executionRequest.getInternalJvmTestRequests(_) >> []
        1 * executionRequest.getTaskAndTests(_) >> [:]
        1 * executionRequest.getTestClassNames() >> ["org.acme.Foo"]
        1 * executionRequest.getTaskSpecs(_) >> []
        1 * executionRequest.isRunDefaultTasks(_) >> false

        when:
        def classNames = executionRequest.getTestClassNames() as Set
        def executionRequestAction = new TestExecutionRequestAction(
            buildClientSubscriptions,
            buildParameters,
            executionRequest.getTestExecutionDescriptors() as Set,
            classNames,
            TestExecutionRequestAction.getInternalJvmTestRequests(executionRequest, classNames),
            TestExecutionRequestAction.getDebugOptions(executionRequest),
            TestExecutionRequestAction.getTaskAndTests(executionRequest),
            executionRequest.isRunDefaultTasks(false),
            executionRequest.getTaskSpecs([])
        )

        then:
        executionRequestAction.getTestClassNames() == ["org.acme.Foo"] as Set
        executionRequestAction.getInternalJvmTestRequests().collect { [clazz:it.className, method:it.methodName]} == [[clazz:"org.acme.Foo", method:null]]
    }
}
