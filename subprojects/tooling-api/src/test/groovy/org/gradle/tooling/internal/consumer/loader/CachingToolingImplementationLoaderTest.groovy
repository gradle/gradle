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

import org.gradle.logging.ProgressLoggerFactory
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import spock.lang.Specification

class CachingToolingImplementationLoaderTest extends Specification {
    final ToolingImplementationLoader target = Mock()
    final ProgressLoggerFactory loggerFactory = Mock()
    final CachingToolingImplementationLoader loader = new CachingToolingImplementationLoader(target)

    def delegatesToTargetLoaderToCreateImplementation() {
        final Distribution distribution = Mock()
        final ConsumerConnection connection = Mock()

        when:
        def impl = loader.create(distribution, loggerFactory, true)

        then:
        impl == connection
        1 * target.create(distribution, loggerFactory, true) >> connection
        _ * distribution.getToolingImplementationClasspath(loggerFactory) >> ([new File('a.jar')] as Set)
        0 * _._
    }

    def reusesImplementationWithSameClasspath() {
        final Distribution distribution = Mock()
        final ConsumerConnection connection = Mock()

        when:
        def impl = loader.create(distribution, loggerFactory, true)
        def impl2 = loader.create(distribution, loggerFactory, true)

        then:
        impl == connection
        impl2 == connection
        1 * target.create(distribution, loggerFactory, true) >> connection
        _ * distribution.getToolingImplementationClasspath(loggerFactory) >> ([new File('a.jar')] as Set)
        0 * _._
    }

    def createsNewImplementationWhenClasspathNotSeenBefore() {
        ConsumerConnection connection1 = Mock()
        ConsumerConnection connection2 = Mock()
        Distribution distribution1 = Mock()
        Distribution distribution2 = Mock()

        when:
        def impl = loader.create(distribution1, loggerFactory, true)
        def impl2 = loader.create(distribution2, loggerFactory, false)

        then:
        impl == connection1
        impl2 == connection2
        1 * target.create(distribution1, loggerFactory, true) >> connection1
        1 * target.create(distribution2, loggerFactory, false) >> connection2
        _ * distribution1.getToolingImplementationClasspath(loggerFactory) >> ([new File('a.jar')] as Set)
        _ * distribution2.getToolingImplementationClasspath(loggerFactory) >> ([new File('b.jar')] as Set)
        0 * _._
    }
}
