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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

import java.lang.management.ManagementFactory

class DaemonJvmSettingsIntegrationTest extends DaemonIntegrationSpec {
    def "uses current JVM and default JVM args when none specified"() {
        given:
        executer.useOnlyRequestedJvmOpts()
        buildFile printJvmArgs()

        when:
        succeeds()

        then:
        def daemonArgs = daemonJvmArgs()
        daemonArgs.contains('-Xmx512m')
        daemonArgs.contains('-XX:+HeapDumpOnOutOfMemoryError')
    }

    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor)
    def "JVM args from gradle.properties packaged in distribution are merged with defaults"() {
        setup:
        requireIsolatedGradleDistribution()
        executer.useOnlyRequestedJvmOpts()

        buildFile printJvmArgs()

        when:
        distribution.gradleHomeDir.file('gradle.properties') << 'org.gradle.jvmargs=-Xmx1024m'
        succeeds()

        then:
        def daemonArgs = daemonJvmArgs()
        daemonArgs.contains('-Xmx1024m')
        daemonArgs.contains('-XX:MaxMetaspaceSize=768m')
        daemonArgs.contains('-XX:+HeapDumpOnOutOfMemoryError')
        daemonArgs.count { !it.startsWith('--add-opens=') && !it.startsWith('--add-exports=') && !it.startsWith('--enable-native-access=') && !it.startsWith('--sun-misc-unsafe-memory-access=') && !it.startsWith('-D') && !it.startsWith('-javaagent:') } == 3

        cleanup:
        stopDaemonsNow()
    }

    def "merges JVM args #jvmArgs from gradle.properties with defaults"() {
        setup:
        executer.useOnlyRequestedJvmOpts()
        file('gradle.properties').writeProperties('org.gradle.jvmargs': jvmArgs)
        buildFile printJvmArgs()

        when:
        succeeds()

        then:
        def daemonArgs = daemonJvmArgs()
        daemonArgs.containsAll(expectedArgs)
        daemonArgs.intersect(unexpectedArgs).empty

        where:
        jvmArgs                           | expectedArgs                                                                                                 | unexpectedArgs
        "-XX:+UseSerialGC"                | ["-XX:+UseSerialGC", "-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"] | []
        "-Xmx1024m"                       | ["-Xmx1024m", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"]                                | ["-Xmx512m", "-Xms256m"]
        "-Xms128m"                        | ["-Xms128m", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"]                                 | ["-Xmx512m", "-Xms256m"]
        "-XX:MaxMetaspaceSize=1g"         | ["-XX:MaxMetaspaceSize=1g", "-Xmx512m", "-Xms256m", "-XX:+HeapDumpOnOutOfMemoryError"]                       | ["-XX:MaxMetaspaceSize=768m"]
        "-XX:-HeapDumpOnOutOfMemoryError" | ["-XX:-HeapDumpOnOutOfMemoryError", "-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=768m"]                     | ["-XX:+HeapDumpOnOutOfMemoryError"]
    }

    def "reuses daemon across builds with JVM args merged with defaults"() {
        setup:
        executer.useOnlyRequestedJvmOpts()
        file('gradle.properties').writeProperties('org.gradle.jvmargs': "-Xmx1024m")

        when:
        succeeds()
        executer.useOnlyRequestedJvmOpts()
        succeeds()

        then:
        daemons.daemons.size() == 1
    }

    def "uses defaults for max/min heap size when JAVA_TOOL_OPTIONS is set (#javaToolOptions)"() {
        setup:
        executer.requireDaemon().requireIsolatedDaemons()
        buildFile """
            import java.lang.management.ManagementFactory

            task verify {
                doFirst {
                    def heapUsage = ManagementFactory.memoryMXBean.heapMemoryUsage
                    println "INITIAL_HEAP: " + heapUsage.init
                    println "MAX_HEAP: " + heapUsage.max
                }
            }
        """

        when:
        // This prevents the executer fixture from adding "default" values to the build jvm options
        executer.useOnlyRequestedJvmOpts()
        executer.withEnvironmentVars(JAVA_TOOL_OPTIONS: javaToolOptions)
        run "verify"

        then:
        daemons.daemons.size() == 1
        outputContains("INITIAL_HEAP: ${256 * 1024 * 1024}")
        outputContains("MAX_HEAP: ${512 * 1024 * 1024}")

        where:
        javaToolOptions << ["-Xms513m", "-Xmx255m", "-Xms128m -Xmx256m"]
    }

    def 'can start the daemon with flag that emits to stdout'() {
        given:
        file('build.gradle') << """
println 'Started'
"""
        executer.useOnlyRequestedJvmOpts()

        when:
        file('gradle.properties') << 'org.gradle.jvmargs=-XshowSettings'

        then:
        succeeds()
    }

    private static String printJvmArgs() {
        """
            ${ManagementFactory.name}.runtimeMXBean.inputArguments.each {
                println "JVM_ARG: " + it
            }
        """
    }

    private List<String> daemonJvmArgs() {
        output.readLines().findAll { it.startsWith("JVM_ARG: ") }.collect { it - "JVM_ARG: " }
    }
}
