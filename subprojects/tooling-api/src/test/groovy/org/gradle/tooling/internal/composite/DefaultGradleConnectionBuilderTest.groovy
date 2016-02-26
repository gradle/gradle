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

package org.gradle.tooling.internal.composite

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.tooling.internal.consumer.DistributionFactory
import org.gradle.tooling.internal.consumer.LoggingProvider
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import spock.lang.Specification

class DefaultGradleConnectionBuilderTest extends Specification {
    ToolingImplementationLoader toolingImplementationLoader = Mock()
    ExecutorFactory executorFactory = Mock()
    LoggingProvider loggingProvider = Mock()
    GradleConnectionFactory connectionFactory = new GradleConnectionFactory(toolingImplementationLoader, executorFactory, loggingProvider)
    DistributionFactory distributionFactory = Mock()

    def builder = new DefaultGradleConnectionBuilder(connectionFactory, distributionFactory)

    def "requires at least one participant"() {
        when:
        builder.build()
        then:
        thrown(IllegalStateException)
    }
}
