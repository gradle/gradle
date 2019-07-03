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

package org.gradle.integtests.tooling.r56

import com.sun.jdi.Bootstrap
import com.sun.jdi.VirtualMachine
import com.sun.jdi.connect.Connector
import com.sun.jdi.connect.ListeningConnector
import com.sun.jdi.connect.TransportTimeoutException
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import spock.lang.Timeout


@ToolingApiVersion(">=5.6")
@TargetGradleVersion(">=5.6")
@Timeout(60)
class TestLauncherDebuggingCrossVersionTest extends ToolingApiSpecification {

    // TODO extract common elements to setup()
    // TODO use async api to connect to VirtualMachine

    def setup() {
        buildFile << """
            plugins { id 'java-library' }
            ${mavenCentralRepository()}
            dependencies { testCompile 'junit:junit:4.12' }
        """
        file('src/test/java/example/MyTest.java').text = """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
        file('src/test/java/example/SecondTest.java').text = """
            package example;
            public class SecondTest {
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
    }

    def "build fails if debugger is not ready"() {
        when:
        withConnection { connection ->
            connection.newTestLauncher()
                .withJvmTestClasses("example.MyTest")
                .withDebugOptions(findFreePort())
                .run()
        }

        then:
        thrown(BuildException)
    }

    def "can launch tests in debug mode"() {
        setup:
        def (connector, port, arguments) = startSocketListenDebugSession()

        when:
        withConnection { connection ->
            connection.newTestLauncher()
                .withJvmTestClasses("example.MyTest")
                .withDebugOptions(port)
                .run()
        }

        then:
        true // test successfully executed with debugger attached

        cleanup:
        connector.stopListening(arguments)
    }

    def "Overwrites configuration from --debug-jvm parameter"() {
        setup:
        def (connector, port, arguments) = startSocketListenDebugSession()

        when:
        withConnection { connection ->
            connection.newTestLauncher()
                .withJvmTestClasses("example.MyTest")
                .withDebugOptions(port)
                .addJvmArguments("--debug-jvm")
                .run()
        }

        then:
        true // test successfully executed with debugger attached

        cleanup:
        connector.stopListening(arguments)
    }

    def "Forks only one JVM to debug"() {
        setup:
        buildFile << """
             tasks.withType(Test) {
                  forkEvery = 1
                  maxParallelForks = 2
            }
        """
        def (connector, port, arguments) = startSocketListenDebugSession()

        when:
        withConnection { connection ->
            connection.newTestLauncher()
                .withJvmTestClasses("example.MyTest", "example.SecondTest")
                .withDebugOptions(port)
                .run()
        }

        then:
        true // test successfully executed with debugger attached

        cleanup:
        connector.stopListening(arguments)
    }

    int findFreePort() {
        new ServerSocket(0).withCloseable { socket ->
            try {
                return socket.getLocalPort()
            } catch (IOException e) {
                throw new RuntimeException("Cannot find free port", e)
            }
        }
    }

    def startSocketListenDebugSession() {
        int port = findFreePort()

        ListeningConnector connector = Bootstrap.virtualMachineManager().listeningConnectors().find { it.name() == "com.sun.jdi.SocketListen" }

        Map<String, Connector.Argument> arguments = connector.defaultArguments()
        Connector.Argument param = arguments.get("port")
        param.setValue(String.valueOf(port))
        Connector.Argument timeout = arguments.get("timeout")
        timeout.setValue("3000") // 3 seconds

        connector.startListening(arguments)

        Thread.start {
            VirtualMachine vm
            while (!vm) {
                try {
                    vm = connector.accept(arguments)
                } catch (TransportTimeoutException e) {
                }
            }
        }

        [connector, port, arguments]
    }
}
