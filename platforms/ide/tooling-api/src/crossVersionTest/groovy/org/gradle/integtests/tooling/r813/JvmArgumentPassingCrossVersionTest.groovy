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
class JvmArgumentPassingCrossVersionTest extends ToolingApiSpecification {

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

    def "set and additional arguments work correctly when overridden from the TAPI connector with predefined values coming from the project gradle properties file"() {
        given:
        def gradlePropertyText = "org.gradle.jvmargs=${predefinedProperties.collect { "-D$it" }.join(" ")}"
        propertiesFile << gradlePropertyText

        when:
        withConnection { ProjectConnection connection ->
            def build = connection
                .newBuild()
                .forTasks("help")

            build.setJvmArguments(setProperties.collect { "-D$it".toString() })
            if (additionalProperties != null) {
                build.addJvmArguments(additionalProperties.collect { "-D$it".toString() })
            }

            build.run()
        }
        def sysProperties = readSystemProperties()

        then:
        sysProperties == expectedSystemProperties.toSet()

        where:
        predefinedProperties | setProperties | additionalProperties || expectedSystemProperties
        ["test-predefined"]  | null          | null                 || ["test-predefined"]
        ["test-predefined"]  | []            | null                 || ["test-predefined"]
        ["test-predefined"]  | ["test-set"]  | []                   || ["test-set"]
        ["test-predefined"]  | null          | ["test-add"]         || ["test-predefined", "test-add"]
        ["test-predefined"]  | []            | ["test-add"]         || ["test-predefined", "test-add"]
        ["test-predefined"]  | ["test-set"]  | ["test-add"]         || ["test-set", "test-add"]
    }

    def "set and additional arguments work correctly when overridden from the TAPI connector with predefined values coming from an isolated user home"() {
        given:
        requireIsolatedUserHome()
        def gradlePropertyText = "org.gradle.jvmargs=${predefinedProperties.collect { "-D$it" }.join(" ")}"
        def isolatedPropertiesFile = file("user-home-dir").file("gradle.properties")
        isolatedPropertiesFile << gradlePropertyText

        when:
        withConnection { ProjectConnection connection ->
            def build = connection
                .newBuild()
                .forTasks("help")

            build.setJvmArguments(setProperties.collect { "-D$it".toString() })
            if (additionalProperties != null) {
                build.addJvmArguments(additionalProperties.collect { "-D$it".toString() })
            }

            build.run()
        }
        def sysProperties = readSystemProperties()

        then:
        sysProperties == expectedSystemProperties.toSet()

        where:
        predefinedProperties | setProperties | additionalProperties || expectedSystemProperties
        ["test-predefined"]  | null          | null                 || ["test-predefined"]
        ["test-predefined"]  | []            | null                 || ["test-predefined"]
        ["test-predefined"]  | ["test-set"]  | []                   || ["test-set"]
        ["test-predefined"]  | null          | ["test-add"]         || ["test-predefined", "test-add"]
        ["test-predefined"]  | []            | ["test-add"]         || ["test-predefined", "test-add"]
        ["test-predefined"]  | ["test-set"]  | ["test-add"]         || ["test-set", "test-add"]
    }

    Set<String> readSystemProperties() {
        return file("system-properties.txt")
            .text
            .split("\n")
            .findAll {
                it.startsWith("test-")
            }
            .toSet()
    }

}
