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

package org.gradle.integtests.tooling.r88

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.DaemonJvmPropertiesFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.GradleConnectionException

// 8.8 did not support configuring the set of available Java homes or disabling auto-detection
@TargetGradleVersion(">=8.9")
class DaemonToolchainCoexistWithCurrentOptionsCrossVersionTest extends ToolingApiSpecification implements DaemonJvmPropertiesFixture {

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given toolchain properties are provided then build succeeds"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion.majorVersion)
        captureJavaHome()

        when:
        withConnection {
            it.newBuild().forTasks("help").withArguments(
                "-Dorg.gradle.java.installations.auto-detect=false",
                "-Dorg.gradle.java.installations.paths=${otherJvm.javaHome.canonicalPath}"
            ).run()
        }

        then:
        assertDaemonUsedJvm(otherJvm.javaHome)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given disabled auto-detection When using daemon toolchain Then build fails"() {
        given:
        // We don't want to be able to find an already running compatible daemon
        requireIsolatedDaemons()
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion.majorVersion)
        captureJavaHome()

        when:
        withConnection {
            it.newBuild().forTasks("help").withArguments("-Dorg.gradle.java.installations.auto-detect=false").run()
        }

        then:
        def e = thrown(GradleConnectionException)
        e.cause.message.contains("Cannot find a Java installation on your machine")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined org.gradle.java.home gradle property When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion.majorVersion)
        captureJavaHome()
        file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk8.javaHome.canonicalPath)

        when:
        withConnection {
            it.newBuild().forTasks("help").withArguments(
                "-Dorg.gradle.java.installations.auto-detect=false",
                "-Dorg.gradle.java.installations.paths=${otherJvm.javaHome.canonicalPath}"
            ).run()
        }

        then:
        assertDaemonUsedJvm(otherJvm.javaHome)
    }
}
