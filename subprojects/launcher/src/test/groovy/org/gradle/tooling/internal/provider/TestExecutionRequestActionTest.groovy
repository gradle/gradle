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

import org.gradle.api.internal.StartParameterInternal
import org.gradle.tooling.internal.provider.test.ProviderInternalTestExecutionRequest
import spock.lang.Specification

class TestExecutionRequestActionTest extends Specification {

    StartParameterInternal startParameter = Mock()
    BuildClientSubscriptions buildClientSubscriptions= Mock()
    ProviderInternalTestExecutionRequest executionRequest = Mock()

    def "maps testClasses to internalJvmTestRequests if empty"(){
        given:
        consumerRequestWithClassNamesOnly();

        when:
        def executionRequestAction = TestExecutionRequestAction.create(buildClientSubscriptions, startParameter, executionRequest);
        then:
        executionRequestAction.getTestClassNames() == ["org.acme.Foo"] as Set
        executionRequestAction.getInternalJvmTestRequests().collect { [clazz:it.className, method:it.methodName]} == [[clazz:"org.acme.Foo", method:null]]

    }

    def consumerRequestWithClassNamesOnly() {
        1 * executionRequest.getTestExecutionDescriptors() >> []
        1 * executionRequest.getInternalJvmTestRequests(_) >> []
        1 * executionRequest.getTestClassNames() >> ["org.acme.Foo"]
        executionRequest
    }
}
