/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r813

import org.gradle.integtests.fixtures.jvm.JDWPUtil
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.junit.Assume
import spock.lang.Issue

@ToolingApiVersion(">=8.13")
@Requires(
    value = IntegTestPreconditions.NotEmbeddedExecutor,
    reason = "In order to pass JVM arguments to the Gradle daemon, we need to use the external executor."
)
class JvmArgumentPassingCrossVersionTest extends ToolingApiSpecification {

    private static final String SYSPROP_A = 'mysysprop-a=a'
    private static final Map<String, String> SYSPROP_A_FROM_TAPI_AS_MAP = [('mysysprop-a'): 'a2']
    private static final String SYSPROP_A_FROM_TAPI = 'mysysprop-a=a2'
    private static final String SYSPROP_B = 'mysysprop-b=b'
    private static final String SYSPROP_C = 'mysysprop-c=c'
    private static final String JVMARG_SYSPROP_A = "-D$SYSPROP_A"
    private static final String JVMARG_SYSPROP_B = "-D$SYSPROP_B"
    private static final String JVMARG_SYSPROP_C = "-D$SYSPROP_C"
    private static final String JVMARG_A = '-verbose:gc'
    private static final String JVMARG_B = '-XX:+PrintGC'
    private static final String JVMARG_C = '-XX:+PrintGCDetails'

    // TODO (donat) this is an end-to end testing for defining JVM arguments for the build. It would be beneficial to extend the coverage with adding functional tests for the daemon launcher. We want
    // to verify what are the exact JVM arguments that the launcher uses for launching the daemon, without actually running a build.

    def setup() {
        buildFile """
            import java.lang.management.ManagementFactory

            List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
            println("JVM arguments")
            println(jvmArgs)
            file("jvm-args.txt").text = jvmArgs.join("\\n")

            println("------")

            List<String> systemProperties = System.getProperties().entrySet().toList()
            println("System properties")
            println(systemProperties)
            file("system-properties.txt").text = System.properties.entrySet().collect { it.key + "=" + it.value }.join('\\n')
        """
    }

    def "set and additional arguments work correctly when overridden from the TAPI connector with predefined values coming from the project gradle properties file"() {
        given:
        def gradlePropertyText = "org.gradle.jvmargs=$predefinedProperty"
        propertiesFile << gradlePropertyText

        when:
        withConnection { ProjectConnection connection ->
            def build = connection
                    .newBuild()
                    .forTasks("help")
                    .setJvmArguments(setProperties)
            if (additionalProperties != null) {
                build.addJvmArguments(additionalProperties)
            }

            build.run()
        }
        def sysProperties = readSystemProperties()
        def jvmArgs = readJvmArgs()

        then:
        sysProperties == expectedSystemProperties.toSet()
        jvmArgs == expectedJvmArgs.toSet()

        where:
        predefinedProperty | setProperties      | additionalProperties || expectedSystemProperties | expectedJvmArgs
        JVMARG_SYSPROP_A   | null               | null                 || [SYSPROP_A]              | []
        JVMARG_SYSPROP_A   | []                 | null                 || [SYSPROP_A]              | []
        JVMARG_SYSPROP_A   | [JVMARG_SYSPROP_B] | []                   || [SYSPROP_B]              | []
        JVMARG_SYSPROP_A   | null               | [JVMARG_SYSPROP_C]   || [SYSPROP_A, SYSPROP_C]   | []
        JVMARG_SYSPROP_A   | []                 | [JVMARG_SYSPROP_C]   || [SYSPROP_A, SYSPROP_C]   | []
        JVMARG_SYSPROP_A   | [JVMARG_SYSPROP_B] | [JVMARG_SYSPROP_C]   || [SYSPROP_B, SYSPROP_C]   | []
        JVMARG_A           | null               | null                 || []                       | [JVMARG_A]
        JVMARG_A           | []                 | null                 || []                       | [JVMARG_A]
        JVMARG_A           | [JVMARG_B]         | []                   || []                       | [JVMARG_B]
        JVMARG_A           | null               | [JVMARG_C]           || []                       | [JVMARG_A, JVMARG_C]
        JVMARG_A           | []                 | [JVMARG_C]           || []                       | [JVMARG_A, JVMARG_C]
        JVMARG_A           | [JVMARG_B]         | [JVMARG_C]           || []                       | [JVMARG_B, JVMARG_C]
    }

