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
package org.gradle.tooling.internal.consumer.loader

import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.DefaultConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import spock.lang.Specification

class CachingToolingImplementationLoaderTest extends Specification {
    final ToolingImplementationLoader target = Mock()
    final ProgressLoggerFactory loggerFactory = Mock()
    final InternalBuildProgressListener progressListener = Mock()
    final ConnectionParameters params = DefaultConnectionParameters.builder().build()
    final BuildCancellationToken cancellationToken = Mock()
    final CachingToolingImplementationLoader loader = new CachingToolingImplementationLoader(target)

    def delegatesToTargetLoaderToCreateImplementation() {
        def distribution = Mock(Distribution)
        def connection = Mock(ConsumerConnection)

        when:
        def impl = loader.create(distribution, loggerFactory, progressListener, params, cancellationToken)

        then:
        impl == connection
        1 * target.create(distribution, loggerFactory, progressListener, params, cancellationToken) >> connection
        _ * distribution.getToolingImplementationClasspath(loggerFactory, progressListener, params, cancellationToken) >> DefaultClassPath.of(new File('a.jar'))
        0 * _._
    }

    def reusesImplementationWithSameClasspath() {
        def distribution = Mock(Distribution)
        def connection = Mock(ConsumerConnection)

        when:
        def impl = loader.create(distribution, loggerFactory, progressListener, params, cancellationToken)
        def impl2 = loader.create(distribution, loggerFactory, progressListener, params, cancellationToken)

        then:
        impl == connection
        impl2 == connection
        1 * target.create(distribution, loggerFactory, progressListener, params, cancellationToken) >> connection
        _ * distribution.getToolingImplementationClasspath(loggerFactory, progressListener, params, cancellationToken) >> { DefaultClassPath.of(new File('a.jar')) }
        0 * _._
    }

    def createsNewImplementationWhenClasspathNotSeenBefore() {
        def connection1 = Mock(ConsumerConnection)
        def connection2 = Mock(ConsumerConnection)
        def distribution1 = Mock(Distribution)
        def distribution2 = Mock(Distribution)

        when:
        def impl = loader.create(distribution1, loggerFactory, progressListener, params, cancellationToken)
        def impl2 = loader.create(distribution2, loggerFactory, progressListener, params, cancellationToken)

        then:
        impl == connection1
        impl2 == connection2
        1 * target.create(distribution1, loggerFactory, progressListener, params, cancellationToken) >> connection1
        1 * target.create(distribution2, loggerFactory, progressListener, params, cancellationToken) >> connection2
        _ * distribution1.getToolingImplementationClasspath(loggerFactory, progressListener, params, cancellationToken) >> DefaultClassPath.of(new File('a.jar'))
        _ * distribution2.getToolingImplementationClasspath(loggerFactory, progressListener, params, cancellationToken) >> DefaultClassPath.of(new File('b.jar'))
        0 * _._
    }

    def closesConnectionsWhenClosed() {
        def connection1 = Mock(ConsumerConnection)
        def connection2 = Mock(ConsumerConnection)
        def distribution1 = Mock(Distribution)
        def distribution2 = Mock(Distribution)

        given:
        loader.create(distribution1, loggerFactory, progressListener, params, cancellationToken)
        loader.create(distribution2, loggerFactory, progressListener, params, cancellationToken)
        loader.create(distribution1, loggerFactory, progressListener, params, cancellationToken)

        _ * target.create(distribution1, loggerFactory, progressListener, params, cancellationToken) >> connection1
        _ * target.create(distribution2, loggerFactory, progressListener, params, cancellationToken) >> connection2
        _ * params.getGradleUserHomeDir() >> null
        _ * distribution1.getToolingImplementationClasspath(loggerFactory, progressListener, params, cancellationToken) >> DefaultClassPath.of(new File('a.jar'))
        _ * distribution2.getToolingImplementationClasspath(loggerFactory, progressListener, params, cancellationToken) >> DefaultClassPath.of(new File('b.jar'))

        when:
        loader.close()

        then:
        connection1.stop()
        connection2.stop()
        0 * _
    }
}
