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
        printDaemonLog()
    }

    @Timeout(5) //healthy timeout just in case
    def "daemons expire"() {
        when:
        prepare()
        connector = new DaemonConnector(temp.testDir, 500) //0.5 sec expiry time
        def c = connect()

        then:
        poll { assert reg.all.size() == 1 }

        when:
        c.stop()

        then:
        poll {
            assert reg.idle.size() == 1
        }

        poll(2000) {
            assert reg.all.size() == 0
        }
    }

    @Timeout(5)
    def "knows status of the daemon"() {
        when:
        prepare()

        then:
        assert reg.busy.size() == 0
        assert reg.idle.size() == 0

        when:
        def connection = connect()

        then:
        poll {
            assert reg.busy.size() == 1
            assert reg.idle.size() == 0
        }

        when:
        connection.stop();

        then:
        poll {
            assert reg.busy.size() == 0
            assert reg.idle.size() == 1
        }
    }

    @Timeout(5)
    def "spins new daemon if all are busy"() {
        when:
        prepare()

        then:
        assert reg.busy.size() == 0
        assert reg.idle.size() == 0

        when:
        def connection = connect()

        then:
        poll {
            assert reg.busy.size() == 1
            assert reg.idle.size() == 0
        }

        when:
        def connectionTwo = connect()

        then:
        poll {
            assert reg.busy.size() == 2
            assert reg.idle.size() == 0
        }
    }

    static class AddressStub implements Address {
        String getDisplayName() {
            return "foo";
        }
    }

    @Timeout(5)
    def "registry deletes the bin files"() {
        prepare()

        def daemonRegistry = new DaemonRegistry(temp.dir)
        def reg = daemonRegistry.newRegistry();


        reg.store(new AddressStub());

        when:
        reg.remove();

        then:
        daemonRegistry.all.size() == 0
        //TODO SF - not yet implemented
        //daemonRegistry.registryFolder.list().length == 0
    }

    @Timeout(5)
    def "cleans up registry"() {
        prepare()

        when:
        def connection = connect()
        then:
        poll { assert reg.all.size() == 1 }

        when:
        connection.dispatch(new Stop(new GradleLauncherMetaData()))

        then:
        poll {
            assert reg.all.size() == 0
        }
    }

    @Timeout(5)
    def "stops all daemons"() {
        prepare()
        OutputEventListener listener = Mock()

        when:
        def connection = connect()

        then:
        poll { assert reg.busy.size() == 1 }

        when:
        def connectionTwo = connect()

        then:
        poll { assert reg.busy.size() == 2 }

        when:
        def connectionThree = connect()

        then:
        poll { assert reg.busy.size() == 3 }

        when:
        connectionTwo.stop()
        connectionThree.stop()

        then:
        poll {
            assert reg.idle.size() == 2
            assert reg.busy.size() == 1
        }

        when:
        new DaemonClient(connector, new GradleLauncherMetaData(), listener).stop()

        then:
        poll {
            assert reg.all.size() == 0
        }
    }

    private Connection<Object> connect() {
        def c = connector.connect()
        cleanMe << c
        return c
    }

    void printDaemonLog() {
        int i = 1;
        reg.daemonDir.logs.each {
            println ""
            println "*** daemon #$i log:"
            println it.text
            i++
        }
    }

    //simplistic polling assertion. attempts asserting every x millis up to some max timeout
    void poll(int timeout = 1000, Closure assertion) {
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
