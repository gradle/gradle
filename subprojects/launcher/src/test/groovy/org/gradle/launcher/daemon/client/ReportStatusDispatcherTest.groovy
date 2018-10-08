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

package org.gradle.launcher.daemon.client

import org.gradle.internal.remote.internal.Connection
import org.gradle.launcher.daemon.protocol.ReportStatus
import spock.lang.Specification

class ReportStatusDispatcherTest extends Specification {

    def dispatcher = new ReportStatusDispatcher()
    def connection = Mock(Connection)

    def "ignores failed dispatch and does not receive"() {
        given:
        def message = new ReportStatus(UUID.randomUUID(), "TOKEN".bytes)
        connection.dispatch(message) >> { throw new RuntimeException("Cannot dispatch") }

        when:
        dispatcher.dispatch(connection, message)

        then:
        0 * connection.receive()
        noExceptionThrown()
    }

    def "ignores failed receive"() {
        given:
        def message = new ReportStatus(UUID.randomUUID(), "TOKEN".bytes)
        connection.receive() >> { throw new RuntimeException("Cannot dispatch") }

        when:
        dispatcher.dispatch(connection, message)

        then:
        1 * connection.dispatch(_)
        noExceptionThrown()
    }
}
