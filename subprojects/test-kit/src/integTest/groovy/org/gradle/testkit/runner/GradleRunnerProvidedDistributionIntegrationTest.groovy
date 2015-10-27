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

import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.DistributionLocator
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@LeaksFileHandles
@Requires(TestPrecondition.ONLINE)
class GradleRunnerProvidedDistributionIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    @Shared
    DistributionLocator locator = new DistributionLocator()

    def "execute build with different distribution types"() {
        given:
        buildFile << helloWorldTaskWithLoggerOutput()

        when:
        def result = runner(gradleDistribution, 'helloWorld')
            .build()

        then:
        result.taskPaths(SUCCESS) == [':helloWorld']

        where:
        gradleDistribution << [
            GradleDistribution.fromPath(buildContext.gradleHomeDir),
            GradleDistribution.fromUri(locator.getDistributionFor(GradleVersion.version('2.7'))),
            GradleDistribution.withVersion('2.7')
        ]
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "execute build for multiple Gradle versions of the same distribution type"() {
        given:
        buildFile << helloWorldTaskWithLoggerOutput()

        when:
        def result = runner(GradleDistribution.withVersion(gradleVersion), 'helloWorld')
            .build()

        then:
        result.taskPaths(SUCCESS) == [':helloWorld']

        where:
        gradleVersion << ['2.6', '2.7']
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
        def result = runner(GradleDistribution.withVersion('2.5'), 'dependencies')
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