    def "set and additional arguments work correctly when overridden from the TAPI connector with predefined values coming from an isolated user home"() {
        given:
        requireIsolatedUserHome()
        def gradlePropertyText = "org.gradle.jvmargs=$predefinedProperty"
        def isolatedPropertiesFile = file("user-home-dir").file("gradle.properties")
        isolatedPropertiesFile << gradlePropertyText

        when:
        withConnection { ProjectConnection connection ->
            def build = connection
                    .newBuild()
                    .forTasks("help")
                    .setJvmArguments(setProperties)
            if (additionalProperties != null) {
                build.addJvmArguments(additionalProperties)
            }

            build.run()
        }
        def sysProperties = readSystemProperties()
        def jvmArgs = readJvmArgs()

        then:
        sysProperties == expectedSystemProperties.toSet()
        jvmArgs == expectedJvmArgs.toSet()

        where:
        predefinedProperty | setProperties      | additionalProperties || expectedSystemProperties | expectedJvmArgs
        JVMARG_SYSPROP_A   | null               | null                 || [SYSPROP_A]              | []
        JVMARG_SYSPROP_A   | []                 | null                 || [SYSPROP_A]              | []
        JVMARG_SYSPROP_A   | [JVMARG_SYSPROP_B] | []                   || [SYSPROP_B]              | []
        JVMARG_SYSPROP_A   | null               | [JVMARG_SYSPROP_C]   || [SYSPROP_A, SYSPROP_C]   | []
        JVMARG_SYSPROP_A   | []                 | [JVMARG_SYSPROP_C]   || [SYSPROP_A, SYSPROP_C]   | []
        JVMARG_SYSPROP_A   | [JVMARG_SYSPROP_B] | [JVMARG_SYSPROP_C]   || [SYSPROP_B, SYSPROP_C]   | []
        JVMARG_A           | null               | null                 || []                       | [JVMARG_A]
        JVMARG_A           | []                 | null                 || []                       | [JVMARG_A]
        JVMARG_A           | [JVMARG_B]         | []                   || []                       | [JVMARG_B]
        JVMARG_A           | null               | [JVMARG_C]           || []                       | [JVMARG_A, JVMARG_C]
        JVMARG_A           | []                 | [JVMARG_C]           || []                       | [JVMARG_A, JVMARG_C]
        JVMARG_A           | [JVMARG_B]         | [JVMARG_C]           || []                       | [JVMARG_B, JVMARG_C]
    }

