/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.GradleVersion
import spock.lang.Issue

@NonCrossVersion
@DoesNotSupportNonAsciiPaths(reason = "Java 6 seems to have issues with non-ascii paths")
@Flaky(because = "https://github.com/gradle/gradle-private/issues/3890")
class GradleRunnerSupportedBuildJvmIntegrationTest extends BaseGradleRunnerIntegrationTest {
    @NoDebug
    @Requires(IntegTestPreconditions.UnsupportedJavaHomeAvailable)
    def "fails when build is configured to use Java 7 or earlier"() {
        given:
        testDirectory.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)
        String args = OperatingSystem.current().windows ? "args '-D${StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY}=false'" : 'no args'

        when:
        runner().buildAndFail()

        then:
        IllegalStateException e = thrown()
        e.message.startsWith("An error occurred executing build with ${args} in directory ")
        e.cause instanceof GradleConnectionException
        e.cause.cause.message == "Gradle ${GradleVersion.current().version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6", "1.7")
    }


    @Issue("https://github.com/gradle/gradle/issues/13957")
    @NoDebug
    @Requires(IntegTestPreconditions.Java8HomeAvailable)
    def "supports failing builds on older Java versions"() {
        given:
        testDirectory.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)
        buildFile << """
            task myTask {
                doLast {
                    throw new RuntimeException("Boom")
                }
            }
        """

        expect:
        runner().withArguments("myTask").buildAndFail()

        where:
        jdk << AvailableJavaHomes.getJdks("1.8")
    }
}
