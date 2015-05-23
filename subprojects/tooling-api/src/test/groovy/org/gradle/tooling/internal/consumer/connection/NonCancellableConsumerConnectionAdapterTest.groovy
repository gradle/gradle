/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.logging.ConfigureLogging
import org.gradle.logging.TestOutputEventListener
import org.gradle.tooling.BuildAction
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.junit.Rule
import spock.lang.Specification

class NonCancellableConsumerConnectionAdapterTest extends Specification {
    final target = Mock(ConsumerConnection)
    final connection = new NonCancellableConsumerConnectionAdapter(target)
    final outputEventListener = new TestOutputEventListener()
    @Rule ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def "delegates to connection to run build action"() {
        def action = Mock(BuildAction)
        def parameters = Stub(ConsumerOperationParameters)

        when:
        def result = connection.run(action, parameters)

        then:
        result == 'result'

        and:
        1 * target.run(action, parameters) >> 'result'
    }

    def "logs when cancelled"() {
        def action = Mock(BuildAction)
        def cancellation = new DefaultBuildCancellationToken()
        def parameters = Stub(ConsumerOperationParameters) {
            getCancellationToken() >> cancellation
        }

        given:
        _ * target.run(action, parameters) >> {
            cancellation.cancel()
            'result'
        }

        when:
        def result = connection.run(action, parameters)

        then:
        result == 'result'

        and:
        cancellation.cancellationRequested
        outputEventListener.toString().contains('Note: Version of Gradle provider does not support cancellation.')
    }

    def "no logging when cancelled after action"() {
        def action = Mock(BuildAction)
        def cancellation = new DefaultBuildCancellationToken()
        def parameters = Stub(ConsumerOperationParameters) {
            getCancellationToken() >> cancellation
        }

        when:
        def result = connection.run(action, parameters)
        cancellation.cancel()

        then:
        result == 'result'

        and:
        1 * target.run(action, parameters) >> 'result'
        cancellation.cancellationRequested
        outputEventListener.toString().empty
    }
}
