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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.ConfigurableConnection
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalCancellableConnection
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection
import org.gradle.tooling.internal.consumer.TestExecutionRequest
import spock.lang.Specification

class TestExecutionConsumerConnectionTest extends Specification {

    def "calls delegate connection"() {
        given:
        TestConnection delegate = newDelegateConnection()
        TestExecutionConsumerConnection connection = new TestExecutionConsumerConnection(delegate, Mock(ModelMapping), Mock(ProtocolToModelAdapter))
        TestExecutionRequest testExecutionRequest = Mock(TestExecutionRequest)
        ConsumerOperationParameters operationParameters = Mock()
        when:
        connection.runTests(testExecutionRequest, operationParameters)

        then:
        1 * delegate.runTests(testExecutionRequest, _, operationParameters)
    }

    private TestConnection newDelegateConnection() {
        TestConnection delegate = Mock(TestConnection)
        ConnectionMetaDataVersion1 connectionMetaData = Mock()
        1 * delegate.getMetaData() >> connectionMetaData
        1 * connectionMetaData.getVersion() >> "2.6-rc-1"
        delegate
    }

    interface TestConnection extends InternalTestExecutionConnection, ConnectionVersion4, InternalCancellableConnection, ConfigurableConnection {}
}

