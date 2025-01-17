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
package org.gradle.launcher.daemon.configuration

import org.gradle.api.internal.file.TestFiles
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria
import spock.lang.Specification

import static java.lang.Boolean.parseBoolean

class DaemonParametersTest extends Specification {
    def userHomeDir = new File("gradle-user-home").absoluteFile
    def parameters = new DaemonParameters(userHomeDir, TestFiles.fileCollectionFactory())

    def "has reasonable default values"() {
        expect:
        parameters.enabled
        parameters.idleTimeout == DaemonParameters.DEFAULT_IDLE_TIMEOUT
        parameters.periodicCheckInterval == DaemonParameters.DEFAULT_PERIODIC_CHECK_INTERVAL_MILLIS
        parameters.baseDir == new File(userHomeDir, "daemon")
        parameters.systemProperties.isEmpty()
        parameters.effectiveJvmArgs.size() == 4 + 4 // 4 immutable system properties and 4 memory related properties
    }

    def "configuring jvmargs replaces the defaults"() {
        when:
        parameters.setJvmArgs(["-Xmx17m"])

        then:
        parameters.effectiveJvmArgs.intersect(parameters.DEFAULT_JVM_ARGS).empty
    }

    def "does not apply defaults when jvmargs already specified"() {
        when:
        parameters.setJvmArgs(["-Xmx17m"])
        parameters.requestedJvmCriteria = new DaemonJvmCriteria.Spec(JavaLanguageVersion.of(8), null, null)

        then:
        parameters.effectiveJvmArgs.containsAll(["-Xmx17m"])
        parameters.effectiveJvmArgs.intersect(parameters.DEFAULT_JVM_ARGS).empty
    }

    def "can apply defaults for Java 8 and later"() {
        when:
        parameters.requestedJvmCriteria = new DaemonJvmCriteria.Spec(JavaLanguageVersion.of(9), null, null)

        then:
        parameters.effectiveJvmArgs.containsAll(DaemonParameters.DEFAULT_JVM_ARGS)
    }

    def "can configure debug mode"() {
        when:
        parameters.setDebug(parseBoolean(flag))

        then:
        parameters.effectiveJvmArgs.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005") == parseBoolean(flag)

        where:
        flag << ["true", "false"]
    }

    def "debug mode is persisted when defaults are applied"() {
        when:
        parameters.setDebug(true)
        parameters.requestedJvmCriteria = new DaemonJvmCriteria.Spec(jvmDefault, null, null)

        then:
        parameters.effectiveJvmArgs.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")

        where:
        jvmDefault << [JavaLanguageVersion.of(8), JavaLanguageVersion.of(9)]
    }

    def "can configure debug port"() {
        given:
        parameters.setDebug(true)

        when:
        parameters.setDebugPort(port)

        then:
        parameters.effectiveJvmArgs.contains(debugArgument)

        where:
        port || debugArgument
        5005 || "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
        5006 || "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5006"
    }

    def "can configure debug suspend"() {
        given:
        parameters.setDebug(true)

        when:
        parameters.setDebugSuspend(suspend)

        then:
        parameters.effectiveJvmArgs.contains(debugArgument)

        where:
        suspend || debugArgument
        true    || "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
        false   || "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
    }

    def "can configure debug server"() {
        given:
        parameters.setDebug(true)

        when:
        parameters.setDebugServer(server)

        then:
        parameters.effectiveJvmArgs.contains(debugArgument)

        where:
        server || debugArgument
        true   || "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
        false  || "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=5005"
    }

    def "can enable the daemon"() {
        when:
        parameters.setEnabled(true)

        then:
        parameters.enabled
    }

    def "can explicitly disable the daemon"() {
        when:
        parameters.setEnabled(false)

        then:
        !parameters.enabled
    }
}
