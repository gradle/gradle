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

package org.gradle.integtests.tooling.r814

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.JavaToolchainFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@TargetGradleVersion(">=8.14")
class JavaCompileToolchainCrossVersionTest extends ToolingApiSpecification implements JavaToolchainFixture {

    def setup() {
        requireDaemons()
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given custom toolchain location using environment variable When executing compileJava task Then build used expected toolchain"() {
        given:
        file("src/main/java/Foo.java") << """class Foo { }"""
        def otherJvm = AvailableJavaHomes.differentVersion
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${otherJvm.javaVersionMajor})
                }
            }
        """

        when:
        withConnection {
            // To modify environment variables for crossVersion tests is a must to use real daemon processes and not embedded,
            // otherwise, those are going to be ignored being this different than properties which uses SystemPropertySetterExecuter
            it.newBuild().setEnvironmentVariables(["OTHER_JAVA_HOME": otherJvm.javaHome.absolutePath])
                .forTasks(":compileJava").withArguments(
                    "--info",
                    "-Porg.gradle.java.installations.fromEnv=OTHER_JAVA_HOME",
                    "-Porg.gradle.java.installations.auto-detect=false",
            ).run()
        }

        then:
        outputContains("Compiling with toolchain '${otherJvm.javaHome.absolutePath}'")
        classJavaVersion(javaClassFile("Foo.class")) == JavaVersion.toVersion(otherJvm.javaVersion)
    }
}
