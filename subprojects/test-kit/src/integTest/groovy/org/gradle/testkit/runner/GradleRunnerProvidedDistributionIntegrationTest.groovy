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

import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.DistributionLocator
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.ClassRule
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.*

@LeaksFileHandles
@Requires(TestPrecondition.ONLINE)
class GradleRunnerProvidedDistributionIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    @Shared
    DistributionLocator locator = new DistributionLocator()

    @Shared
    ReleasedVersionDistributions distributions = new ReleasedVersionDistributions()

    @Shared
    GradleVersion mostRecentSnapshot = distributions.mostRecentSnapshot.version

    @ClassRule
    @Shared
    TestNameTestDirectoryProvider testKitDir = new TestNameTestDirectoryProvider()

    def "execute build with different distribution types"() {
        given:
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = runnerWithSharedTestKitDir(gradleDistribution, 'helloWorld')
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        !result.standardError
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

        where:
        gradleDistribution << [new InstalledGradleDistribution(buildContext.gradleHomeDir),
                               new URILocatedGradleDistribution(locator.getDistributionFor(mostRecentSnapshot)),
                               new VersionBasedGradleDistribution(mostRecentSnapshot.version)]
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "execute build for multiple Gradle versions of the same distribution type"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    // standard output wasn't parsed properly for pre-2.8 Gradle versions in embedded mode
                    // using the Gradle logger instead
                    logger.quiet 'Hello world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = runnerWithSharedTestKitDir(new VersionBasedGradleDistribution(gradleVersion), 'helloWorld')
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        !result.standardError
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

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
        GradleRunner gradleRunner = runnerWithSharedTestKitDir(new VersionBasedGradleDistribution('2.5'), 'dependencies')
        BuildResult result = gradleRunner.buildAndFail()

        then:
        !result.standardOutput.contains(':dependencies')
        result.standardOutput.contains('BUILD FAILED')
        result.standardError.contains("Could not find method gradleTestKit() for arguments [] on root project '$gradleRunner.projectDir.name'")
        result.tasks.empty
        result.taskPaths(SUCCESS).empty
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

    private GradleRunner runnerWithSharedTestKitDir(GradleDistribution<?> gradleDistribution, String... arguments) {
        runner(gradleDistribution, arguments)
        .withTestKitDir(testKitDir.root)
    }
}
