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

package org.gradle.integtests.tooling.r90

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.DaemonJvmPropertiesFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@TargetGradleVersion(">=9.0")
class DaemonToolchainCrossVersionTest extends ToolingApiSpecification implements DaemonJvmPropertiesFixture {

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given criteria matching JAVA_HOME environment variable and disabled auto-detection When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion.majorVersion)
        captureJavaHome()

        when:
        withConnection {
            it.newBuild()
                .setEnvironmentVariables(["JAVA_HOME": otherJvm.javaHome.absolutePath])
                .forTasks("help").withArguments(
                "-Dorg.gradle.java.installations.auto-detect=false",
            ).run()
        }

        then:
        assertDaemonUsedJvm(otherJvm.javaHome)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given custom toolchain location using environment variable and disabled auto-detection When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion.majorVersion)
        captureJavaHome()

        when:
        withConnection {
            it.newBuild()
                .setEnvironmentVariables(["OTHER_JAVA_HOME": otherJvm.javaHome.absolutePath])
                .forTasks("help").withArguments(
                    "-Porg.gradle.java.installations.fromEnv=OTHER_JAVA_HOME",
                    "-Dorg.gradle.java.installations.auto-detect=false",
            ).run()
        }

        then:
        assertDaemonUsedJvm(otherJvm.javaHome)
    }
}
