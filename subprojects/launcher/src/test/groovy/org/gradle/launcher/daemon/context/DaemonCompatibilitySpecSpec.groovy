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
package org.gradle.launcher.daemon.context


import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.os.OperatingSystem
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.ConfigureUtil
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

class DaemonCompatibilitySpecSpec extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    def clientConfigure = {}
    def serverConfigure = {}

    def client(@DelegatesTo(DaemonContextBuilder) Closure c) {
        clientConfigure = c
    }

    def server(@DelegatesTo(DaemonContextBuilder) Closure c) {
        serverConfigure = c
    }

    def createContext(Closure config) {
        def builder = new DaemonContextBuilder({ 12L } as ProcessEnvironment)
        builder.daemonRegistryDir = new File("dir")
        ConfigureUtil.configure(config, builder).create()
    }

    def getClientContext() {
        createContext(clientConfigure)
    }

    def getServerContext() {
        createContext(serverConfigure)
    }

    private boolean isCompatible() {
        new DaemonCompatibilitySpec(clientContext).isSatisfiedBy(serverContext)
    }

    private String getUnsatisfiedReason() {
        new DaemonCompatibilitySpec(clientContext).whyUnsatisfied(serverContext)
    }

    def "default contexts are compatible"() {
        expect:
        compatible
        !unsatisfiedReason
    }

    def "contexts with different java homes are incompatible"() {
        client {
            javaHome = tmp.createDir("client")
            javaHome.file("bin", OperatingSystem.current().getExecutableName("java")).touch()
        }
        server {
            javaHome = tmp.createDir("server")
            javaHome.file("bin", OperatingSystem.current().getExecutableName("java")).touch()
        }

        expect:
        !compatible
        unsatisfiedReason.contains "Java home is different"
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "contexts with symlinked javaHome are compatible"() {
        // Make something that looks like a Java installation
        def jdk = tmp.testDirectory.file("jdk").createDir()
        jdk.file("bin/java").touch()

        def linkToJdk = tmp.testDirectory.file("link")
        linkToJdk.createLink(jdk)

        assert jdk != linkToJdk
        assert linkToJdk.exists()
        assert jdk.canonicalFile == linkToJdk.canonicalFile

        client { javaHome = jdk }
        server { javaHome = linkToJdk }

        expect:
        compatible

        cleanup:
        assert linkToJdk.delete()
    }

    def "contexts with same daemon opts are compatible"() {
        client { daemonOpts = ["-Xmx256m", "-Dfoo=foo"] }
        server { daemonOpts = ["-Xmx256m", "-Dfoo=foo"] }

        expect:
        compatible
    }

    def "contexts with same daemon opts but different order are compatible"() {
        client { daemonOpts = ["-Xmx256m", "-Dfoo=foo"] }
        server { daemonOpts = ["-Dfoo=foo", "-Xmx256m"] }

        expect:
        compatible
    }

    def "contexts with different quantity of opts are not compatible"() {
        client { daemonOpts = ["-Xmx256m", "-Dfoo=foo"] }
        server { daemonOpts = ["-Xmx256m"] }

        expect:
        !compatible
        unsatisfiedReason.contains "At least one daemon option is different"
    }

    def "contexts with different daemon opts are incompatible"() {
        client { daemonOpts = ["-Xmx256m", "-Dfoo=foo"] }
        server { daemonOpts = ["-Xmx256m", "-Dfoo=bar"] }

        expect:
        !compatible
    }

    def "contexts with different priority"() {
        client { priority = DaemonParameters.Priority.LOW }
        server { priority = DaemonParameters.Priority.NORMAL }

        expect:
        !compatible
        unsatisfiedReason.contains "Process priority is different"
    }

    def "context with different agent status"() {
        client { applyInstrumentationAgent = clientStatus }
        server { applyInstrumentationAgent = !clientStatus }

        expect:
        !compatible
        unsatisfiedReason.contains "Agent status is different"

        where:
        clientStatus << [true, false]
    }

    def "context with same agent status"() {
        client { applyInstrumentationAgent = status }
        server { applyInstrumentationAgent = status }

        expect:
        compatible

        where:
        status << [true, false]
    }
}
