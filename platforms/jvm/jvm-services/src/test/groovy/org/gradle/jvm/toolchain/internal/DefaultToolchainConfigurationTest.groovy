/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.jvm.toolchain.internal

import org.gradle.internal.SystemProperties
import org.gradle.internal.os.OperatingSystem
import spock.lang.Specification

class DefaultToolchainConfigurationTest extends Specification {
    private SystemProperties systemProperties = Stub()
    private Map<String, String> environment = [:]

    def setup() {
        systemProperties.getUserHome() >> "/home/user"
    }

    def "configuration has reasonable defaults"() {
        given:
        def configuration = new DefaultToolchainConfiguration(OperatingSystem.LINUX, systemProperties, environment)

        expect:
        configuration.autoDetectEnabled
        configuration.downloadEnabled
        configuration.installationsFromPaths.isEmpty()
        configuration.javaInstallationsFromEnvironment.isEmpty()

        configuration.asdfDataDirectory == new File("/home/user/.asdf")
        configuration.intelliJdkDirectory == new File("/home/user/.jdks")
        configuration.sdkmanCandidatesDirectory == new File("/home/user/.sdkman/candidates")
        configuration.jabbaHomeDirectory == null
    }

    def "configuration has reasonable defaults on macOS"() {
        given:
        def configuration = new DefaultToolchainConfiguration(OperatingSystem.MAC_OS, systemProperties, environment)

        expect:
        // This is the only property that's different between OSes
        configuration.intelliJdkDirectory == new File("/home/user/Library/Java/JavaVirtualMachines")
    }

    def "environment variables influence configuration"() {
        given:
        environment.put("ASDF_DATA_DIR", "/other/.asdf")
        environment.put("JABBA_HOME", "/other/.jabba")
        environment.put("SDKMAN_CANDIDATES_DIR", "/other/.sdkman/candidates")

        def configuration = new DefaultToolchainConfiguration(OperatingSystem.LINUX, systemProperties, environment)

        expect:
        configuration.asdfDataDirectory == new File("/other/.asdf")
        configuration.jabbaHomeDirectory == new File("/other/.jabba")
        configuration.sdkmanCandidatesDirectory == new File("/other/.sdkman/candidates")
    }

    def "configuration can be changed programmatically"() {
        def configuration = new DefaultToolchainConfiguration(OperatingSystem.LINUX, systemProperties, environment)

        when:
        configuration.autoDetectEnabled = false
        configuration.downloadEnabled = false
        configuration.intelliJdkDirectory = new File("/other/.jdks")
        configuration.installationsFromPaths = ["/path/to/java"]
        configuration.javaInstallationsFromEnvironment = ["JDK8", "JDK11"]

        then:
        configuration.intelliJdkDirectory == new File("/other/.jdks")
        !configuration.autoDetectEnabled
        !configuration.downloadEnabled
        configuration.installationsFromPaths.size() == 1
        configuration.javaInstallationsFromEnvironment.size() == 2
    }
}
