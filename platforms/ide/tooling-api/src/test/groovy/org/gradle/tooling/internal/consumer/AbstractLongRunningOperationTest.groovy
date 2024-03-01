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

package org.gradle.tooling.internal.consumer

import spock.lang.Specification

class AbstractLongRunningOperationTest extends Specification {
    def op = new TestOperation(Stub(ConnectionParameters))

    def "null or empty arguments have the same meaning"() {
        when:
        op.withArguments(null as List)

        then:
        op.consumerOperationParameters.arguments == null

        when:
        op.withArguments([])

        then:
        op.consumerOperationParameters.arguments == null

        when:
        op.withArguments()

        then:
        op.consumerOperationParameters.arguments == null

        when:
        op.withArguments('-Dfoo')

        then:
        op.consumerOperationParameters.arguments == ['-Dfoo']

        when:
        op.withArguments(['-Dfoo'])

        then:
        op.consumerOperationParameters.arguments == ['-Dfoo']
    }

    def "null or empty jvm arguments have the same meaning"() {
        when:
        op.jvmArguments = null as List

        then:
        op.consumerOperationParameters.jvmArguments == null

        when:
        op.jvmArguments = null as String[]

        then:
        op.consumerOperationParameters.jvmArguments == null

        when:
        op.jvmArguments = []

        then:
        op.consumerOperationParameters.jvmArguments == null

        when:
        op.setJvmArguments()

        then:
        op.consumerOperationParameters.jvmArguments == null

        when:
        op.jvmArguments = ['-Xmx']

        then:
        op.consumerOperationParameters.jvmArguments == ['-Xmx']

        when:
        op.setJvmArguments('-Xmx')

        then:
        op.consumerOperationParameters.jvmArguments == ['-Xmx']
    }

    class TestOperation extends AbstractLongRunningOperation<TestOperation> {
        protected TestOperation(ConnectionParameters parameters) {
            super(parameters)
            operationParamsBuilder.entryPoint = "test"
        }

        @Override
        protected TestOperation getThis() {
            return this
        }
    }
}
