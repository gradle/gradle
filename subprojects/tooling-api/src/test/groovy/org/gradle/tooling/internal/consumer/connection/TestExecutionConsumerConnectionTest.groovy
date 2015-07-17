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

import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.*
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest
import org.gradle.tooling.internal.consumer.TestExecutionRequest
import spock.lang.Specification

class TestExecutionConsumerConnectionTest extends Specification {

    def "calls delegate connection with converted InternalTestExecutionRequest"() {
        given:
        TestConnection delegate = newDelegateConnection()
        TestExecutionConsumerConnection connection = new TestExecutionConsumerConnection(delegate, Mock(ModelMapping), Mock(ProtocolToModelAdapter))

        when:
        TestExecutionRequest testExecutionRequest = testExecutionRequest(jvmTestDecriptor("SomeClass", "someMethod"))
        connection.runTests(testExecutionRequest, Mock(ConsumerOperationParameters))

        then:
        delegate.runTests(_, _, _) >> { arguments ->
            final InternalTestExecutionRequest internalTestExecutionRequest = arguments[0]
            assert internalTestExecutionRequest.testExecutionDescriptors
                .collect {[clazz: it.className, method:it.methodName, taskPath:it.taskPath]} == [[clazz:"SomeClass", method:"someMethod", taskPath: null]]
            Mock(BuildResult)
        }
    }

    def "fails for non JvmTestOperationDescriptor descriptors"() {
        given:
        TestConnection delegate = newDelegateConnection()
        TestExecutionConsumerConnection connection = new TestExecutionConsumerConnection(delegate, Mock(ModelMapping), Mock(ProtocolToModelAdapter))

        when:
        TestExecutionRequest testExecutionRequest = testExecutionRequest(Mock(NonJvmTestOperationDescriptor))
        connection.runTests(testExecutionRequest, Mock(ConsumerOperationParameters))

        then:
        def e = thrown(TestExecutionException)
        e.message == "Invalid TestOperationDescriptor implementation. Only JvmTestOperationDescriptor supported."

    }

    private JvmTestOperationDescriptor jvmTestDecriptor(String className, String methodName) {
        JvmTestOperationDescriptor jvmTestOperationDescriptor = Mock()
        _ * jvmTestOperationDescriptor.className >> className
        _ * jvmTestOperationDescriptor.methodName >> methodName
        jvmTestOperationDescriptor
    }

    private TestExecutionRequest testExecutionRequest(TestOperationDescriptor... descriptors) {
        TestExecutionRequest testExecutionRequest = Mock()
        testExecutionRequest.operationDescriptors >> Arrays.asList(descriptors)
        testExecutionRequest
    }

    private TestConnection newDelegateConnection() {
        TestConnection delegate = Mock(TestConnection)
        ConnectionMetaDataVersion1 connectionMetaData = Mock()
        1 * delegate.getMetaData() >> connectionMetaData
        1 * connectionMetaData.getVersion() >> "2.6-rc-1"
        delegate
    }

    interface TestConnection extends InternalTestExecutionConnection, ConnectionVersion4, InternalCancellableConnection, ConfigurableConnection {}
    interface NonJvmTestOperationDescriptor extends TestOperationDescriptor{}
}

