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

/**
 * A utility for communicating with a VM in debug mode using JDWP.
 */
class JDWPUtil implements TestRule {
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
            def connection = vmm.attachingConnectors().find { "dt_socket".equalsIgnoreCase(it.transport().name()) }
            def connectionArgs = connection.defaultArguments()
            connectionArgs.get("port").setValue(port as String)
            connectionArgs.get("hostname").setValue(host)
            this.vm = connection.attach(connectionArgs)
        }
        vm
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
}
