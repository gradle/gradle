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
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import spock.lang.Issue

@NonCrossVersion
class GradleRunnerSupportedBuildJvmIntegrationTest extends BaseGradleRunnerIntegrationTest {
    @NoDebug
    @Requires(adhoc = { AvailableJavaHomes.getJdks("1.5", "1.6", "1.7") })
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

    @NoDebug
    @Issue("https://github.com/gradle/gradle/issues/13957")
    @Requires(adhoc = { AvailableJavaHomes.getJdks("1.8") })
    def "supports failing builds on older Java versions"() {
        given:
        testDirectory.file("gradle.properties")
            .writeProperties(
                "org.gradle.java.home": jdk.javaHome.absolutePath,
            )

        buildFile << '''
            task myTask {
                doLast {
                    println "Java version: ${System.getProperty('java.version')}"
                    println "Gradle version: ${gradle.gradleVersion}"
                    println "Args: ${java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()}"
                    def e = new RuntimeException('@Root@', new RuntimeException('@Cause1@', new RuntimeException('@Cause2@')))
                    e.addSuppressed(new RuntimeException('@Suppressed1@'))
                    e.addSuppressed(new RuntimeException('@Suppressed2@'))
                    throw e
                }
            }
        '''

        when:
        def build = (runner("myTask", '-S', '--info') as DefaultGradleRunner)
            .withJvmArguments("-javaagent:/Users/lorinc/IdeaProjects/dotcom/tapi-instrumentation/build/libs/tapi-instrumentation-all.jar")
            .tap {
                if (buildToolVersion != 'LATEST') {
                    withGradleVersion(buildToolVersion)
                }
            }
            .forwardOutput()

        then:
        with(build.buildAndFail().output) {
            if (buildToolVersion != 'LATEST') {
                contains("Gradle version: ${buildToolVersion}")
            }
            contains("@Root@")
            contains("@Cause1@")
            contains("@Cause2@")
            contains("@Suppressed1@")
            contains("@Suppressed2@")
            !contains("StreamCorruptedException")
        }

        where:
        [buildToolVersion, jdk] << [
            ['2.6', '2.7', '2.8', '2.9', '2.11', '2.12', '2.13', '2.14.1', '3.0', '3.1', '3.2.1', '3.3', '3.4.1', '3.5.1', '4.0.2', '4.1', '4.2.1', '4.3.1', '4.4.1', '4.5.1', '4.6', '4.7', '4.8.1', '4.9', '4.10.3', '5.0', '5.1.1', '5.2.1', '5.3.1', '5.4.1', '5.5.1', '5.6.4', '6.0.1', '6.1.1', '6.2.2', '6.3', '6.4.1', '6.5.1', '6.6.1', '6.7.1', '6.8.3', '6.9', '7.0.2', '7.1.1', 'LATEST'],
            AvailableJavaHomes.getJdks('1.8')
        ].combinations()
    }
}
