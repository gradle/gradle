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

import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.LoggingProvider
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.parameters.FailsafeBuildProgressListenerAdapter
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import spock.lang.Specification

class LazyConsumerActionExecutorTest extends Specification {
    final Distribution distribution = Mock()
    final ToolingImplementationLoader implementationLoader = Mock()
    final ConsumerOperationParameters params = Mock()
    final ConsumerAction<String> action = Mock()
    final ConnectionParameters connectionParams = Mock()
    final ConsumerConnection consumerConnection = Mock()
    final LoggingProvider loggingProvider = Mock()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final FailsafeBuildProgressListenerAdapter buildProgressListener = Mock()
    final BuildCancellationToken cancellationToken = Mock()

    final LazyConsumerActionExecutor connection = new LazyConsumerActionExecutor(distribution, implementationLoader, loggingProvider, connectionParams)

    def createsConnectionOnDemandToBuildModel() {
        when:
        connection.run(action)

        then:
        1 * loggingProvider.progressLoggerFactory >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory, _ as InternalBuildProgressListener, connectionParams, cancellationToken) >> consumerConnection
        _ * action.parameters >> params
        _ * params.cancellationToken >> cancellationToken
        _ * params.buildProgressListener >> buildProgressListener
        1 * action.run(consumerConnection)
        0 * _._
    }

    def reusesConnection() {
        def action2 = Mock(ConsumerAction)

        when:
        connection.run(action)
        connection.run(action2)

        then:
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory, _ as InternalBuildProgressListener, connectionParams, cancellationToken) >> consumerConnection
        _ * action.parameters >> params
        1 * params.cancellationToken >> cancellationToken
        1 * params.buildProgressListener >> buildProgressListener
        1 * action.run(consumerConnection)
        0 * _._

        then:
        _ * action2.parameters >> params
        _ * params.cancellationToken >> cancellationToken
        1 * params.buildProgressListener >> buildProgressListener
        1 * action2.run(consumerConnection)
        0 * _._
    }

    def doesNotStopConnectionOnStopIfNotCreated() {
        when:
        connection.stop()

        then:
        0 * _._
    }

    def doesNotStopConnectionOnStopIfConnectionCouldNotBeCreated() {
        def failure = new RuntimeException()

        when:
        connection.run(action)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory, _ as InternalBuildProgressListener, connectionParams, cancellationToken) >> { throw failure }
        _ * action.parameters >> params
        _ * params.cancellationToken >> cancellationToken
        _ * params.buildProgressListener >> buildProgressListener

        when:
        connection.stop()

        then:
        0 * _._
    }
}