    @TargetGradleVersion(">=7.6") // LongRunningOperation.withSystemProperties() was introduced in Gradle 7.6
    def "build launcher also sets system properties"() {
        // NOTE The system properties defined in gradle.properties files take precedence over the ones defined in the TAPI client config (ie via LongRunningOperation.withSystemProperties).
        // This is not correct. This test only ensures, that the fix for https://github.com/gradle/gradle/issues/31462 does not change the existing behaviour.

        def gradlePropertyText = predefinedProperty ? "org.gradle.jvmargs=$predefinedProperty" : ''
        propertiesFile << gradlePropertyText

        when:
        withConnection { ProjectConnection connection ->
            def build = connection
                    .newBuild()
                    .forTasks("help")
                    .withSystemProperties(SYSPROP_A_FROM_TAPI_AS_MAP)
                    .setJvmArguments(setProperties)
            if (additionalProperties != null) {
                build.addJvmArguments(additionalProperties)
            }

            build.run()
        }
        def sysProperties = readSystemProperties()
        def jvmArgs = readJvmArgs()

        then:
        sysProperties == expectedSystemProperties.toSet()

        where:
        predefinedProperty | setProperties      | additionalProperties || expectedSystemProperties
        null               | null               | null                 || [SYSPROP_A_FROM_TAPI]
        null               | null               | null                 || [SYSPROP_A_FROM_TAPI]
        null               | []                 | null                 || [SYSPROP_A_FROM_TAPI]
        null               | [JVMARG_SYSPROP_B] | []                   || [SYSPROP_A_FROM_TAPI, SYSPROP_B]
        null               | null               | [JVMARG_SYSPROP_C]   || [SYSPROP_A_FROM_TAPI, SYSPROP_C]
        null               | []                 | [JVMARG_SYSPROP_C]   || [SYSPROP_A_FROM_TAPI, SYSPROP_C]
        null               | [JVMARG_SYSPROP_B] | [JVMARG_SYSPROP_C]   || [SYSPROP_A_FROM_TAPI, SYSPROP_B, SYSPROP_C]
        JVMARG_SYSPROP_A   | null               | null                 || [SYSPROP_A]
        JVMARG_SYSPROP_A   | []                 | null                 || [SYSPROP_A]
        JVMARG_SYSPROP_A   | [JVMARG_SYSPROP_B] | []                   || [SYSPROP_A_FROM_TAPI, SYSPROP_B]
        JVMARG_SYSPROP_A   | null               | [JVMARG_SYSPROP_C]   || [SYSPROP_A, SYSPROP_C]
        JVMARG_SYSPROP_A   | []                 | [JVMARG_SYSPROP_C]   || [SYSPROP_A, SYSPROP_C]
        JVMARG_SYSPROP_A   | [JVMARG_SYSPROP_B] | [JVMARG_SYSPROP_C]   || [SYSPROP_A_FROM_TAPI, SYSPROP_B, SYSPROP_C]
    }

    @Issue("https://youtrack.jetbrains.com/issue/IDEA-364072")
    def "Can configure debug mode in gradle.properties"() {
        given:
        Assume.assumeTrue(debugPortIsFree())
        requireIsolatedUserHome()
        def gradlePropertyText = "org.gradle.debug=true"
        def isolatedPropertiesFile = file("user-home-dir").file("gradle.properties")
        isolatedPropertiesFile << gradlePropertyText
        JDWPUtil jdwpClient = new JDWPUtil(5005)
        boolean debuggerCouldConnect = false

        when:
        withConnection { ProjectConnection connection ->
            def build = connection
                .newBuild()
                .forTasks("help")
                .addJvmArguments('-Dtest-add')
            build.run(Mock(ResultHandler))
            ConcurrentTestUtil.poll() {
                jdwpClient.connect().dispose() // connect() blocks until can connect to jvm
                debuggerCouldConnect = true
            }
        }

        then:
        debuggerCouldConnect == true
    }

    Set<String> readSystemProperties() {
        readEntries('system-properties.txt', [SYSPROP_A, SYSPROP_A_FROM_TAPI, SYSPROP_B, SYSPROP_C])
    }

    Set<String> readJvmArgs() {
        readEntries('jvm-args.txt', [JVMARG_A, JVMARG_B, JVMARG_C])
    }

    Set<String> readEntries(String path, List possibleEntries) {
        file(path)
            .text
            .split("\n")
            .findAll {
                it in possibleEntries
            }
            .toSet()
    }

    static boolean debugPortIsFree() {
        ServerSocket serverSocket
        try {
            serverSocket = new ServerSocket(5005)
            serverSocket.setReuseAddress(true)
            return true
        } catch (IOException e) {
            // If we get an exception, someone else is using the port or
            // we don't have permission.
            return false
        } finally {
            serverSocket?.close()
        }
    }
}
