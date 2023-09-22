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

import org.gradle.api.internal.specs.ExplainingSpec
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.remote.Address
import org.gradle.internal.remote.internal.ConnectCompletion
import org.gradle.internal.remote.internal.ConnectException
import org.gradle.internal.remote.internal.OutgoingConnector
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.serialize.Serializer
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry
import spock.lang.Specification

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Busy
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.Idle

class DefaultDaemonConnectorTest extends Specification {

    def javaHome = new File("tmp")
    def connectTimeoutSecs = 1
    def daemonCounter = 0

    class OutgoingConnectorStub implements OutgoingConnector {
        ConnectCompletion connect(Address address) throws ConnectException {
            def connection = [:] as RemoteConnection
            // unsure why I can't add this as property in the map-mock above
            connection.metaClass.num = address.num
            return { connection } as ConnectCompletion
        }
    }

    def createAddress(int i) {
        new Address() {
            int getNum() { i }

            String getDisplayName() { getNum() }
        }
    }

    def createConnector() {
        def connector = Spy(DefaultDaemonConnector, constructorArgs: [
                new EmbeddedDaemonRegistry(),
                Spy(OutgoingConnectorStub),
                { startBusyDaemon() } as DaemonStarter,
                Stub(DaemonStartListener),
                Stub(ProgressLoggerFactory),
                Stub(Serializer)]
        )
        connector.connectTimeout = connectTimeoutSecs * 1000
        connector
    }

    def startBusyDaemon() {
        def daemonNum = daemonCounter++
        DaemonContext context = new DefaultDaemonContext(daemonNum.toString(), javaHome, javaHome, daemonNum, 1000, [], false, DaemonParameters.Priority.NORMAL)
        def address = createAddress(daemonNum)
        registry.store(new DaemonInfo(address, context, "password".bytes, Busy))
        return new DaemonStartupInfo(daemonNum.toString(), null, null);
    }

    def startIdleDaemon() {
        def daemonNum = daemonCounter++
        DaemonContext context = new DefaultDaemonContext(daemonNum.toString(), javaHome, javaHome, daemonNum, 1000, [], false, DaemonParameters.Priority.NORMAL)
        def address = createAddress(daemonNum)
        registry.store(new DaemonInfo(address, context, "password".bytes, Idle))
    }

    def theConnector

    def DefaultDaemonConnector getConnector() {
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

    abstract static class DummyExplainingSpec implements ExplainingSpec {
        String whyUnsatisfied(Object element) {
            ""
        }
    }

    def "maybeConnect() returns connection to any daemon that matches spec"() {
        given:
        startIdleDaemon()
        startIdleDaemon()

        expect:
        def connection = connector.maybeConnect({it.pid < 12} as ExplainingSpec)
        connection && connection.connection.num < 12
    }

    def "maybeConnect() returns null when no daemon matches spec"() {
        given:
        startIdleDaemon()
        startIdleDaemon()

        expect:
        connector.maybeConnect({it.pid == 12} as DummyExplainingSpec) == null
    }

    def "maybeConnect() ignores daemons that do not match spec"() {
        given:
        startIdleDaemon()
        startIdleDaemon()

        expect:
        def connection = connector.maybeConnect({it.pid == 1} as DummyExplainingSpec)
        connection && connection.connection.num == 1
    }

    def "connect() returns connection to any existing daemon that matches spec"() {
        given:
        startIdleDaemon()
        startIdleDaemon()

        expect:
        def connection = connector.connect({it.pid < 12} as ExplainingSpec)
        connection && connection.connection.num < 12

        and:
        numAllDaemons == 2
    }

    def "connect() returns null when no daemon matches spec"() {
        given:
        startIdleDaemon()

        expect:
        def connection = connector.connect({it.pid > 0} as DummyExplainingSpec)
        connection == null

        and:
        numAllDaemons == 1
    }

    def "connect() will not use existing connection if it fails the compatibility spec"() {
        given:
        startIdleDaemon()

        expect:
        def connection = connector.connect({it.pid != 0} as DummyExplainingSpec)
        connection == null

        and:
        numAllDaemons == 1
    }

    def "suspect address is removed from the registry on connect failure"() {
        given:
        startIdleDaemon()
        assert !registry.all.empty

        connector.connector.connect(_ as Address) >> { throw new ConnectException("Problem!", new RuntimeException("foo")) }

        when:
        def connection = connector.maybeConnect( { true } as ExplainingSpec)

        then:
        !connection

        registry.all.empty
    }
}
