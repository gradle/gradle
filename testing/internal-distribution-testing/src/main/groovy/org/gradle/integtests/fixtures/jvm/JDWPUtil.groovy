/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.fixtures.jvm

import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.util.ports.DefaultPortDetector
import org.gradle.util.ports.FixedAvailablePortAllocator
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import java.util.concurrent.atomic.AtomicReference

/**
 * A utility for communicating with a VM in debug mode using JDWP.
 */
class JDWPUtil implements TestRule {
    private static final int ATTACH_TIMEOUT_SECONDS = 30

    String host
    Integer port
    def vm
    def connection
    def connectionArgs

    JDWPUtil() {
        this(FixedAvailablePortAllocator.instance.assignPort())
    }

    JDWPUtil(Integer port) {
        this("127.0.0.1", port)
        Assume.assumeTrue(new DefaultPortDetector().isAvailable(port))
    }

    JDWPUtil(String host, Integer port) {
        this.host = host
        this.port = port
    }

    @Override
    Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            void evaluate() throws Throwable {
                base.evaluate()
                close()
            }
        }
    }

    def getPort() {
        port
    }

    def connect() {
        if (vm == null) {
            def vmm = bootstrapClass.virtualMachineManager()
            def connector = vmm.attachingConnectors().find { "dt_socket".equalsIgnoreCase(it.transport().name()) }
            def connectionArgs = connector.defaultArguments()
            connectionArgs.get("port").setValue(port as String)
            connectionArgs.get("hostname").setValue(host)
            this.vm = attachWithinTimeout(connector, connectionArgs)
        }
        vm
    }

    /**
     * Performs {@code attach()} with a hard wall-clock bound.
     *
     * <p>{@code attach()} does a blocking JDWP handshake. That read is <em>not</em> interruptible: the
     * connector's own {@code timeout} argument bounds only the TCP connect (not the handshake), and
     * {@code Thread.interrupt()} / a Spock {@code @Timeout} cannot unblock a plain socket read. A stalled
     * handshake would therefore hang until the build-level execution timeout. So run the attach on a
     * watchdog thread and give up on it after a fixed timeout, failing the test fast instead.
     * See <a href="https://github.com/gradle/gradle-private/issues/3612">gradle-private#3612</a>.
     */
    private attachWithinTimeout(connector, connectionArgs) {
        def result = new AtomicReference()
        def failure = new AtomicReference()
        def attachThread = new Thread({
            try {
                result.set(connector.attach(connectionArgs))
            } catch (Throwable t) {
                failure.set(t)
            }
        }, "jdwp-attach-${host}:${port}")
        attachThread.daemon = true // a stuck native read can't be interrupted, so let it leak until the debuggee dies
        attachThread.start()
        attachThread.join(ATTACH_TIMEOUT_SECONDS * 1000L)
        if (attachThread.alive) {
            throw new IllegalStateException("Timed out after ${ATTACH_TIMEOUT_SECONDS}s attaching the debugger to ${host}:${port}; the JDWP handshake stalled")
        }
        if (failure.get() != null) {
            throw failure.get()
        }
        return result.get()
    }

    def listen(boolean acceptAsync = true) {
        def vmm = bootstrapClass.virtualMachineManager()
        connection = vmm.listeningConnectors().find { it.name() == "com.sun.jdi.SocketListen" }
        connectionArgs = connection.defaultArguments()
        connectionArgs.get("port").setValue(port as String)
        connectionArgs.get("timeout").setValue('3000')
        connection.startListening(connectionArgs)

        if (acceptAsync) {
            Thread.start {
                accept()
            }
        }
    }

    def accept() {
        while (vm == null) {
            try {
                vm = connection.accept(connectionArgs)
                connection = null
            } catch (Exception e) {
            }
        }
    }

    /**
     * This is used to resume the VM after events are triggered, which can cause the VM to be suspended.
     *
     * @param isAlive Whether the VM is still alive.
     */
    void asyncResumeWhile(Closure<Boolean> isAlive) {
        def vmRef = vm
        Thread.start("VM listener") {
            while (isAlive()) {
                def event = vmRef.eventQueue().remove(100)
                if (event == null) {
                    continue
                }
                event.resume()
            }
        }
    }

    void resume() {
        vm.resume()
    }

    void close() {
        if (connection && connectionArgs) {
            connection.stopListening(connectionArgs)
        }

        if (vm != null) {
            try {
                vm.dispose()
            } catch (Exception e) {
                if (e.class.getName() == "com.sun.jdi.VMDisconnectedException") {
                    // This is ok - we're just trying to make sure all resources are released and threads
                    // have been resumed - this implies the VM has exited already.
                } else {
                    throw e
                }
            }
        }

        FixedAvailablePortAllocator.instance.releasePort(port)
    }

    // We do this to work around an issue in JDK 8 where tools.jar doesn't show up on the classpath and we
    // get a ClassDefNotFound error.
    static def getBootstrapClass() {
        if (JavaVersion.current().isJava9Compatible()) {
            return Class.forName("com.sun.jdi.Bootstrap")
        } else {
            ClassLoader classLoader = new URLClassLoader(Jvm.current().toolsJar.toURI().toURL())
            return classLoader.loadClass("com.sun.jdi.Bootstrap")
        }
    }

    /**
     * Returns an address of a real, non-loopback network interface suitable for attaching a debugger over
     * the network, or {@code null} if none can be found.
     *
     * <p>Uses the address of the interface that carries the default route (the real NIC) rather than the
     * first non-loopback address that happens to be enumerated. On CI agents that first address is frequently
     * a virtual bridge (docker0 = 172.17.0.1), and on dev machines a VPN tunnel (utunN) - neither is reliably
     * usable for a JDWP connection, which made the debug integration tests hang.
     * See <a href="https://github.com/gradle/gradle-private/issues/3612">gradle-private#3612</a>.
     */
    static String nonLoopbackAddress() {
        println("Looking at network interfaces")
        def address = primaryNetworkAddress() ?: firstNonLoopbackAddress()
        println("using address=$address")
        return address
    }

    private static String primaryNetworkAddress() {
        try {
            return new DatagramSocket().withCloseable { socket ->
                // A UDP "connect" only performs a routing-table lookup (no packets are sent), so the socket's
                // local address becomes the address of the interface that would be used to reach the target.
                socket.connect(InetAddress.getByName("203.0.113.1"), 9) // TEST-NET-3 (RFC 5737): never routed
                def local = socket.localAddress
                (local instanceof Inet4Address && !local.anyLocalAddress && !local.loopbackAddress) ? local.hostAddress : null
            }
        } catch (Exception ignored) {
            return null
        }
    }

    private static String firstNonLoopbackAddress() {
        return Collections.list(NetworkInterface.getNetworkInterfaces())
            .collectMany { it.isLoopback() ? [] : Collections.list(it.inetAddresses) }
            .find { it instanceof Inet4Address && !it.isLoopbackAddress() }
            ?.hostAddress
    }
}
