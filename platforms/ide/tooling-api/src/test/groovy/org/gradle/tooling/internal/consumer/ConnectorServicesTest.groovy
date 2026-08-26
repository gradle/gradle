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

import org.gradle.tooling.GradleConnector
import spock.lang.Specification

class ConnectorServicesTest extends Specification {

    def cleanup() {
        ConnectorServices.reset()
    }

    def "services sharing configuration"() {
        when:
        def connectorOne = ConnectorServices.createConnector()
        def connectorTwo = ConnectorServices.createConnector()

        then:
        connectorOne != connectorTwo

        //below is necessary for some of the thread safety measures we took in the internal implementation
        //it is covered in integrations tests as well, but is not immediately obvious why the concurrent integ test fails hence below assertions

        //tooling impl loader must be shared across connectors, so that we have single DefaultConnection per distro/classpath
        connectorOne.connectionFactory.toolingImplementationLoader == connectorTwo.connectionFactory.toolingImplementationLoader
    }

    def "can close services and create connector again"() {
        when:
        ConnectorServices.close()
        def connector = ConnectorServices.createConnector()

        then:
        connector != null
    }

    def "discards the shared factory when closing it fails"() {
        given:
        def failure = new RuntimeException("cannot stop services")
        //noinspection GroovyAccessibility
        ConnectorServices.sharedConnectorFactory = new BrokenConnectorFactory(failure)

        when:
        ConnectorServices.close()

        then:
        def e = thrown(RuntimeException)
        e === failure

        when: "a connector is requested after the failed close"
        def connector = ConnectorServices.createConnector()

        then: "the broken factory is gone and a fresh one is used"
        connector != null
    }

    def "resets the active connector count when closing the shared factory fails"() {
        given: "an active connector, so that the count is not already zero"
        ConnectorServices.createConnector()
        //noinspection GroovyAccessibility
        assert ConnectorServices.activeConnectors == 1

        and:
        //noinspection GroovyAccessibility
        ConnectorServices.sharedConnectorFactory = new BrokenConnectorFactory(new RuntimeException("cannot stop services"))

        when:
        ConnectorServices.reset()

        then:
        thrown(RuntimeException)

        and:
        //noinspection GroovyAccessibility
        ConnectorServices.activeConnectors == 0
    }

    private static class BrokenConnectorFactory implements GradleConnectorFactory {
        private final RuntimeException failure

        BrokenConnectorFactory(RuntimeException failure) {
            this.failure = failure
        }

        @Override
        GradleConnector createConnector() {
            throw new UnsupportedOperationException()
        }

        @Override
        void close() {
            throw failure
        }
    }
}
