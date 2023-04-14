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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf

class DaemonJvmSettingsIntegrationTest extends DaemonIntegrationSpec {
    def "uses current JVM and default JVM args when none specified"() {
        file('build.gradle') << """
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx512m')
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-XX:+HeapDumpOnOutOfMemoryError')
"""

        given:
        executer.useOnlyRequestedJvmOpts()

        expect:
        succeeds()
    }

    @IgnoreIf({ GradleContextualExecuter.embedded })
    def "JVM args from gradle.properties packaged in distribution override defaults"() {
        setup:
        requireIsolatedGradleDistribution()
        executer.useOnlyRequestedJvmOpts()

        file('build.gradle') << """
            def inputArguments = java.lang.management.ManagementFactory.runtimeMXBean.inputArguments
            assert inputArguments.contains('-Xmx1024m')
            assert inputArguments.count { !it.startsWith('--add-opens=') && !it.startsWith('-D') && !it.startsWith('-javaagent:') } == 1
        """

        when:
        distribution.gradleHomeDir.file('gradle.properties') << 'org.gradle.jvmargs=-Xmx1024m'

        then:
        succeeds()

        cleanup:
        stopDaemonsNow()
    }

    def "uses defaults for max/min heap size when JAVA_TOOL_OPTIONS is set (#javaToolOptions)"() {
        setup:
        executer.requireDaemon().requireIsolatedDaemons()
        boolean java9orAbove = JavaVersion.current().java9Compatible

        buildScript """
            import java.lang.management.ManagementFactory
            import java.lang.management.MemoryMXBean

            println "GRADLE_VERSION: " + gradle.gradleVersion

            task verify {
                doFirst {
                    MemoryMXBean memBean = ManagementFactory.getMemoryMXBean()
                    println "Initial Heap: " + memBean.heapMemoryUsage.init
                    assert memBean.heapMemoryUsage.init == 256 * 1024 * 1024
                    println "    Max Heap: " + memBean.heapMemoryUsage.max

                    // Java 8 does not report max heap size exactly matching the command line setting
                    if ($java9orAbove) {
                        assert memBean.heapMemoryUsage.max == 512 * 1024 * 1024
                    } else {
                        assert memBean.heapMemoryUsage.max > 256 * 1024 * 1024
                    }
                }
            }
        """

        when:
        // This prevents the executer fixture from adding "default" values to the build jvm options
        executer.useOnlyRequestedJvmOpts()
        executer.withEnvironmentVars(JAVA_TOOL_OPTIONS: javaToolOptions)
        run "verify"

        then:
        String gradleVersion = (output =~ /GRADLE_VERSION: (.*)/)[0][1]
        daemons(gradleVersion).daemons.size() == 1

        where:
        javaToolOptions << ["-Xms513m", "-Xmx255m", "-Xms128m -Xmx256m"]
    }

    @Requires(TestPrecondition.JDK16_OR_EARLIER) // TraceClassLoading option has been deprecated and is removed in JDK17
    def 'can start the daemon with ClassLoading tracing enabled'() {
        given:
        file('build.gradle') << """
println 'Started'
"""
        executer.useOnlyRequestedJvmOpts()

        when:
        file('gradle.properties') << 'org.gradle.jvmargs=-XX:+TraceClassLoading'

        then:
        succeeds()
    }
}
