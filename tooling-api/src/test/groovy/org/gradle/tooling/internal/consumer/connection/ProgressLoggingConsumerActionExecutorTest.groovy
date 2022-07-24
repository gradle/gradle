/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.internal.event.ListenerManager
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.tooling.internal.consumer.LoggingProvider
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.parameters.FailsafeBuildProgressListenerAdapter
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1
import spock.lang.Specification

class ProgressLoggingConsumerActionExecutorTest extends Specification {
    final ConsumerActionExecutor target = Mock()
    final ConsumerAction<String> action = Mock()
    final ConsumerOperationParameters params = Mock()
    final ProgressListenerVersion1 listener = Mock()
    final FailsafeBuildProgressListenerAdapter buildListener = Mock()
    final ProgressLogger progressLogger = Mock()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final ListenerManager listenerManager = Mock()
    final LoggingProvider loggingProvider = Mock()
    final ProgressLoggingConsumerActionExecutor connection = new ProgressLoggingConsumerActionExecutor(target, loggingProvider)

    def notifiesProgressListenerOfStartAndEndOfFetchingModel() {
        when:
        connection.run(action)

        then:
        1 * loggingProvider.listenerManager >> listenerManager
        1 * loggingProvider.progressLoggerFactory >> progressLoggerFactory
        1 * listenerManager.addListener(!null)
        1 * progressLoggerFactory.newOperation(ProgressLoggingConsumerActionExecutor.class) >> progressLogger
        1 * progressLogger.setDescription('Build')
        1 * progressLogger.started()
        1 * target.run(action)
        1 * progressLogger.completed()
        1 * listenerManager.removeListener(!null)
        _ * params.progressListener >> listener
        _ * params.buildProgressListener >> buildListener
        _ * action.parameters >> params
        0 * _._
    }
}
