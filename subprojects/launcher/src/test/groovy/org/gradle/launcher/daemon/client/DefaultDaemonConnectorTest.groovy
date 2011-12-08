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
package org.gradle.launcher.daemon.client

import org.gradle.api.GradleException
import org.gradle.api.specs.Spec
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry
import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.OutgoingConnector
import spock.lang.Specification

class DefaultDaemonConnectorTest extends Specification {

    def connectTimeoutSecs = 1
    def daemonCounter = 0

    def createOutgoingConnector() {
        new OutgoingConnector() {
            Connection connect(Address address) {
                def connection = [:] as Connection
                // unsure why I can't add this as property in the map-mock above
                connection.metaClass.num = address.num
                connection
            }
        }
    }

    def createAddress(int i) {
        new Address() {
            int getNum() { i }

            String getDisplayName() { getNum() }
        }
    }

    def createConnector() {
        def connector = new DefaultDaemonConnector(
                new EmbeddedDaemonRegistry(),
                createOutgoingConnector(),
                { startNewDaemon() }
        )
        connector.connectTimeout = connectTimeoutSecs * 1000
        connector
    }

    def startNewDaemon() {
        def daemonNum = daemonCounter++
        def context = [:] as DaemonContext
        context.metaClass.num = daemonNum
        registry.store(createAddress(daemonNum), context, "password")
    }

    def theConnector

    def getConnector() {
        if (theConnector == null) {
            theConnector = createConnector()
        }
        theConnector
    }

    def getRegistry() {
        connector.daemonRegistry
    }

    def getNumAllDaemons() {
        registry.all.size()
    }

    def "maybeConnect() returns connection to any daemon that matches spec"() {
        given:
        startNewDaemon()
        startNewDaemon()
        
        expect:
        def connection = connector.maybeConnect({it.num < 12} as Spec)
        connection && connection.connection.num < 12
    }

    def "maybeConnect() returns null when no daemon matches spec"() {
        given:
        startNewDaemon()
        startNewDaemon()

        expect:
        connector.maybeConnect({it.num == 12} as Spec) == null
    }

    def "maybeConnect() ignores daemons that do not match spec"() {
        given:
        startNewDaemon()
        startNewDaemon()

        expect:
        def connection = connector.maybeConnect({it.num == 1} as Spec)
        connection && connection.connection.num == 1
    }

    def "connect() returns connection to any existing daemon that matches spec"() {
        given:
        startNewDaemon()
        startNewDaemon()

        expect:
        def connection = connector.connect({it.num < 12} as Spec)
        connection && connection.connection.num < 12

        and:
        numAllDaemons == 2
    }

    def "connect() starts a new daemon when no daemon matches spec"() {
        given:
        startNewDaemon()

        expect:
        def connection = connector.connect({it.num > 0} as Spec)
        connection && connection.connection.num > 0

        and:
        numAllDaemons == 2
    }

    def "connect() will not use existing connection if it fails the compatibility spec"() {
        given:
        startNewDaemon()

        expect:
        def connection = connector.connect({it.num != 0} as Spec)
        connection && connection.connection.num != 0

        and:
        numAllDaemons == 2
    }

    def "connect() will error if daemon started by connector fails compatibility spec"() {
        when:
        connector.connect({false} as Spec)

        then:
        GradleException e = thrown()
        e.message.startsWith "Timeout waiting to connect to Gradle daemon"
    }


}