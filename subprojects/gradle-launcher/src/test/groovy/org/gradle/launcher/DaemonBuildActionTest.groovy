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

import org.gradle.StartParameter
import org.gradle.initialization.ParsedCommandLine
import org.gradle.launcher.protocol.Build
import org.gradle.launcher.protocol.CommandComplete
import org.gradle.logging.internal.OutputEventListener
import org.gradle.messaging.remote.internal.Connection
import spock.lang.Specification

class DaemonBuildActionTest extends Specification {
    final DaemonConnector connector = Mock()
    final OutputEventListener listener = Mock()
    final ExecutionListener completer = Mock()
    final ParsedCommandLine commandLine = Mock()
    final StartParameter startParameter = new StartParameter()
    final DaemonBuildAction action = new DaemonBuildAction(listener, connector, startParameter, commandLine)

    def runsBuildUsingDaemon() {
        Connection<Object> connection = Mock()

        when:
        action.execute(completer)

        then:
        1 * connector.connect(startParameter) >> connection
        1 * connection.dispatch({!null}) >> { args ->
            Build build = args[0]
            assert build.currentDir == startParameter.currentDir
            assert build.args == commandLine
        }
        1 * connection.receive() >> new CommandComplete(null)
        1 * connection.stop()
        0 * _._
    }
}
