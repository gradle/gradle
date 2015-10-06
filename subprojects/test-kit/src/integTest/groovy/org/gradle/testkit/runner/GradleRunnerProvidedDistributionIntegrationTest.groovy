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
import org.gradle.util.DistributionLocator
import org.gradle.util.GradleVersion
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.*

class GradleRunnerProvidedDistributionIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    @Shared
    DistributionLocator locator = new DistributionLocator()
    @Shared
    ReleasedVersionDistributions distributions = new ReleasedVersionDistributions()
    @Shared
    GradleVersion mostRecentSnapshot = distributions.mostRecentSnapshot.version

    def "execute build with different distribution types"() {
        given:
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = runner(gradleDistribution, 'helloWorld')
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
        GradleRunner gradleRunner = runner(new VersionBasedGradleDistribution(gradleVersion), 'helloWorld')
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
}
