/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.DistributionLocator
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Requires(TestPrecondition.ONLINE)
class GradleRunnerGradleVersionIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    @Shared
    DistributionLocator locator = new DistributionLocator()

    def "execute build with different distribution types"(Action<GradleRunner> configurer) {
        given:
        buildFile << helloWorldTaskWithLoggerOutput()

        when:
        def runner = runner('helloWorld')
        configurer.execute(runner)
        def result = runner.build()

        then:
        result.taskPaths(SUCCESS) == [':helloWorld']

        where:
        configurer << [
            { it.withGradleInstallation(buildContext.gradleHomeDir) },
            { it.withGradleDistribution(locator.getDistributionFor(GradleVersion.version('2.7'))) },
            { it.withGradleVersion("2.7") }
        ]
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "execute build for multiple Gradle versions of the same distribution type"() {
        given:
        buildFile << helloWorldTaskWithLoggerOutput()

        when:
        def result = runner('helloWorld')
            .withGradleVersion(gradleVersion)
            .build()

        then:
        result.taskPaths(SUCCESS) == [':helloWorld']

        where:
        gradleVersion << ['2.6', '2.7']
    }

    @LeaksFileHandles
    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "distributions are not stored in the test kit dir"() {
        given:
        requireIsolatedTestKitDir = true

        def version = "2.6"
        buildFile << '''task v << {
            file("gradleVersion.txt").text = gradle.gradleVersion
            file("gradleHomeDir.txt").text = gradle.gradleHomeDir.canonicalPath
        }'''

        when:
        runner('v')
            .withGradleVersion(version)
            .build()

        then:
        file("gradleVersion.txt").text == version

        // Note: GradleRunnerIntegTestRunner configures the test env to use this gradle user home dir
        file("gradleHomeDir.txt").text.startsWith(new IntegrationTestBuildContext().gradleUserHomeDir.absolutePath)

        testKitDir.eachFileRecurse {
            assert !it.name.contains("gradle-$version-bin.zip")
        }
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "fails a build that uses unsupported APIs for a Gradle version"() {
        given:
        buildFile << """
            configurations {
                functionalTestCompile
            }

            dependencies {
                // method was introduced in Gradle 2.6
                functionalTestCompile gradleTestKit()
            }
        """

        when:
        def result = runner('dependencies')
            .withGradleVersion("2.5")
            .buildAndFail()

        then:
        result.output.contains("Could not find method gradleTestKit() for arguments [] on root project '$rootProjectName'")
    }

    static String helloWorldTaskWithLoggerOutput() {
        """
            task helloWorld {
                doLast {
                    // standard output wasn't parsed properly for pre-2.8 Gradle versions in embedded mode
                    // using the Gradle logger instead
                    logger.quiet 'Hello world!'
                }
            }
        """
    }
}
