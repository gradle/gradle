/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.KillProcessAvailability
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.junit.Rule
import org.junit.rules.ExternalResource
import spock.lang.IgnoreIf
import spock.lang.Issue

import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

@IgnoreIf({ !KillProcessAvailability.CAN_KILL })
class DaemonInitialCommunicationFailureIntegrationSpec extends DaemonIntegrationSpec {

    @Rule TestServer server = new TestServer()

    @Issue("GRADLE-2444")
    def "behaves if the registry contains connectable port without daemon on the other end"() {
        when:
        buildSucceeds()

        then:
        //there should be one idle daemon
        def daemon = daemons.daemon

        when:
        // Ensure that the daemon has finished updating the registry. Killing it halfway through the registry update will leave the registry corrupted,
        // and the client will just throw the registry away and replace it with an empty one
        daemon.assertIdle()
        daemon.kill()

        and:
        //starting some service on the daemon port
        poll {
            server.tryStart(daemon.port)
        }

        then:
        //most fundamentally, the build works ok:
        buildSucceeds()

        and:
        output.contains DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE

        when:
        buildSucceeds()

        then:
        //suspected address was removed from the registry
        // so we should the client does not attempt to connect to it again
        !output.contains(DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE)
    }

    @Issue("GRADLE-2444")
    def "stop behaves if the registry contains connectable port without daemon on the other end"() {
        when:
        buildSucceeds()

        then:
        def daemon = daemons.daemon

        when:
        daemon.assertIdle()
        daemon.kill()
        poll {
            server.tryStart(daemon.port)
        }

        then:
        //most fundamentally, stopping works ok:
        stopDaemonsNow()

        and:
        output.contains DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE

        when:
        stopDaemonsNow()

        then:
        !output.contains(DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE)
        output.contains(DaemonMessages.NO_DAEMONS_RUNNING)
    }

    @Issue("GRADLE-2464")
    def "when nothing responds to the address found in the registry we remove the address"() {
        when:
        buildSucceeds()

        then:
        def daemon = daemons.daemon

        when:
        daemon.assertIdle()
        daemon.kill()

        then:
        buildSucceeds()

        and:
        def analyzer = daemons
        analyzer.daemons.size() == 2        //2 daemon participated
        analyzer.visible.size() == 1        //only one address in the registry
    }

    private static class TestServer extends ExternalResource {
        ServerSocketChannel socket;
        Thread acceptor;

        void tryStart(int port) {
            socket = ServerSocketChannel.open()
            socket.socket().bind(new InetSocketAddress(port))
            acceptor = new Thread() {
                @Override
                void run() {
                    while (true) {
                        SocketChannel connection
                        try {
                            connection = socket.accept()
                        } catch (IOException e) {
                            return
                        }
                        try {
                            connection.read(ByteBuffer.allocate(4096))
                            connection.write(ByteBuffer.wrap("hello".bytes))
                        } finally {
                            connection.close()
                        }
                    }
                }
            }
            acceptor.start()
        }

        @Override
        protected void after() {
            socket?.close()
            acceptor?.join()
        }
    }
}