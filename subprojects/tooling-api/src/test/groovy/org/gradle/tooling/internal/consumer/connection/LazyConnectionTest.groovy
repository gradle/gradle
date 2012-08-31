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

import org.gradle.logging.ProgressLoggerFactory
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.LoggingProvider
import org.gradle.tooling.internal.consumer.ModelProvider
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import org.gradle.tooling.internal.consumer.parameters.ConsumerConnectionParameters
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import spock.lang.Specification

class LazyConnectionTest extends Specification {
    final Distribution distribution = Mock()
    final ToolingImplementationLoader implementationLoader = Mock()
    final ConsumerOperationParameters params = Mock()
    final ConsumerConnectionParameters connectionParams = Mock()
    final ConsumerConnection consumerConnection = Mock()
    final LoggingProvider loggingProvider = Mock()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final LazyConnection connection = new LazyConnection(distribution, implementationLoader, loggingProvider, false)

    static class SomeModel {}

    def setup() {
        connection.connectionParameters = connectionParams
        connection.modelProvider = Mock(ModelProvider)
    }

    def createsConnectionOnDemandToBuildModel() {
        when:
        connection.run(SomeModel, params)

        then:
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory, connectionParams) >> consumerConnection
        1 * connection.modelProvider.provide(!null, SomeModel, params)
        0 * _._
    }

    def reusesConnection() {
        when:
        connection.run(SomeModel, params)
        connection.run(String, params)

        then:
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory, connectionParams) >> consumerConnection
        1 * connection.modelProvider.provide(consumerConnection, SomeModel, params)
        1 * connection.modelProvider.provide(consumerConnection, String, params)
        0 * _._
    }

    def stopsConnectionOnStop() {
        when:
        connection.run(SomeModel, params)
        connection.stop()

        then:
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory, connectionParams) >> consumerConnection
        1 * connection.modelProvider.provide(consumerConnection, SomeModel, params)
        1 * consumerConnection.stop()
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
        connection.run(SomeModel, params)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory, connectionParams) >> { throw failure }

        when:
        connection.stop()

        then:
        0 * _._
    }
}
