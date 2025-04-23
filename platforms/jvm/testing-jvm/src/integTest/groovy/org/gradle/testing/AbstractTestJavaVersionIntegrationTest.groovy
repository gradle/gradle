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

package org.gradle.testing

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.junit.Assume

/**
 * Verifies that a test framework can execute tests on all supported JVM versions.
 * <p>
 * This test specifically tests a wider range of Java versions than CI does automatically,
 * as CI only runs tests against Java versions that the Gradle daemon supports.
 * <p>
 * Our test infrastructure supports additional versions of Java that the daemon does not.
 */
abstract class AbstractTestJavaVersionIntegrationTest extends AbstractTestingMultiVersionIntegrationTest implements JavaToolchainFixture {

    def "can run test on java #jdk.javaVersionMajor"() {
        Assume.assumeTrue(supportsJavaVersion(jdk.javaVersionMajor))

        if (jdk.javaVersionMajor == Jvm.current().javaVersionMajor) {
            // if current JAVA_HOME and target jdk are different, but have same major version
            // the test will start with JAVA_HOME=/path/to/jdk-1 and -Porg.gradle.java.installations.paths=/path/to/jdk-2
            // resulting in flakiness result
            jdk = Jvm.current()
        }

        given:
        file("src/test/java/SomeTest.java") << """
            ${testFrameworkImports}

            public class SomeTest {
                @Test
                public void doTest() {
                    System.out.println("Java Home: " + System.getProperty("java.home"));
                }
            }
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}
            ${javaPluginToolchainVersion(jdk)}

            dependencies {
                ${testFrameworkDependencies}
            }

            testing.suites.test {
                targets.configureEach {
                    testTask.configure {
                        ${configureTestFramework}

                        testLogging {
                            showStandardStreams = true
                        }
                    }
                }
            }
        """

        when:
        withInstallations(jdk)
        succeeds("test")

        then:
        outputContains("Java Home: ${jdk.javaHome.absolutePath}")

        where:
        jdk << AvailableJavaHomes.supportedWorkerJdks
    }

}
