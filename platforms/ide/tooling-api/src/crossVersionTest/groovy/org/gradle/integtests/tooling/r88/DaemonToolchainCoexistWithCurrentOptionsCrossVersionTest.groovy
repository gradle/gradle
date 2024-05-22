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

@TargetGradleVersion(">=8.8")
class DaemonToolchainCoexistWithCurrentOptionsCrossVersionTest extends ToolingApiSpecification implements DaemonJvmPropertiesFixture {

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given disabled auto-detection When using daemon toolchain Then option is ignored resolving with expected toolchain"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion.majorVersion)
        captureJavaHome()

        when:
        withConnection {
            it.newBuild().forTasks("help").withArguments("-Porg.gradle.java.installations.auto-detect=false").run()
        }

        then:
        assertDaemonUsedJvm(otherJvm.javaHome)
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
            it.newBuild().forTasks("help").run()
        }

        then:
        assertDaemonUsedJvm(otherJvm.javaHome)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties When executing any task passing them as arguments Then those are ignored since aren't defined on daemon-jvm properties file"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        captureJavaHome()


        when:
        withConnection {
            it.newBuild().forTasks("help").withArguments("-PtoolchainVersion=$otherJvmMetadata.javaVersion -PtoolchainVendor=$otherJvmMetadata.vendor.knownVendor").run()
        }

        then:
        assertDaemonUsedJvm(currentJavaHome)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given daemon toolchain properties defined on gradle properties When executing any task Then those are ignored since aren't defined on daemon-jvm properties file"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        captureJavaHome()
        file("gradle.properties")
            .writeProperties(
                "toolchainVersion": otherJvmMetadata.javaVersion,
                "toolchainVendor": otherJvmMetadata.vendor.knownVendor.name()
            )

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        assertDaemonUsedJvm(currentJavaHome)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined org.gradle.java.home under Build properties When executing any task Then this is ignored since isn't defined on gradle properties file"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherJvmMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)
        captureJavaHome()

        file("gradle/gradle-daemon-jvm.properties")
            .writeProperties(
                "org.gradle.java.home": otherJvmMetadata.javaVersion,
            )

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        assertDaemonUsedJvm(currentJavaHome)
    }
}
