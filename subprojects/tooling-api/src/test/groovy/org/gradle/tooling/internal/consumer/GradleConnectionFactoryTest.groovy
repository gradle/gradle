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
import org.gradle.tooling.internal.protocol.GradleConnectionVersion1
import org.gradle.tooling.internal.protocol.GradleConnectionFactoryVersion1
import org.gradle.tooling.internal.provider.DefaultGradleConnectionFactory

class GradleConnectionFactoryTest extends Specification {
    def usesMetaInfServiceToDetermineFactoryImplementation() {
        GradleConnectionFactory factory = new GradleConnectionFactory()

        expect:
        factory.implementationFactory instanceof DefaultGradleConnectionFactory
    }

    def instantiatesFactoryImplementationAndUsesItToCreateConnections() {
        GradleConnectionFactoryVersion1 factoryImpl = Mock()
        TestFactory.factory = factoryImpl
        GradleConnectionVersion1 connectionImpl = Mock()
        File projectDir = new File('project-dir')
        GradleConnectionFactory factory = new GradleConnectionFactory(TestFactory.class.name)

        when:
        def connection = factory.create(projectDir)

        then:
        factory.implementationFactory instanceof TestFactory
        connection != null
        1 * factoryImpl.create(projectDir) >> connectionImpl
    }
}

class TestFactory implements GradleConnectionFactoryVersion1 {
    static GradleConnectionFactoryVersion1 factory

    GradleConnectionVersion1 create(File projectDirectory) {
        return factory.create(projectDirectory)
    }
}