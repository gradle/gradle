/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.buildconfiguration.fixture.BuildPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Assume

class DaemonToolchainIntegrationTest extends AbstractIntegrationSpec implements BuildPropertiesFixture {
    def "Given daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        writeJvmCriteria(Jvm.current())
        expectJavaHome(Jvm.current())

        expect:
        succeeds("help")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given other daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        buildFile << """
            plugins {
                id("org.gradle.jvm-toolchains")
            }

            task provision {
                doLast {
                    println("Provisioning Java ${otherJvm.javaVersion}")
                    def launcher = javaToolchains.launcherFor {
                        languageVersion = JavaLanguageVersion.of("${otherJvm.javaVersion.majorVersion}")
                    }.get()
                    file("provisioned.jdk") << launcher.metadata.installationPath.asFile.canonicalPath
                }
            }
        """
        settingsFile << """
            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
            }
        """
        executer.withToolchainDownloadEnabled()
        succeeds("provision")

        writeJvmCriteria(otherJvm)
        expectJavaHome(new File(file("provisioned.jdk").text))

        expect:
        succeeds("help")
    }

    def "Given daemon toolchain criteria that doesn't match installed ones When executing any task Then fails with the expected message"() {
        given:
        // Java 10 is not available
        def java10 = AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_1_10)
        Assume.assumeTrue(java10.isEmpty())
        writeJvmCriteria(JavaVersion.VERSION_1_10)

        expect:
        fails("help")
        failure.assertHasDescription("Cannot find a Java installation on your machine")
    }
}
