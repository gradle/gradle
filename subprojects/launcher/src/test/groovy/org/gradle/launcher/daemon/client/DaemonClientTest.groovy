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

import org.gradle.initialization.BuildClientMetaData
import org.gradle.initialization.GradleLauncherAction
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.launcher.daemon.protocol.Build
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.launcher.daemon.protocol.CommandFailure
import org.gradle.launcher.daemon.protocol.Stop
import org.gradle.logging.internal.OutputEventListener
import org.gradle.messaging.remote.internal.Connection
import spock.lang.Specification

class DaemonClientTest extends Specification {
    final DaemonConnector connector = Mock()
    final DaemonConnection daemonConnection = Mock()
    final Connection<Object> connection = Mock()
    final BuildClientMetaData metaData = Mock()
    final OutputEventListener outputEventListener = Mock()
    final DaemonClient client = new DaemonClient(connector, metaData, outputEventListener)

    def setup() {
        daemonConnection.getConnection() >> connection
    }

    def stopsTheDaemonWhenRunning() {
        when:
        client.stop()

        then:
        2 * connector.maybeConnect() >>> [daemonConnection, null]
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new Success(null)
        1 * connection.stop()
        daemonConnection.getConnection() >> connection // why do I need this? Why doesn't the interaction specified in setup cover me?
        0 * _
    }

    def "stops all daemons"() {
        when:
        client.stop()

        then:
        3 * connector.maybeConnect() >>> [daemonConnection, daemonConnection, null]
        2 * connection.dispatch({it instanceof Stop})
        2 * connection.receive() >> new Success(null)
    }

    def stopsTheDaemonWhenNotRunning() {
        when:
        client.stop()

        then:
        1 * connector.maybeConnect() >> null
        0 * _
    }

    def executesAction() {
        GradleLauncherAction<String> action = Mock()
        BuildActionParameters parameters = Mock()

        when:
        def result = client.execute(action, parameters)

        then:
        result == '[result]'
        1 * connector.connect() >> daemonConnection
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> new Success('[result]')
        1 * connection.stop()
    }

    def rethrowsFailureToExecuteAction() {
        GradleLauncherAction<String> action = Mock()
        BuildActionParameters parameters = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        client.execute(action, parameters)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * connector.connect() >> daemonConnection
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> new CommandFailure(failure)
        1 * connection.stop()
    }
}
