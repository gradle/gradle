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

import org.gradle.tooling.internal.protocol.ConnectionVersion4
import spock.lang.Specification
import org.gradle.listener.ListenerManager
import org.gradle.logging.ProgressLoggerFactory

class ConnectionFactoryTest extends Specification {
    final ToolingImplementationLoader implementationLoader = Mock()
    final ListenerManager listenerManager = Mock()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final Distribution distribution = Mock()
    final ConnectionVersion4 connectionImpl = Mock()
    final ConnectionParameters parameters = Mock()
    final ConnectionFactory factory = new ConnectionFactory(implementationLoader, listenerManager, progressLoggerFactory)

    def usesImplementationLoaderToLoadConnectionFactory() {
        when:
        def result = factory.create(distribution, parameters)

        then:
        result instanceof DefaultProjectConnection
        result.connection instanceof DefaultAsyncConnection
        result.connection.connection instanceof ProgressLoggingConnection
        result.connection.connection.connection instanceof LazyConnection
        0 * _._
    }
}
