/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.id.IdGenerator
import org.gradle.launcher.daemon.context.DaemonConnectDetails
import org.gradle.launcher.daemon.protocol.Stop
import org.gradle.launcher.daemon.protocol.StopWhenIdle
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.DaemonRegistry
import spock.lang.Specification

class DefaultManagedDaemonsTest extends Specification {

    def registry = Mock(DaemonRegistry)
    def connector = Mock(DaemonConnector)
    def idGenerator = Stub(IdGenerator) { generateId() >> UUID.randomUUID() }
    def stopClient = Mock(DaemonStopClient)
    def statusClient = Mock(ReportDaemonStatusClient)

    def managed = new DefaultManagedDaemons(registry, connector, idGenerator, stopClient, statusClient)

    def daemonInfo(long pid) {
        Mock(DaemonInfo) { getPid() >> pid }
    }

    def "stopAll delegates to the stop client"() {
        when:
        managed.stopAll()

        then:
        1 * stopClient.stop()
    }

    def "reportStatus delegates to the status client"() {
        when:
        managed.reportStatus()

        then:
        1 * statusClient.listAll()
    }

    def "stopAllWhenIdle gracefully stops the registered daemons"() {
        given:
        def a = daemonInfo(100)
        def b = daemonInfo(200)
        registry.getAll() >> [a, b]

        when:
        managed.stopAllWhenIdle()

        then:
        1 * stopClient.gracefulStop({ it as List == [a, b] })
    }

    def "getDaemons exposes a handle per registry entry"() {
        given:
        registry.getAll() >> [daemonInfo(100), daemonInfo(200)]

        expect:
        managed.getDaemons()*.pid == [100L, 200L]
    }

    def "a handle stop() dispatches a Stop over the protocol"() {
        given:
        def info = daemonInfo(100)
        def connection = connectionFor()
        connector.maybeConnect(info) >> connection
        registry.getAll() >> [info]
        def handle = managed.getDaemons().first()

        when:
        handle.stop()

        then:
        1 * connection.dispatch({ it instanceof Stop })
        1 * connection.stop()
    }

    def "a handle stopWhenIdle() dispatches a StopWhenIdle over the protocol"() {
        given:
        def info = daemonInfo(100)
        def connection = connectionFor()
        connector.maybeConnect(info) >> connection
        registry.getAll() >> [info]
        def handle = managed.getDaemons().first()

        when:
        handle.stopWhenIdle()

        then:
        1 * connection.dispatch({ it instanceof StopWhenIdle })
    }

    def "a handle for an unreachable daemon is a no-op / null status"() {
        given:
        def info = daemonInfo(100)
        connector.maybeConnect(info) >> null
        registry.getAll() >> [info]
        def handle = managed.getDaemons().first()

        expect:
        handle.status == null

        when:
        handle.stop()
        handle.stopWhenIdle()

        then:
        noExceptionThrown()
    }

    private DaemonClientConnection connectionFor() {
        def details = Stub(DaemonConnectDetails) { getToken() >> new byte[16] }
        Mock(DaemonClientConnection) { getDaemon() >> details }
    }
}
