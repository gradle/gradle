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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.ProjectConnection

@ToolingApiVersion(">=8.13")
@TargetGradleVersion(">=8.13")
@Requires(
    value = IntegTestPreconditions.NotEmbeddedExecutor,
    reason = "In order to pass JVM arguments to the Gradle daemon, we need to use the external executor."
)
class ProviderOperationParametersCrossVersionTest extends ToolingApiSpecification {

    def setup() {
        buildFile """
import java.lang.management.ManagementFactory

List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
println("JVM arguments")
println(jvmArgs)
file("jvm-args.txt").text = jvmArgs.join("\\n")

println("------")

List<String> systemProperties = System.getProperties().keySet().toList()
println("System properties")
println(systemProperties)
file("system-properties.txt").text = System.getProperties().keySet().join("\\n")
        """
    }

    def "#addJvmArguments should not reset properties defined in project gradle properties"() {
        given:
        propertiesFile << "org.gradle.jvmargs=-Dgradle-properties-arg"

        when:
        withConnection { ProjectConnection connection ->
            connection
                .newBuild()
                .forTasks("help")
                .addJvmArguments("-Dadd-jvm-arg")
                .run()
        }

        then:
        def sysProperties = file("system-properties.txt").text.split("\n")
        verifyAll {
            sysProperties.contains("gradle-properties-arg")
            sysProperties.contains("add-jvm-arg")
        }
    }

    def "#addJvmArguments should not reset properties defined in user home gradle properties"() {
        given:
        requireIsolatedUserHome()
        file("user-home-dir").file("gradle.properties") << "org.gradle.jvmargs=-Dgradle-properties-arg"

        when:
        withConnection { ProjectConnection connection ->
            connection
                .newBuild()
                .forTasks("help")
                .addJvmArguments("-Dadd-jvm-arg")
                .run()
        }

        then:
        def sysProperties = file("system-properties.txt").text.split("\n")
        verifyAll {
            sysProperties.contains("gradle-properties-arg")
            sysProperties.contains("add-jvm-arg")
        }
    }

    def "#addJvmArguments should not inflict immutable properties other properties defined in user home gradle properties"() {
        given:
        requireIsolatedUserHome()
        file("user-home-dir").file("gradle.properties") << "org.gradle.jvmargs=-Dgradle-properties-arg -Duser.language=xx"

        when:
        withConnection { ProjectConnection connection ->
            connection
                .newBuild()
                .forTasks("help")
                .addJvmArguments("-Dadd-jvm-arg")
                .run()
        }

        then:
        def jvmArgs = file("jvm-args.txt").text.split("\n")
        jvmArgs.contains("-Duser.language=xx")

        def sysProperties = file("system-properties.txt").text.split("\n")
        verifyAll {
            sysProperties.contains("gradle-properties-arg")
            sysProperties.contains("add-jvm-arg")
            sysProperties.contains("gradle-properties-arg")
        }
    }

    def "#addJvmArguments should not reset properties defined in project gradle properties when using a provider operation"() {
        given:
        propertiesFile << "org.gradle.jvmargs=-Dgradle-properties-arg"

        when:
        withConnection { ProjectConnection connection ->
            connection
                .newBuild()
                .forTasks("help")
                .addJvmArguments("-Dadd-jvm-arg")
                .run()
        }

        then:
        def sysProperties = file("system-properties.txt").text.split("\n")
        verifyAll {
            sysProperties.contains("gradle-properties-arg")
            sysProperties.contains("add-jvm-arg")
        }
    }

}
