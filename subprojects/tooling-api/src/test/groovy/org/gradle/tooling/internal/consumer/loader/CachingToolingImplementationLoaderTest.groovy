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

import org.apache.commons.lang.RandomStringUtils
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class CachingToolingImplementationLoaderTest extends Specification {
    final ToolingImplementationLoader target = Mock()
    final ProgressLoggerFactory loggerFactory = Mock()
    final ConnectionParameters params = Mock()
    final BuildCancellationToken cancellationToken = Mock()
    final CachingToolingImplementationLoader loader = new CachingToolingImplementationLoader(target)

    @Rule TemporaryFolder tempFolder = new TemporaryFolder();

    def delegatesToTargetLoaderToCreateImplementation() {
        def distribution = Mock(Distribution)
        def connection = Mock(ConsumerConnection)
        def userHomeDir = new File("user-home")

        when:
        def impl = loader.create(distribution, loggerFactory, params, cancellationToken)

        then:
        impl == connection
        1 * target.create(distribution, loggerFactory, params, cancellationToken) >> connection
        1 * params.getGradleUserHomeDir() >> userHomeDir
        _ * distribution.getToolingImplementationClasspath(loggerFactory, userHomeDir, cancellationToken) >> new DefaultClassPath(randomeFileWithContent())
        0 * _._
    }

    private File randomeFileWithContent() {
        def file = tempFolder.newFile(RandomStringUtils.random(10) + '.jar')
        file << RandomStringUtils.random(1000)
        file
    }

    def reusesImplementationWithSameClasspath() {
        def distribution = Mock(Distribution)
        def connection = Mock(ConsumerConnection)
        def userHomeDir = new File("user-home")
        def classpathFile = randomeFileWithContent()
        when:
        def impl = loader.create(distribution, loggerFactory, params, cancellationToken)
        def impl2 = loader.create(distribution, loggerFactory, params, cancellationToken)

        then:
        impl == connection
        impl2 == connection
        1 * target.create(distribution, loggerFactory, params, cancellationToken) >> connection
        2 * params.getGradleUserHomeDir() >> userHomeDir
        _ * distribution.getToolingImplementationClasspath(loggerFactory, userHomeDir, cancellationToken) >> { new DefaultClassPath(classpathFile) }
        0 * _._
    }

    def createsNewImplementationWhenClasspathHashNotSeenBefore() {
        def connection1 = Mock(ConsumerConnection)
        def connection2 = Mock(ConsumerConnection)
        def distribution1 = Mock(Distribution)
        def distribution2 = Mock(Distribution)

        when:
        def impl = loader.create(distribution1, loggerFactory, params, cancellationToken)
        def impl2 = loader.create(distribution2, loggerFactory, params, cancellationToken)

        then:
        impl == connection1
        impl2 == connection2
        1 * target.create(distribution1, loggerFactory, params, cancellationToken) >> connection1
        1 * target.create(distribution2, loggerFactory, params, cancellationToken) >> connection2
        2 * params.getGradleUserHomeDir() >> null
        _ * distribution1.getToolingImplementationClasspath(loggerFactory, null, cancellationToken) >> new DefaultClassPath(randomeFileWithContent())
        _ * distribution2.getToolingImplementationClasspath(loggerFactory, null, cancellationToken) >> new DefaultClassPath(randomeFileWithContent())
        0 * _._
    }

    def closesConnectionsWhenClosed() {
        def connection1 = Mock(ConsumerConnection)
        def connection2 = Mock(ConsumerConnection)
        def distribution1 = Mock(Distribution)
        def distribution2 = Mock(Distribution)

        given:
        _ * distribution1.getToolingImplementationClasspath(loggerFactory, null, cancellationToken) >> new DefaultClassPath(randomeFileWithContent())
        _ * distribution2.getToolingImplementationClasspath(loggerFactory, null, cancellationToken) >> new DefaultClassPath(randomeFileWithContent())
        _ * target.create(distribution1, loggerFactory, params, cancellationToken) >> connection1
        _ * target.create(distribution2, loggerFactory, params, cancellationToken) >> connection2
        _ * params.getGradleUserHomeDir() >> null

        loader.create(distribution1, loggerFactory, params, cancellationToken)
        loader.create(distribution2, loggerFactory, params, cancellationToken)
        loader.create(distribution1, loggerFactory, params, cancellationToken)

        when:
        loader.close()

        then:
        1 * connection1.stop()
        1 * connection2.stop()
        0 * _
    }
}
