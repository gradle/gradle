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

package org.gradle.launcher.daemon

import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.integtests.fixtures.DaemonController
import org.gradle.integtests.fixtures.RegistryBackedDaemonController
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.client.DaemonClientServices
import org.gradle.launcher.daemon.client.DaemonConnector
import org.gradle.launcher.daemon.protocol.Sleep
import org.gradle.launcher.daemon.protocol.Stop
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.logging.internal.OutputEventListener
import org.gradle.messaging.remote.internal.Connection
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Timeout
import static org.gradle.util.ConcurrentSpecification.poll

/**
 * @author: Szczepan Faber, created at: 8/18/11
 */
class DaemonFunctionalTest extends Specification {

    @Rule public final TemporaryFolder temp = new TemporaryFolder()
    DaemonConnector connector
    DaemonController reg
    List<Connection> cleanMe = []
    OutputEventListener listener = Mock()

    //cannot use setup() because temp folder will get the proper name
    def prepare() {
        //connector with short-lived daemons
        def clientServices = new DaemonClientServices(temp.testDir, 10000)
        connector = clientServices.get(DaemonConnector)
        reg = new RegistryBackedDaemonController(clientServices.get(DaemonRegistry))
    }

    def cleanup() {
        try {
            cleanMe*.stop()
            //although daemons have very short idle timeout we could still stop them at the end of the test
            //this way the test has quicker rerunability but unfortunately will make every test break when there's a bug in stopping logic
            //For now, let's just rely on the 10 secs. idle timeout.
            //new DaemonClient(connector, new GradleLauncherMetaData(), listener).stop()
        } finally {
            //daemon log will be printed only when the test fails
            //because for some reason passing test's cleanup is called after the temp folder rule cleans up
            printDaemonLog()
        }
    }

    @Timeout(20) //healthy timeout just in case
    def "expired daemons are removed from registry"() {
        //we could write a proper test for expiration and look if processes are finished.
        //At the moment only windows build detects this potential problem because Windows is less forgiving :)
        prepare()

        when:
        connector = new DaemonClientServices(temp.testDir, 2000).get(DaemonConnector) //2000 sec expiry time
        def c = connect()
        c.dispatch(new Sleep(1000))

        then:
        reg.isDaemonsBusy(1)

        when:
        connect()

        then:
        reg.isDaemonsRunning(2)

        and:
        //after some time...
        reg.isDaemonsRunning(0)
    }

    @Timeout(20)
    def "knows status of the daemon"() {
        when:
        prepare()

        then:
        reg.isDaemonsRunning(0)
        reg.isDaemonsIdle(0)
        reg.isDaemonsBusy(0)

        when:
        def connection = connect()

        then:
        reg.isDaemonsRunning(1)
        reg.isDaemonsIdle(1)
        reg.isDaemonsBusy(0)

        when:
        def sleep = new Sleep(500)
        connection.dispatch(sleep)

        then:
        reg.isDaemonsRunning(1)
        reg.isDaemonsBusy(1)
        reg.isDaemonsIdle(0)

        and:
        //after some some time...
        reg.isDaemonsRunning(1)
        reg.isDaemonsIdle(1)
        reg.isDaemonsBusy(0)
    }

    @Timeout(20)
    def "spins new daemon if all are busy"() {
        when:
        prepare()
        def c = connect()

        then:
        reg.isDaemonsIdle(1)

        when:
        c.dispatch(new Sleep(500))

        then:
        reg.isDaemonsBusy(1)

        when:
        connect()

        then:
        reg.isDaemonsRunning(2)

        //daemons log separately:
        assert reg.daemonLogs.size() == 2
    }

    @Timeout(20)
    def "stops idle daemon"() {
        prepare()

        when:
        def connection = connect()

        then:
        reg.isDaemonsIdle(1)

        when:
        new DaemonClient(connector, new GradleLauncherMetaData(), listener).stop()

        then:
        reg.isDaemonsRunning(0)
    }

    @Timeout(20)
    def "stops busy daemon"() {
        prepare()

        when:
        def c = connect()
        c.dispatch(new Sleep(3000))

        then:
        reg.isDaemonsBusy(1)

        when:
        new DaemonClient(connector, new GradleLauncherMetaData(), listener).stop()

        then:
        reg.isDaemonsRunning(0)
    }

    @Timeout(20)
    def "stops all daemons"() {
        prepare()

        when:
        def connection = connect()
        connection.dispatch(new Sleep(5000))

        then:
        reg.isDaemonsBusy(1)

        when:
        def connectionTwo = connect()
        connectionTwo.dispatch(new Sleep(5000))

        then:
        reg.isDaemonsBusy(2)

        when:
        def connectionThree = connect()

        then:
        reg.isDaemonsRunning(3)
        reg.isDaemonsBusy(2)
        reg.isDaemonsIdle(1)

        when:
        new DaemonClient(connector, new GradleLauncherMetaData(), listener).stop()

        then:
        reg.isDaemonsRunning(0)
    }

    private Connection<Object> connect() {
        def c = connector.connect()
        cleanMe << c
        return c
    }

    void printDaemonLog() {
        print(getDaemonLog())
    }

    String getDaemonLog() {
        String out = ''
        int i = 1;
        reg.daemonLogs.each {
            out += "\n"
            out += "*** daemon #$i log:\n"
            out += "$it.text\n"
            i++
        }
        return out
    }
}
