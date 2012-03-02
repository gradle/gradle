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

import org.gradle.api.specs.Spec
import org.gradle.initialization.BuildClientMetaData
import org.gradle.initialization.GradleLauncherAction
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.logging.internal.OutputEventListener
import org.gradle.messaging.remote.internal.Connection
import spock.lang.Specification
import org.gradle.launcher.daemon.protocol.*

class DaemonClientTest extends Specification {
    final DaemonConnector connector = Mock()
    final DaemonConnection daemonConnection = Mock()
    final Connection<Object> connection = Mock()
    final BuildClientMetaData metaData = Mock()
    final OutputEventListener outputEventListener = Mock()
    final Spec<DaemonContext> compatibilitySpec = Mock()
    final DaemonClient client = new DaemonClient(connector, metaData, outputEventListener, compatibilitySpec, System.in)

    def setup() {
        daemonConnection.getConnection() >> connection
    }

    def stopsTheDaemonWhenRunning() {
        when:
        client.stop()

        then:
        2 * connector.maybeConnect(compatibilitySpec) >>> [daemonConnection, null]
        1 * connection.dispatch({it instanceof Stop})
        1 * connection.receive() >> new Success(null)
        1 * connection.stop()
        daemonConnection.getConnection() >> connection // why do I need this? Why doesn't the interaction specified in setup cover me?
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
        when:
        client.stop()

        then:
        3 * connector.maybeConnect(compatibilitySpec) >>> [daemonConnection, daemonConnection, null]
        2 * connection.dispatch({it instanceof Stop})
        2 * connection.receive() >> new Success(null)
    }

    def executesAction() {
        when:
        def result = client.execute(Mock(GradleLauncherAction), Mock(BuildActionParameters))

        then:
        result == '[result]'
        1 * connector.connect(compatibilitySpec) >> daemonConnection
        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [Mock(BuildStarted), new Success('[result]')]
        1 * connection.stop()
    }

    def rethrowsFailureToExecuteAction() {
        GradleLauncherAction<String> action = Mock()
        BuildActionParameters parameters = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        client.execute(Mock(GradleLauncherAction), Mock(BuildActionParameters))

        then:
        RuntimeException e = thrown()
        e == failure
        1 * connector.connect(compatibilitySpec) >> daemonConnection
        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [Mock(BuildStarted), new CommandFailure(failure)]
        1 * connection.stop()
    }
    
    def "tries to find a different daemon if getting the first result from the daemon fails"() {
        when:
        client.execute(Mock(GradleLauncherAction), Mock(BuildActionParameters))

        then:
        2 * connector.connect(compatibilitySpec) >> daemonConnection
        connection.dispatch({it instanceof Build}) >> { throw new RuntimeException("Boo!")} >> { /* success */ }
        2 * connection.receive() >>> [Mock(BuildStarted), new Success('')]
    }

    def "tries to find a different daemon if the daemon is busy"() {
        when:
        client.execute(Mock(GradleLauncherAction), Mock(BuildActionParameters))

        then:
        2 * connector.connect(compatibilitySpec) >> daemonConnection
        connection.receive() >>> [Mock(DaemonBusy), Mock(BuildStarted), new Success('')]
    }

    def "tries to find a different daemon if the first result is null"() {
        when:
        client.execute(Mock(GradleLauncherAction), Mock(BuildActionParameters))

        then:
        3 * connector.connect(compatibilitySpec) >> daemonConnection
        //first busy, then null, then build started...
        connection.receive() >>> [Mock(DaemonBusy), null, Mock(BuildStarted), new Success('')]
    }

    def "does not loop forever finding usable daemons"() {
        given:
        connector.connect(compatibilitySpec) >> daemonConnection
        connection.receive() >> Mock(DaemonBusy)
        
        when:
        client.execute(Mock(GradleLauncherAction), Mock(BuildActionParameters))

        then:
        thrown(NoUsableDaemonFoundException)
    }
}
