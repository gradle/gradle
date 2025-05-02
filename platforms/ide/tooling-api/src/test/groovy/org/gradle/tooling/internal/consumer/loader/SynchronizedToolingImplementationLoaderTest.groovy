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
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener

public class SynchronizedToolingImplementationLoaderTest extends ConcurrentSpec {

    def factory = Mock(ProgressLoggerFactory)
    def distro = Mock(Distribution)
    private def logger = Mock(ProgressLogger)
    def params = Mock(ConnectionParameters)
    def cancellationToken = Mock(BuildCancellationToken)
    def target = Mock(ToolingImplementationLoader)
    def loader = new SynchronizedToolingImplementationLoader(target)

    def "blocks and reports progress when busy"() {
        when:
        start {
            loader.create(distro, factory, _ as InternalBuildProgressListener, params, cancellationToken)
        }
        async {
            thread.blockUntil.busy
            loader.create(distro, factory, _ as InternalBuildProgressListener, params, cancellationToken)
        }

        then:
        instant.idle < instant.created
        1 * target.create(distro, factory, _ as InternalBuildProgressListener, params, cancellationToken) >> {
            instant.busy
            thread.block()
            instant.idle
            Stub(ConsumerConnection)
        }
        1 * target.create(distro, factory, _ as InternalBuildProgressListener, params, cancellationToken) >> {
            instant.created
            Stub(ConsumerConnection)
        }

        and:
        1 * factory.newOperation(_ as Class) >> logger
        1 * logger.setDescription(_ as String)
        1 * logger.started()
        1 * logger.completed()
        0 * _
    }

    def "does not report progress when appropriate"() {
        when:
        loader.create(distro, factory, _ as InternalBuildProgressListener, params, cancellationToken)

        then:
        1 * target.create(distro, factory, _ as InternalBuildProgressListener, params, cancellationToken)
        0 * _
    }
}
