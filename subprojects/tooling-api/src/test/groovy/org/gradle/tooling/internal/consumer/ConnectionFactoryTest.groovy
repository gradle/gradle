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
package org.gradle.tooling.internal.consumer

import org.gradle.listener.ListenerManager
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.tooling.internal.consumer.async.DefaultAsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.LazyConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.LoggingInitializerConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ProgressLoggingConsumerActionExecutor
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import spock.lang.Specification

class ConnectionFactoryTest extends Specification {
    final ToolingImplementationLoader implementationLoader = Mock()
    final ListenerManager listenerManager = Mock()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final Distribution distribution = Mock()
    final ConnectionParameters parameters = new DefaultConnectionParameters()
    final ConnectionFactory factory = new ConnectionFactory(implementationLoader)

    def usesImplementationLoaderToLoadConnectionFactory() {
        when:
        def result = factory.create(distribution, parameters)

        then:
        result instanceof DefaultProjectConnection
        result.connection instanceof DefaultAsyncConsumerActionExecutor
        result.connection.actionExecutor instanceof LoggingInitializerConsumerActionExecutor
        result.connection.actionExecutor.actionExecutor instanceof ProgressLoggingConsumerActionExecutor
        result.connection.actionExecutor.actionExecutor.actionExecutor instanceof LazyConsumerActionExecutor
        _ * distribution.displayName >> "[some distribution]"
        0 * _._
    }
}
