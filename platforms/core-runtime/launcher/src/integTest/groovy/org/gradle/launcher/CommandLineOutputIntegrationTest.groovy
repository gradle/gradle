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

package org.gradle.launcher

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.internal.jvm.Jvm

/**
 * Assertions over the output of certain command line invocations.
 */
class CommandLineOutputIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture {
    def setup() {
        // forces the tests to fork and not run in-process
        executer.requireDaemon().requireIsolatedDaemons()
    }

    def "displays version message appropriately for daemon JVM with no configuration"() {
        when:
        succeeds("--version")

        then:
        outputContains("Daemon JVM:    ${Jvm.current().javaHome.absolutePath} (no JDK specified, using current Java home)")
    }

    def "displays version message appropriately for daemon JVM requested by org.gradle.java.home"() {
        given:
        file("gradle.properties").writeProperties("org.gradle.java.home": Jvm.current().javaHome.absolutePath)

        when:
        succeeds("--version")

        then:
        outputContains("Daemon JVM:    ${Jvm.current().javaHome.absolutePath} (from org.gradle.java.home)")
    }

    def "displays version message appropriately for daemon JVM requested by build criteria"() {
        given:
        writeJvmCriteria(JavaVersion.VERSION_17)

        when:
        succeeds("--version")

        then:
        outputContains("Daemon JVM:    Compatible with Java 17, any vendor (from gradle/gradle-daemon-jvm.properties)")
    }
}
