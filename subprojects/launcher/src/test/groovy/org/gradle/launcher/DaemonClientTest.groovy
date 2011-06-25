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
package org.gradle.launcher

import org.gradle.initialization.BuildClientMetaData
import org.gradle.initialization.GradleLauncherAction
import org.gradle.launcher.protocol.Build
import org.gradle.launcher.protocol.CommandComplete
import org.gradle.launcher.protocol.Result
import org.gradle.launcher.protocol.Stop
import org.gradle.logging.internal.OutputEventListener
import org.gradle.messaging.remote.internal.Connection
import spock.lang.Specification

class DaemonClientTest extends Specification {
    final DaemonConnector connector = Mock()
    final Connection<Object> connection = Mock()
    final BuildClientMetaData metaData = Mock()
    final OutputEventListener outputEventListener = Mock()
    final DaemonClient client = new DaemonClient(connector, metaData, outputEventListener)

    def stopsTheDaemonWhenRunning() {
        when:
        client.stop()

        then:
        1 * connector.maybeConnect() >> connection
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new CommandComplete(null)
        1 * connection.stop()
        0 * _._
    }

    def stopsTheDaemonWhenNotRunning() {
        when:
        client.stop()

        then:
        1 * connector.maybeConnect() >> null
        0 * _._
    }

    def rethrowsFailureToStopDaemon() {
        RuntimeException failure = new RuntimeException()

        when:
        client.stop()

        then:
        RuntimeException e = thrown()
        e == failure
        1 * connector.maybeConnect() >> connection
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new CommandComplete(failure)
        1 * connection.stop()
        0 * _._
    }

    def executesAction() {
        GradleLauncherAction<String> action = Mock()
        BuildActionParameters parameters = Mock()

        when:
        def result = client.execute(action, parameters)

        then:
        result == '[result]'
        1 * connector.connect() >> connection
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> new Result('[result]')
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
        1 * connector.connect() >> connection
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> new CommandComplete(failure)
        1 * connection.stop()
    }
}
