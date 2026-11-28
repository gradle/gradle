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

    def "merges jvmargs #userArgs with the defaults"() {
        when:
        parameters.setJvmArgs(userArgs)

        then:
        def effectiveArgs = parameters.effectiveJvmArgs.findAll { !it.startsWith("-D") }
        effectiveArgs as Set == expectedArgs as Set
        effectiveArgs.size() == expectedArgs.size()

        where:
        userArgs                                                 || expectedArgs
        []                                                       || ["-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"]
        ["-XX:+UseG1GC"]                                         || ["-XX:+UseG1GC", "-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"]
        ["-Xmx2g"]                                               || ["-Xmx2g", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"]
        ["-Xms1g"]                                               || ["-Xms1g", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"]
        ["-Xms1g", "-Xmx2g"]                                     || ["-Xms1g", "-Xmx2g", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"]
        ["-XX:MaxMetaspaceSize=1g"]                              || ["-XX:MaxMetaspaceSize=1g", "-Xmx512m", "-Xms256m", "-XX:+HeapDumpOnOutOfMemoryError"]
        ["-XX:-HeapDumpOnOutOfMemoryError"]                      || ["-XX:-HeapDumpOnOutOfMemoryError", "-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=768m"]
        ["-XX:+HeapDumpOnOutOfMemoryError"]                      || ["-XX:+HeapDumpOnOutOfMemoryError", "-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=768m"]
        DaemonParameters.DEFAULT_JVM_ARGS                        || DaemonParameters.DEFAULT_JVM_ARGS
        ["-XX:MaxRAMPercentage=75"]                              || ["-XX:MaxRAMPercentage=75", "-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"]
    }

    def "places defaults before user-supplied jvmargs"() {
        when:
        parameters.setJvmArgs(["-XX:MaxHeapSize=2g"])

        then:
        parameters.effectiveJvmArgs.take(4) == ["-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"]
        parameters.effectiveJvmArgs.indexOf("-XX:MaxHeapSize=2g") > 3
    }

    def "merges defaults with jvmargs added after setting jvmargs"() {
        when:
        parameters.setJvmArgs(["-XX:MaxMetaspaceSize=1g"])
        parameters.addJvmArgs(["-Xmx2g", "-XX:-HeapDumpOnOutOfMemoryError"])

        then:
        def effectiveArgs = parameters.effectiveJvmArgs.findAll { !it.startsWith("-D") }
        effectiveArgs as Set == ["-XX:MaxMetaspaceSize=1g", "-Xmx2g", "-XX:-HeapDumpOnOutOfMemoryError"] as Set
    }

    def "replaces previously set jvmargs when setting jvmargs again"() {
        when:
        parameters.setJvmArgs(["-Xmx2g", "-XX:MaxMetaspaceSize=1g"])
        parameters.setJvmArgs(["-XX:+UseG1GC"])

        then:
        def effectiveArgs = parameters.effectiveJvmArgs.findAll { !it.startsWith("-D") }
        effectiveArgs as Set == ["-XX:+UseG1GC"] + DaemonParameters.DEFAULT_JVM_ARGS as Set
    }

    def "merging defaults is idempotent for #userArgs"() {
        given:
        parameters.setJvmArgs(userArgs)
        def effectiveArgs = parameters.effectiveJvmArgs

        when:
        def roundTripped = new DaemonParameters(userHomeDir, TestFiles.fileCollectionFactory())
        roundTripped.setJvmArgs(effectiveArgs)

        then:
        roundTripped.effectiveJvmArgs as Set == effectiveArgs as Set
        roundTripped.effectiveJvmArgs.size() == effectiveArgs.size()

        where:
        userArgs << [
            [],
            ["-Xmx2g"],
            ["-XX:-HeapDumpOnOutOfMemoryError", "-ea", "-Dfoo=bar"]
        ]
    }

    def "keeps assertions and debug arguments when merging defaults"() {
        when:
        parameters.setJvmArgs(["-ea"])
        parameters.setDebug(true)

        then:
        parameters.effectiveJvmArgs.containsAll(["-ea", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"])
        parameters.effectiveJvmArgs.containsAll(DaemonParameters.DEFAULT_JVM_ARGS)
    }

    def "applies defaults regardless of requested daemon JVM"() {
        when:
        parameters.requestedJvmCriteria = new DaemonJvmCriteria.Spec(JavaLanguageVersion.of(9), null, null, false)

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
        parameters.requestedJvmCriteria = new DaemonJvmCriteria.Spec(jvmDefault, null, null, false)

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
