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

import org.gradle.api.internal.specs.ExplainingSpec
import org.gradle.internal.id.UUIDGenerator
import org.gradle.launcher.daemon.context.DaemonConnectDetails
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.protocol.Failure
import org.gradle.launcher.daemon.protocol.Finished
import org.gradle.launcher.daemon.protocol.Stop
import org.gradle.launcher.daemon.protocol.StopWhenIdle
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.util.ConcurrentSpecification

class DaemonStopClientTest extends ConcurrentSpecification {
    final DaemonConnector connector = Mock()
    final DaemonClientConnection connection = Mock()
    final def idGenerator = new UUIDGenerator()
    final def client = new DaemonStopClient(connector, idGenerator)

    def "requests daemons stop gracefully"() {
        def daemon1 = Stub(DaemonConnectDetails)
        def daemon2 = Stub(DaemonConnectDetails)

        when:
        client.gracefulStop([daemon1, daemon2])

        then:
        1 * connector.maybeConnect(daemon1) >>> connection
        _ * connection.daemon >> daemon1
        1 * connection.dispatch({it instanceof StopWhenIdle})
        1 * connection.receive() >> new Success(null)
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()

        and:
        1 * connector.maybeConnect(daemon2) >>> connection
        _ * connection.daemon >> daemon2
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
        _ * connection.daemon >> daemon('1')
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
        _ * connection.daemon >> daemon('1')
        _ * connection2.daemon >> daemon('2')
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
        def d1 = daemonContext('1')
        def d2 = daemonContext('2')
        def connection2 = Mock(DaemonClientConnection)

        when:
        client.stop()

        then:
        _ * connection.daemon >> daemon('1')
        _ * connection2.daemon >> daemon('2')

        1 * connector.maybeConnect(_) >> { ExplainingSpec spec ->
            assert spec.isSatisfiedBy(d1)
            assert spec.isSatisfiedBy(d2)
            connection
        }
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new Success(null)
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()

        1 * connector.maybeConnect(_) >> { ExplainingSpec spec ->
            assert !spec.isSatisfiedBy(d1)
            assert spec.isSatisfiedBy(d2)
            connection2
        }
        1 * connection2.dispatch({it instanceof Stop})
        1 * connection2.receive() >> new Success(null)
        1 * connection2.dispatch({it instanceof Finished})
        1 * connection2.stop()

        1 * connector.maybeConnect(_) >> { ExplainingSpec spec ->
            assert !spec.isSatisfiedBy(d1)
            assert !spec.isSatisfiedBy(d2)
            null
        }
        0 * _
    }

    def "handles failed stop"() {
        def d1 = daemonContext('1')

        when:
        client.stop()

        then:
        _ * connection.daemon >> daemon('1')

        1 * connector.maybeConnect(_) >> { ExplainingSpec spec ->
            assert spec.isSatisfiedBy(d1)
            connection
        }
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new Failure(new RuntimeException())
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()

        1 * connector.maybeConnect(_) >> { ExplainingSpec spec ->
            assert !spec.isSatisfiedBy(d1)
            null
        }
        0 * _
    }

    private DaemonConnectDetails daemon(String id) {
        Stub(DaemonConnectDetails) {
            getUid() >> id
        }
    }

    private DaemonContext daemonContext(String id) {
        Stub(DaemonContext) {
            getUid() >> id
        }
    }
}
