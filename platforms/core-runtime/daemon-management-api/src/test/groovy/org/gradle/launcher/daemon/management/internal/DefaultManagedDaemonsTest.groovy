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
package org.gradle.launcher.daemon.management.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.specs.ExplainingSpec
import org.gradle.internal.id.IdGenerator
import org.gradle.launcher.daemon.connection.DaemonClientConnection
import org.gradle.launcher.daemon.connection.DaemonConnector
import org.gradle.launcher.daemon.context.DaemonConnectDetails
import org.gradle.launcher.daemon.protocol.ReportStatus
import org.gradle.launcher.daemon.protocol.Status
import org.gradle.launcher.daemon.protocol.Stop
import org.gradle.launcher.daemon.protocol.StopWhenIdle
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.DaemonRegistry
import spock.lang.Specification

class DefaultManagedDaemonsTest extends Specification {

    def registry = Mock(DaemonRegistry)
    def connector = Mock(DaemonConnector)
    def idGenerator = Stub(IdGenerator) { generateId() >> UUID.randomUUID() }
    def documentationRegistry = Stub(DocumentationRegistry) { getDocumentationRecommendationFor(_, _, _) >> "" }

    def managed = new DefaultManagedDaemons(registry, connector, idGenerator, documentationRegistry)

    def daemonInfo(long pid) {
        Mock(DaemonInfo) { getPid() >> pid }
    }

    private DaemonClientConnection connectionFor() {
        def details = Stub(DaemonConnectDetails) { getToken() >> new byte[16] }
        Mock(DaemonClientConnection) { getDaemon() >> details }
    }

    def "getDaemons exposes a handle per registry entry"() {
        given:
        registry.getAll() >> [daemonInfo(100), daemonInfo(200)]

        expect:
        managed.getDaemons()*.pid == [100L, 200L]
    }

    def "stopAll reports when there are no daemons"() {
        when:
        managed.stopAll()

        then:
        1 * connector.maybeConnect(_ as ExplainingSpec) >> null
        0 * _._
    }

    def "stopAll dispatches Stop to each daemon until none remain"() {
        given:
        def connection = connectionFor()
        connection.getDaemon() >> Stub(DaemonConnectDetails) { getUid() >> "uid-1"; getToken() >> new byte[16] }

        when:
        managed.stopAll()

        then:
        2 * connector.maybeConnect(_ as ExplainingSpec) >>> [connection, null]
        1 * connection.dispatch({ it instanceof Stop })
        1 * connection.stop()
    }

    def "stopAllWhenIdle dispatches StopWhenIdle to the registered daemons"() {
        given:
        def a = daemonInfo(100)
        def connection = connectionFor()
        registry.getAll() >> [a]
        connector.maybeConnect(a) >> connection

        when:
        managed.stopAllWhenIdle()

        then:
        1 * connection.dispatch({ it instanceof StopWhenIdle })
    }

    def "stopWhenIdle dispatches StopWhenIdle to the given daemons"() {
        given:
        def daemon = Stub(DaemonConnectDetails) { getToken() >> new byte[16] }
        def connection = connectionFor()
        connector.maybeConnect(daemon) >> connection

        when:
        managed.stopWhenIdle([daemon])

        then:
        1 * connection.dispatch({ it instanceof StopWhenIdle })
    }

    def "reportStatus queries each daemon and prints even when empty"() {
        given:
        registry.getAll() >> []
        registry.getStopEvents() >> []

        when:
        managed.reportStatus()

        then:
        noExceptionThrown()
    }

    def "a handle getStatus queries the daemon over the protocol"() {
        given:
        def info = daemonInfo(100)
        def connection = connectionFor()
        connector.maybeConnect(info) >> connection
        connection.receive() >> new Success(new Status(100L, "9.9", "IDLE"))
        registry.getAll() >> [info]
        def handle = managed.getDaemons().first()

        when:
        def status = handle.status

        then:
        1 * connection.dispatch({ it instanceof ReportStatus })
        status.state == "IDLE"
    }

    def "a handle stop() dispatches a Stop"() {
        given:
        def info = daemonInfo(100)
        def connection = connectionFor()
        connector.maybeConnect(info) >> connection
        registry.getAll() >> [info]

        when:
        managed.getDaemons().first().stop()

        then:
        1 * connection.dispatch({ it instanceof Stop })
        1 * connection.stop()
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
}
