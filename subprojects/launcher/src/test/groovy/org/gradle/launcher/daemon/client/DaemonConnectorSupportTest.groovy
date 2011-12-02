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

import org.gradle.api.specs.Specs
import org.gradle.api.GradleException
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry
import org.gradle.launcher.daemon.context.DaemonContext

import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.OutgoingConnector

import spock.lang.*

class DaemonConnectorSupportTest extends Specification {

    def connectTimeoutSecs = 1
    def daemonCounter = 0
    def compatibilitySpec = { true }

    def getCompatibilitySpec() {
        this.compatibilitySpec
    }

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
            Specs.convertClosureToSpec { getCompatibilitySpec()(it) },
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

    def connect() {
        connector.connect().connection.num
    }

    def getRegistry() {
        connector.daemonRegistry
    }

    def getNumAllDaemons() {
        registry.all.size()
    }

    def "can create new connection"() {
        expect:
        connect() == 0
    }

    def "connector will not use existing connection if it fails the compatibility spec"() {
        given:
        startNewDaemon()

        expect:
        numAllDaemons == 1

        when:
        compatibilitySpec = { it.num > 0 }

        then:
        connect() == 1

        and:
        numAllDaemons == 2
    }
    
    def "connector will error if daemon started by connector fails compatibility spec"() {
        given:
        compatibilitySpec = { false }
        
        when:
        connect()
        
        then:
        GradleException e = thrown()
        e.message.startsWith "Timeout waiting to connect to Gradle daemon"
    }
    

}