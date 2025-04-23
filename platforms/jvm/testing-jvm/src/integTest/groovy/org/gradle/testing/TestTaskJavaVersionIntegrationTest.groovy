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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil

class TestTaskJavaVersionIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

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
        buildFile << """
            ${baseProjectForJdk(jdkOther)}
            ${withTestToolchain(jdkOther)}
            ${withTestExecuable(Jvm.current())}
        """

        when:
        withInstallations(jdkOther)
        fails(":test")

        then:
        failureDescriptionStartsWith("Execution failed for task ':test'.")
        failureHasCause("Toolchain from `executable` property does not match toolchain from `javaLauncher` property")

        where:
        jdkOther << AvailableJavaHomes.supportedWorkerJdks.findAll {
            it.javaVersionMajor != Jvm.current().javaVersionMajor
        }
    }

    def "uses configured executable"() {
        buildFile << """
            ${baseProjectForJdk(otherJdk)}
            ${withTestExecuable(otherJdk)}
        """

        when:
        succeeds(":test")

        then:
        executedAndNotSkipped(":test")
        outputContains("Tests running with ${otherJdk.javaHome.absolutePath}")

        where:
        otherJdk << AvailableJavaHomes.supportedWorkerJdks
    }

    def "uses configured toolchain"() {
        if (otherJdk.javaVersionMajor == Jvm.current().javaVersionMajor) {
            // if current JAVA_HOME and target jdk are different, but have same major version
            // the test will start with JAVA_HOME=/path/to/jdk-1 and -Porg.gradle.java.installations.paths=/path/to/jdk-2
            // resulting in flakiness result
            otherJdk = Jvm.current()
        }
        buildFile << """
            ${baseProjectForJdk(otherJdk)}
            ${withTestToolchain(otherJdk)}
        """

        when:
        withInstallations(otherJdk)
        succeeds(":test")

        then:
        executedAndNotSkipped(":test")
        outputContains("Tests running with ${otherJdk.javaHome.absolutePath}")

        where:
        otherJdk << AvailableJavaHomes.supportedWorkerJdks
    }

    def "emits deprecation warning if executable specified as relative path"() {
        given:
        def executable = TextUtil.normaliseFileSeparators(Jvm.current().javaExecutable.toString())

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnit()
                targets.configureEach {
                    testTask.configure {
                        executable = new File(".").getCanonicalFile().toPath().relativize(new File("${executable}").toPath()).toString()
                    }
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Configuring a Java executable via a relative path. " +
            "This behavior has been deprecated. This will fail with an error in Gradle 9.0. " +
            "Resolving relative file paths might yield unexpected results, there is no single clear location it would make sense to resolve against. " +
            "Configure an absolute path to a Java executable instead. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#no_relative_paths_for_java_executables")

        then:
        succeeds("test")
    }

    @Requires(IntegTestPreconditions.UnsupportedWorkerJavaHomeAvailable)
    def "test execution fails using target Java version"() {
        given:
        buildFile << """
            ${baseProjectForJdk(jdk)}
            ${withTestToolchain(jdk)}
        """

        when:
        withInstallations(jdk)
        fails("test")

        then:
        failure.assertHasCause("Gradle does not support executing tests using JVM ${jdk.javaVersionMajor} or earlier.")

        where:
        jdk << AvailableJavaHomes.unsupportedWorkerJdks
    }

    private static String baseProjectForJdk(Jvm jdk) {
        """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            tasks.withType(JavaCompile) {
                sourceCompatibility = "${jdk.javaVersion}"
                targetCompatibility = "${jdk.javaVersion}"
                options.fork = true
                options.forkOptions.javaHome = file("${TextUtil.normaliseFileSeparators(jdk.javaHome.absolutePath)}")
            }

            testing.suites.test {
                useJUnit()
                targets.configureEach {
                    testTask.configure {
                        testLogging {
                            showStandardStreams = true
                        }
                    }
                }
            }
        """
    }

    private static String withTestExecuable(Jvm jdk) {
        """
            tasks.test {
                executable = "${TextUtil.normaliseFileSeparators(jdk.javaExecutable.absolutePath)}"
            }
        """
    }

    private static String withTestToolchain(Jvm jdk) {
        """
            test {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersionMajor})
                }
            }
        """
    }
}
