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

import org.gradle.logging.ProgressLoggerFactory
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1
import org.gradle.tooling.internal.protocol.BuildParametersVersion1
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.ProjectVersion3
import spock.lang.Specification

class LazyConnectionTest extends Specification {
    final Distribution distribution = Mock()
    final ToolingImplementationLoader implementationLoader = Mock()
    final BuildParametersVersion1 buildParams = Mock()
    final BuildOperationParametersVersion1 params = Mock()
    final ConnectionVersion4 connectionImpl = Mock()
    final LoggingProvider loggingProvider = Mock()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final LazyConnection connection = new LazyConnection(distribution, implementationLoader, loggingProvider)

    def setup() {
        connection.modelProvider = Mock(ModelProvider)
    }

    def createsConnectionOnDemandToExecuteBuild() {
        when:
        connection.executeBuild(buildParams, params)

        then:
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory) >> connectionImpl
        1 * connectionImpl.executeBuild(buildParams, params)
        0 * _._
    }

    def createsConnectionOnDemandToBuildModel() {
        when:
        connection.getModel(ProjectVersion3, params)

        then:
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory) >> connectionImpl
        1 * connection.modelProvider.provide(connectionImpl, ProjectVersion3, params)
        0 * _._
    }

    def reusesConnection() {
        when:
        connection.getModel(ProjectVersion3, params)
        connection.executeBuild(buildParams, params)

        then:
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory) >> connectionImpl
        1 * connection.modelProvider.provide(connectionImpl, ProjectVersion3, params)
        1 * connectionImpl.executeBuild(buildParams, params)
        0 * _._
    }

    def stopsConnectionOnStop() {
        when:
        connection.getModel(ProjectVersion3, params)
        connection.stop()

        then:
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory) >> connectionImpl
        1 * connection.modelProvider.provide(connectionImpl, ProjectVersion3, params)
        1 * connectionImpl.stop()
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
        connection.getModel(ProjectVersion3, params)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * loggingProvider.getProgressLoggerFactory() >> progressLoggerFactory
        1 * implementationLoader.create(distribution, progressLoggerFactory) >> { throw failure }

        when:
        connection.stop()

        then:
        0 * _._
    }
}
