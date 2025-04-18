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
import org.junit.Ignore

// 8.8 did not support configuring the set of available Java homes or disabling auto-detection
@TargetGradleVersion(">=8.9")
class DaemonToolchainCrossVersionTest extends ToolingApiSpecification implements DaemonJvmPropertiesFixture {

    @Requires(IntegTestPreconditions.Java8HomeAvailable)
    def "Given daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def jdk8 = AvailableJavaHomes.jdk8
        writeJvmCriteria(jdk8.javaVersion.majorVersion)
        captureJavaHome()
        withInstallations(jdk8.javaHome)

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        assertDaemonUsedJvm(jdk8.javaHome)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given other daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion.majorVersion)
        captureJavaHome()
        withInstallations(otherJvm.javaHome)

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        assertDaemonUsedJvm(otherJvm.javaHome)
    }

    @Ignore("https://github.com/gradle/gradle/issues/32969")
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

    @Requires(IntegTestPreconditions.Java11HomeAvailable)
    def "Given daemon toolchain criteria that doesn't match installed ones When executing any task Then fails with the expected message"() {
        given:
        def jdk11 = AvailableJavaHomes.getJdk11()
        // Java 10 is not available
        writeJvmCriteria("10")
        captureJavaHome()
        withInstallations(jdk11.javaHome)

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        def e= thrown(GradleConnectionException)
        e.cause.message.contains("Cannot find a Java installation on your machine")
    }
}
