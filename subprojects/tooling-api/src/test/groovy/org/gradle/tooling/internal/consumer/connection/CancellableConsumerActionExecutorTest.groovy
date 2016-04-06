/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.initialization.BuildCancellationToken
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import spock.lang.Specification

class CancellableConsumerActionExecutorTest extends Specification {
    final ConsumerOperationParameters params = Mock()
    final ConsumerAction<String> action = Mock()
    final BuildCancellationToken cancellationToken = Mock()
    final ConsumerActionExecutor delegate = Mock()
    final CancellableConsumerActionExecutor connection = new CancellableConsumerActionExecutor(delegate)

    def "runs action when not cancelled"() {
        when:
        connection.run(action)

        then:
        _ * cancellationToken.cancellationRequested >> false
        _ * action.parameters >> params
        _ * params.cancellationToken >> cancellationToken
        1 * delegate.run(action)
        0 * _._
    }

    def doesNotInvokeActionRunWhenCancellationRequested() {
        when:
        connection.run(action)

        then:
        _ * cancellationToken.cancellationRequested >> true
        _ * action.parameters >> params
        _ * params.cancellationToken >> cancellationToken
        0 * _._

        and:
        BuildCancelledException e = thrown()
    }
}
