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

package org.gradle.testing

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

class TestTaskToolchainIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        file("src/test/java/ToolchainTest.java") << """
            import org.junit.*;

            public class ToolchainTest {
               @Test
               public void test() {
                  System.out.println("Tests running with " + System.getProperty("java.home"));
                  Assert.assertEquals(1,1);
               }
            }
        """
    }

    def "fails on toolchain and executable mismatch"() {
        def jdkCurrent = Jvm.current()
        def jdkOther = AvailableJavaHomes.differentVersion

        def compileWithVersion = [jdkCurrent, jdkOther].collect { it.javaVersion }.min()

        configureProjectWithJavaPlugin(compileWithVersion)

        configureLauncher(jdkOther)
        configureExecutable(jdkCurrent)

        when:
        withInstallations(jdkCurrent, jdkOther).runAndFail(":test")

        then:
        failureDescriptionStartsWith("Execution failed for task ':test'.")
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javaLauncher` property")
    }

    def "fails on toolchain and executable mismatch (without java-base plugin)"() {
        def jdkCurrent = Jvm.current()
        def jdkOther = AvailableJavaHomes.differentVersion

        def compileWithVersion = [jdkCurrent, jdkOther].collect { it.javaVersion }.min()

        configureProjectWithoutJavaBasePlugin(compileWithVersion)

        configureLauncher(jdkOther)
        configureExecutable(jdkCurrent)

        when:
        withInstallations(jdkCurrent, jdkOther).runAndFail(":test")

        then:
        failureDescriptionStartsWith("Execution failed for task ':test'.")
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javaLauncher` property")
    }

    def "uses #what toolchain #when (with java plugin)"() {
        Jvm currentJdk = Jvm.current()
        Jvm otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        def compileWithVersion = [currentJdk, otherJdk].collect { it.javaVersion }.min()

        configureProjectWithJavaPlugin(compileWithVersion)

        if (withTool != null) {
            configureLauncher(selectJdk(withTool))
        }
        if (withExecutable != null) {
            configureExecutable(selectJdk(withExecutable))
        }
        if (withJavaExtension != null) {
            configureJavaPluginToolchainVersion(selectJdk(withJavaExtension))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":test", "--info")

        then:
        executedAndNotSkipped(":test")
        outputContains("Tests running with ${targetJdk.javaHome.absolutePath}")

        where:
        // Some cases are skipped, because the executable (when configured) must match the resulting toolchain, otherwise the build fails
        what             | when                                 | withTool | withExecutable | withJavaExtension | target
        "current JVM"    | "when toolchains are not configured" | null     | null           | null              | "current"
        "java extension" | "when configured"                    | null     | null           | "other"           | "other"
        "executable"     | "when configured"                    | null     | "other"        | null              | "other"
        "assigned tool"  | "when configured"                    | "other"  | null           | null              | "other"
        "executable"     | "over java extension"                | null     | "other"        | "current"         | "other"
        "assigned tool"  | "over java extension"                | "other"  | null           | "current"         | "other"
    }

    def "uses #what toolchain #when (without java-base plugin)"() {
        Jvm currentJdk = Jvm.current()
        Jvm otherJdk = AvailableJavaHomes.differentVersion
        def selectJdk = { it == "other" ? otherJdk : it == "current" ? currentJdk : null }

        def compileWithVersion = [currentJdk, otherJdk].collect { it.javaVersion }.min()

        configureProjectWithoutJavaBasePlugin(compileWithVersion)

        if (withTool != null) {
            configureLauncher(selectJdk(withTool))
        }
        if (withExecutable != null) {
            configureExecutable(selectJdk(withExecutable))
        }

        def targetJdk = selectJdk(target)

        when:
        withInstallations(currentJdk, otherJdk).run(":test", "--info")

        then:
        executedAndNotSkipped(":test")
        outputContains("Tests running with ${targetJdk.javaHome.absolutePath}")

        where:
        // Some cases are skipped, because the executable (when configured) must match the resulting toolchain, otherwise the build fails
        what            | when                                 | withTool | withExecutable | target
        "current JVM"   | "when toolchains are not configured" | null     | null           | "current"
        "executable"    | "when configured"                    | null     | "other"        | "other"
        "assigned tool" | "when configured"                    | "other"  | null           | "other"
    }

    private TestFile configureProjectWithJavaPlugin(JavaVersion compileJavaVersion) {
        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            tasks.withType(JavaCompile).configureEach {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${compileJavaVersion.majorVersion})
                }
            }
        """
    }

    private TestFile configureProjectWithoutJavaBasePlugin(JavaVersion compileJavaVersion) {
        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            ${mavenCentralRepository()}

            configurations {
                testImplementation
            }

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            def buildDir = project.layout.buildDirectory

            task compileJava(type: JavaCompile) {
                classpath = configurations.testImplementation
                source = project.layout.files("src/test/java")
                destinationDirectory = buildDir.dir("classes/java/test")
                sourceCompatibility = "${compileJavaVersion}"
                targetCompatibility = "${compileJavaVersion}"
            }

            task test(type: Test) {
                testClassesDirs = buildDir.files("classes/java/test")
                classpath = files(configurations.testImplementation, buildDir.dir("classes/java/test"))
                binaryResultsDirectory.set(buildDir.dir("test-results"))
                reports.junitXml.required.set(false)
                reports.html.outputLocation.set(buildDir.dir("test-results"))
            }

            test.dependsOn compileJava
        """
    }

    private TestFile configureExecutable(Jvm jdk) {
        buildFile << """
            test {
                executable = "${TextUtil.normaliseFileSeparators(jdk.javaExecutable.absolutePath)}"
            }
        """
    }

    private TestFile configureLauncher(Jvm jdk) {
        buildFile << """
            test {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }
        """
    }
}
