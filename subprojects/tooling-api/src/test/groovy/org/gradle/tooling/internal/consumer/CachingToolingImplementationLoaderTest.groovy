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

import spock.lang.Specification
import org.gradle.tooling.internal.protocol.ConnectionFactoryVersion2

class CachingToolingImplementationLoaderTest extends Specification {
    final ToolingImplementationLoader target = Mock()
    final CachingToolingImplementationLoader loader = new CachingToolingImplementationLoader(target)

    def delegatesToTargetLoaderToCreateImplementation() {
        ConnectionFactoryVersion2 factoryImpl = Mock()
        final Distribution distribution = Mock()

        when:
        def impl = loader.create(distribution)

        then:
        impl == factoryImpl
        1 * target.create(distribution) >> factoryImpl
        _ * distribution.toolingImplementationClasspath >> ([new File('a.jar')] as Set)
        0 * _._
    }

    def reusesImplementationWithSameClasspath() {
        ConnectionFactoryVersion2 factoryImpl = Mock()
        final Distribution distribution = Mock()

        when:
        def impl = loader.create(distribution)
        def impl2 = loader.create(distribution)

        then:
        impl == factoryImpl
        impl2 == factoryImpl
        1 * target.create(distribution) >> factoryImpl
        _ * distribution.toolingImplementationClasspath >> ([new File('a.jar')] as Set)
        0 * _._
    }

    def createsNewImplementationWhenClasspathNotSeenBefore() {
        ConnectionFactoryVersion2 factoryImpl1 = Mock()
        ConnectionFactoryVersion2 factoryImpl2 = Mock()
        Distribution distribution1 = Mock()
        Distribution distribution2 = Mock()

        when:
        def impl = loader.create(distribution1)
        def impl2 = loader.create(distribution2)

        then:
        impl == factoryImpl1
        impl2 == factoryImpl2
        1 * target.create(distribution1) >> factoryImpl1
        1 * target.create(distribution2) >> factoryImpl2
        _ * distribution1.toolingImplementationClasspath >> ([new File('a.jar')] as Set)
        _ * distribution2.toolingImplementationClasspath >> ([new File('b.jar')] as Set)
        0 * _._
    }
}
