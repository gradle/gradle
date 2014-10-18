/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.launcher.daemon.context.DaemonAddress
import org.gradle.launcher.daemon.protocol.Finished
import org.gradle.launcher.daemon.protocol.Stop
import org.gradle.launcher.daemon.protocol.StopWhenIdle
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.util.ConcurrentSpecification

class DaemonStopClientTest extends ConcurrentSpecification {
    final DaemonConnector connector = Mock()
    final DaemonClientConnection connection = Mock()
    final IdGenerator<?> idGenerator = {12} as IdGenerator
    final def client = new DaemonStopClient(connector, idGenerator)

    def "requests daemons stop gracefully"() {
        def address1 = Stub(DaemonAddress)
        def address2 = Stub(DaemonAddress)

        when:
        client.gracefulStop([address1, address2])

        then:
        1 * connector.maybeConnect(address1) >>> connection
        1 * connection.dispatch({it instanceof StopWhenIdle})
        1 * connection.receive() >> new Success(null)
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()

        and:
        1 * connector.maybeConnect(address2) >>> connection
        1 * connection.dispatch({it instanceof StopWhenIdle})
        1 * connection.receive() >> new Success(null)
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        0 * _
    }

    def stopsTheDaemonWhenRunning() {
        when:
        client.stop()

        then:
        _ * connection.uid >> '1'
        2 * connector.maybeConnect(_) >>> [connection, null]
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new Success(null)
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        0 * _
    }

    def stopsTheDaemonWhenNotRunning() {
        when:
        client.stop()

        then:
        1 * connector.maybeConnect(_) >> null
        0 * _
    }

    def "stops all compatible daemons"() {
        DaemonClientConnection connection2 = Mock()

        when:
        client.stop()

        then:
        _ * connection.uid >> '1'
        _ * connection2.uid >> '2'
        3 * connector.maybeConnect(_) >>> [connection, connection2, null]
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new Success(null)
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        1 * connection2.dispatch({it instanceof Stop})
        1 * connection2.receive() >> new Success(null)
        1 * connection2.dispatch({it instanceof Finished})
        1 * connection2.stop()
        0 * _
    }

    def "stops each connection at most once"() {
        when:
        client.stop()

        then:
        _ * connection.uid >> '1'
        3 * connector.maybeConnect(_) >>> [connection, connection, null]
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new Success(null)
        1 * connection.dispatch({it instanceof Finished})
        2 * connection.stop()
        0 * _
    }
}
