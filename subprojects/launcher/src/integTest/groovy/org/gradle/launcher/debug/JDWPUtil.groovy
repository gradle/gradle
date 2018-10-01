/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.debug

import org.gradle.util.ports.DefaultPortDetector
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

    def connect() {
        if (vm == null) {
            def vmm = Class.forName("com.sun.jdi.Bootstrap").virtualMachineManager()
            def connection = vmm.attachingConnectors().find { "dt_socket".equalsIgnoreCase(it.transport().name()) }
            def connectionArgs = connection.defaultArguments()
            connectionArgs.get("port").setValue(port as String)
            connectionArgs.get("hostname").setValue(host)
            this.vm = connection.attach(connectionArgs)
        }
        return vm
    }

    void close() {
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
    }
}
