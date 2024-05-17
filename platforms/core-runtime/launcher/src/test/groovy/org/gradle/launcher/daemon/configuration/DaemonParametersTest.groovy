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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.jvm.Jvm
import org.gradle.launcher.configuration.BuildLayoutResult
import spock.lang.Issue
import spock.lang.Specification

import static java.lang.Boolean.parseBoolean

class DaemonParametersTest extends Specification {
    def userHomeDir = new File("gradle-user-home").absoluteFile
    def buildLayoutResult = Stub(BuildLayoutResult) {
        getGradleUserHomeDir() >> userHomeDir
    }
    def parameters = new DaemonParameters(buildLayoutResult, TestFiles.fileCollectionFactory())

    def "has reasonable default values"() {
        expect:
        parameters.enabled
        parameters.idleTimeout == DaemonParameters.DEFAULT_IDLE_TIMEOUT
        parameters.periodicCheckInterval == DaemonParameters.DEFAULT_PERIODIC_CHECK_INTERVAL_MILLIS
        parameters.baseDir == new File(userHomeDir, "daemon")
        parameters.systemProperties.isEmpty()
        parameters.effectiveJvmArgs.size() == 1 + 3 // + 1 because effective JVM args contains -Dfile.encoding, +3 for locale props
    }

    def "setting jvm to null means use the current jvm"() {
        def jvm = Stub(JavaInfo)

        when:
        parameters.jvm = jvm

        then:
        parameters.effectiveJvm == jvm

        when:
        parameters.jvm = null

        then:
        parameters.effectiveJvm == Jvm.current()
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
        parameters.applyDefaultsFor(JavaVersion.VERSION_1_8)

        then:
        parameters.effectiveJvmArgs.containsAll(["-Xmx17m"])
        parameters.effectiveJvmArgs.intersect(parameters.DEFAULT_JVM_ARGS).empty
    }

    def "can apply defaults for Java 7 and earlier"() {
        when:
        parameters.applyDefaultsFor(JavaVersion.VERSION_1_7)

        then:
        parameters.effectiveJvmArgs.containsAll(DaemonParameters.DEFAULT_JVM_ARGS)
    }

    def "can apply defaults for Java 8 and later"() {
        when:
        parameters.applyDefaultsFor(JavaVersion.VERSION_1_9)

        then:
        parameters.effectiveJvmArgs.containsAll(DaemonParameters.DEFAULT_JVM_8_ARGS)
        !parameters.effectiveJvmArgs.containsAll(DaemonParameters.DEFAULT_JVM_ARGS)
    }

    @Issue("20611")
    def "defaults for Java 9+ contain the --add-opens args in the form that can be matched by a user's GRADLE_OPTS"() {
        when:
        parameters.applyDefaultsFor(JavaVersion.VERSION_1_9)

        then: "The --add-opens arguments should be in the form that can be matched by user-provided GRADLE_OPTS: --add-opens=x.y/z.a=..."
        def addOpensArgs = parameters.effectiveJvmArgs.findAll { it.startsWith("--add-opens") }
        !addOpensArgs.isEmpty()
        addOpensArgs.every { it.matches("--add-opens=.*?/.*?=ALL-UNNAMED") }

        and: "The required --add-opens args should not contain duplicates"
        addOpensArgs.toSet().size() == addOpensArgs.size()
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
        parameters.applyDefaultsFor(jvmDefault)

        then:
        parameters.effectiveJvmArgs.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")

        where:
        jvmDefault << [JavaVersion.VERSION_1_8, JavaVersion.VERSION_1_9]
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
