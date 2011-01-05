/*
 * Copyright 2010 the original author or authors.
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

import spock.lang.Specification
import org.gradle.logging.internal.OutputEventListener
import org.gradle.messaging.remote.internal.Connection
import org.gradle.launcher.protocol.Stop
import org.gradle.launcher.protocol.CommandComplete
import org.gradle.initialization.BuildClientMetaData

class StopDaemonActionTest extends Specification {
    final DaemonConnector connector = Mock()
    final OutputEventListener outputListener = Mock()
    final ExecutionListener executionListener = Mock()
    final BuildClientMetaData clientMetaData = Mock()
    final StopDaemonAction action = new StopDaemonAction(connector, outputListener, clientMetaData)

    def executesStopCommand() {
        Connection<Object> connection = Mock()

        when:
        action.execute(executionListener)

        then:
        1 * connector.maybeConnect() >> connection
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new CommandComplete(null)
        1 * connection.stop()
        0 * _._
    }

    def doesNothingWhenDaemonIsNotRunning() {
        when:
        action.execute(executionListener)

        then:
        1 * connector.maybeConnect() >> null
        0 * _._
    }

    def reportsFailureToStop() {
        Connection<Object> connection = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        action.execute(executionListener)

        then:
        1 * connector.maybeConnect() >> connection
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new CommandComplete(failure)
        1 * connection.stop()
        1 * executionListener.onFailure(failure)
        0 * _._
    }
}
