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


import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule
import org.junit.rules.ExternalResource
import spock.lang.Issue

import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

@Requires(IntegTestPreconditions.CanKillProcess)
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
        poll(60) {
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
        poll(60) {
            server.tryStart(daemon.port)
        }

        then:
        //most fundamentally, stopping works ok:
        stopDaemonsNow()

        and:
        output.contains DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE
        output.contains DaemonMessages.UNABLE_TO_STOP_DAEMON

        when:
        stopDaemonsNow()

        then:
        !output.contains(DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE)
        !output.contains(DaemonMessages.UNABLE_TO_STOP_DAEMON)
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

    @Issue("GRADLE-2464")
    def "stop removes entry when nothing is listening on address"() {
        when:
        buildSucceeds()

        then:
        def daemon = daemons.daemon

        when:
        daemon.assertIdle()
        daemon.kill()

        then:
        stopDaemonsNow()

        and:
        output.contains(DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE)
        output.contains(DaemonMessages.NO_DAEMONS_RUNNING)

        and:
        def analyzer = daemons
        analyzer.daemons.size() == 1
        analyzer.visible.size() == 0
    }

    def "daemon drops connection when client request is badly formed"() {
        given:
        buildSucceeds()
        def daemon = daemons.daemon
        daemon.assertIdle()

        when:
        def socket = new Socket(new InetAddressFactory().localBindingAddress, daemon.port)
        socket.outputStream.write("GET / HTTP/1.0\n\n".getBytes())
        socket.outputStream.flush()

        then:
        // Nothing sent back
        socket.inputStream.read() == -1

        // Daemon is still running
        daemon.becomesIdle()
        daemon.log.contains("Unable to receive command from client")

        when:
        // Ensure daemon is functional
        buildSucceeds()

        then:
        daemon.assertIdle()
        daemons.daemons.size() == 1

        cleanup:
        socket?.close()
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
            try {
                socket?.close()
            } catch (IOException ex) {
                ex.printStackTrace()
            }
            acceptor?.join()
        }
    }
}
