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

import org.gradle.initialization.GradleLauncherAction
import org.gradle.internal.id.IdGenerator
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.logging.internal.OutputEventListener
import org.gradle.util.ConcurrentSpecification
import org.gradle.launcher.daemon.protocol.*

class DaemonClientTest extends ConcurrentSpecification {
    final DaemonConnector connector = Mock()
    final DaemonClientConnection connection = Mock()
    final OutputEventListener outputEventListener = Mock()
    final DaemonCompatibilitySpec compatibilitySpec = Mock()
    final IdGenerator<?> idGenerator = {12} as IdGenerator
    final DaemonClient client = new DaemonClient(connector, outputEventListener, compatibilitySpec, new ByteArrayInputStream(new byte[0]), executorFactory, idGenerator)

    def stopsTheDaemonWhenRunning() {
        when:
        client.stop()

        then:
        _ * connection.uid >> '1'
        2 * connector.maybeConnect(compatibilitySpec) >>> [connection, null]
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
        1 * connector.maybeConnect(compatibilitySpec) >> null
        0 * _
    }

    def "stops all compatible daemons"() {
        DaemonClientConnection connection2 = Mock()

        when:
        client.stop()

        then:
        _ * connection.uid >> '1'
        _ * connection2.uid >> '2'
        3 * connector.maybeConnect(compatibilitySpec) >>> [connection, connection2, null]
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
        3 * connector.maybeConnect(compatibilitySpec) >>> [connection, connection, null]
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new Success(null)
        1 * connection.dispatch({it instanceof Finished})
        2 * connection.stop()
        0 * _
    }

    def executesAction() {
        when:
        def result = client.execute(Stub(GradleLauncherAction), Stub(BuildActionParameters))

        then:
        result == '[result]'
        1 * connector.connect(compatibilitySpec) >> connection
        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [Stub(BuildStarted), new Success('[result]')]
        1 * connection.dispatch({it instanceof CloseInput})
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        0 * _
    }

    def rethrowsFailureToExecuteAction() {
        RuntimeException failure = new RuntimeException()

        when:
        client.execute(Stub(GradleLauncherAction), Stub(BuildActionParameters))

        then:
        RuntimeException e = thrown()
        e == failure
        1 * connector.connect(compatibilitySpec) >> connection
        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [Stub(BuildStarted), new CommandFailure(failure)]
        1 * connection.dispatch({it instanceof CloseInput})
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        0 * _
    }
    
    def "tries to find a different daemon if getting the first result from the daemon fails"() {
        DaemonClientConnection connection2 = Mock()

        when:
        client.execute(Stub(GradleLauncherAction), Stub(BuildActionParameters))

        then:
        2 * connector.connect(compatibilitySpec) >>> [connection, connection2]
        1 * connection.dispatch({it instanceof Build}) >> { throw new RuntimeException("Boo!")}
        1 * connection.stop()
        2 * connection2.receive() >>> [Stub(BuildStarted), new Success('')]
        0 * connection._
    }

    def "tries to find a different daemon if the daemon is busy"() {
        DaemonClientConnection connection2 = Mock()

        when:
        client.execute(Stub(GradleLauncherAction), Stub(BuildActionParameters))

        then:
        2 * connector.connect(compatibilitySpec) >>> [connection, connection2]
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> Stub(DaemonUnavailable)
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        2 * connection2.receive() >>> [Stub(BuildStarted), new Success('')]
        0 * connection._
    }

    def "tries to find a different daemon if the first result is null"() {
        DaemonClientConnection connection2 = Mock()

        when:
        client.execute(Stub(GradleLauncherAction), Stub(BuildActionParameters))

        then:
        2 * connector.connect(compatibilitySpec) >>> [connection, connection2]
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> null
        1 * connection.stop()
        2 * connection2.receive() >>> [Stub(BuildStarted), new Success('')]
        0 * connection._
    }

    def "does not loop forever finding usable daemons"() {
        given:
        connector.connect(compatibilitySpec) >> connection
        connection.receive() >> Mock(DaemonUnavailable)

        when:
        client.execute(Stub(GradleLauncherAction), Stub(BuildActionParameters))

        then:
        thrown(NoUsableDaemonFoundException)
    }
}
