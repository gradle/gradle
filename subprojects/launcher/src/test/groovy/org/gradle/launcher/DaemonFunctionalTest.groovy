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

import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.launcher.protocol.Stop
import org.gradle.logging.internal.OutputEventListener
import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.Connection
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Timeout

/**
 * @author: Szczepan Faber, created at: 8/18/11
 */
class DaemonFunctionalTest extends Specification {

    @Rule public final TemporaryFolder temp = new TemporaryFolder()
    DaemonConnector connector
    DaemonRegistry reg
    List<Connection> cleanMe = []

    //cannot use setup() because temp folder will get the proper name
    def prepare() {
        //connector with short-lived daemons
        connector = new DaemonConnector(temp.testDir, 10000)
        reg = connector.daemonRegistry
    }

    def cleanup() {
        cleanMe.each { it.stop() }
    }

    @Ignore //deamons do not seem to expire
    def "daemons expire"() {
        when:
        prepare()

        connector = new DaemonConnector(temp.testDir, 1000) //1 sec expiry time
        def c = connect()
        poll { assert reg.busy.size() == 1 }

        c.stop()
        poll {
            assert reg.busy.size() == 0
            assert reg.idle.size() == 1
        }

        poll(2000) {
            assert reg.busy.size() == 0
            assert reg.idle.size() == 0
        }

        then: 1==1 //TODO SF - to keep spock happy
    }

    def "knows status of the daemon"() {
        when:
        prepare()

        assert reg.busy.size() == 0
        assert reg.idle.size() == 0

        def connection = connect()

        poll {
            assert reg.busy.size() == 1
            assert reg.idle.size() == 0
        }

        connection.stop();

        poll {
            assert reg.busy.size() == 0
            assert reg.idle.size() == 1
        }

        then: 1==1
    }

    def "spins new daemon if all are busy"() {
        when:
        prepare()

        assert reg.busy.size() == 0
        assert reg.idle.size() == 0

        def connection = connect()

        poll {
            assert reg.busy.size() == 1
            assert reg.idle.size() == 0
        }

        def connectionTwo = connect()

        poll {
            assert reg.busy.size() == 2
            assert reg.idle.size() == 0
        }

        then: 1==1
    }

    static class AddressStub implements Address {
        String getDisplayName() {
            return "foo";
        }
    }

    def "registry deletes the bin files"() {
        expect:
        prepare()

        def daemonRegistry = new DaemonRegistry(temp.dir)
        def reg = daemonRegistry.newRegistry();

        reg.store(new AddressStub());
        reg.remove();

        daemonRegistry.all.size() == 0
        //TODO SF - not yet implemented
        //daemonRegistry.registryFolder.list().length == 0
    }

    def "cleans up registry"() {
        when:
        prepare()

        def connection = connect()
        poll { assert reg.all.size() == 1 }

        connection.dispatch(new Stop(new GradleLauncherMetaData()))

        poll {
            assert reg.all.size() == 0
        }

        then: 1==1
    }

    @Timeout(10) //healthy timeout just in case
    def "stops all daemons"() {
        when:
        prepare()

        OutputEventListener listener = Mock()

        def connection = connect()
        poll { assert reg.busy.size() == 1 }

        def connectionTwo = connect()
        poll { assert reg.busy.size() == 2 }

        def connectionThree = connect()
        poll { assert reg.busy.size() == 3 }

        connectionTwo.stop()
        connectionThree.stop()

        poll {
            assert reg.idle.size() == 2
            assert reg.busy.size() == 1
        }

        new DaemonClient(connector, new GradleLauncherMetaData(), listener).stop()

        poll {
            assert reg.all.size() == 0
        }

        then: 1==1
    }

    private Connection<Object> connect() {
        def c = connector.connect()
        cleanMe << c
        return c
    }

    //simplistic polling assertion. attempts asserting every x millis up to some max timeout
    def poll(int timeout = 1000, Closure assertion) {
        int x = 0;
        while(true) {
            try {
                assertion()
                return
            } catch (Throwable t) {
                Thread.sleep(100);
                x += 100;
                if (x > timeout) {
                    throw t;
                }
            }
        }
    }
}
