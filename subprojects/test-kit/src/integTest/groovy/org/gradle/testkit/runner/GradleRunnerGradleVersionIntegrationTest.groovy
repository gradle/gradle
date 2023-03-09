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
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestKitPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.util.GradleVersion
import org.gradle.util.internal.DistributionLocator
import spock.lang.Retry
import spock.lang.Shared

import static org.gradle.integtests.fixtures.RetryConditions.onIssueWithReleasedGradleVersion

@NonCrossVersion
// FIXME:
@Requires([
    UnitTestPreconditions.Online,
    TestKitPreconditions.LowestMajorGradleIsAvailable
])
@Retry(condition = { onIssueWithReleasedGradleVersion(instance, failure) }, count = 2)
class GradleRunnerGradleVersionIntegrationTest extends BaseGradleRunnerIntegrationTest {
    @Shared
    DistributionLocator locator = new DistributionLocator()

    String getReleasedGradleVersion() {
        lowestMajorGradleVersion
    }

    DaemonsFixture getDaemonsFixture() {
        testKitDaemons(GradleVersion.version(lowestMajorGradleVersion))
    }

    def "execute build with different distribution types #type"(String version, Action<GradleRunner> configurer) {
        given:
        requireIsolatedTestKitDir = true
        buildFile << """
            task writeVersion {
                doLast {
                    file("version.txt").with {
                        createNewFile()
                        text = gradle.gradleVersion
                    }
                }
            }
        """

        when:
        def runner = this.runner('writeVersion')
        configurer.execute(runner)
        runner.build()

        then:
        file("version.txt").text == version

        cleanup:
        killDaemons(version)

        where:
        type         | version                      | configurer
        "embedded"   | buildContext.version.version | { if (!GradleContextualExecuter.embedded) { it.withGradleInstallation(buildContext.gradleHomeDir) } }
        "locator"    | lowestMajorGradleVersion     | { it.withGradleDistribution(locator.getDistributionFor(GradleVersion.version(lowestMajorGradleVersion))) }
        "production" | lowestMajorGradleVersion     | { it.withGradleVersion(lowestMajorGradleVersion) }
    }

    def "distributions are not stored in the test kit dir"() {
        given:
        requireIsolatedTestKitDir = true

        buildFile << '''task v {
            doLast {
                file("gradleVersion.txt").text = gradle.gradleVersion
                file("gradleHomeDir.txt").text = gradle.gradleHomeDir.canonicalPath
            }
        }'''

        when:
        runner('v')
            .withGradleVersion(lowestMajorGradleVersion)
            .build()

        then:
        file("gradleVersion.txt").text == lowestMajorGradleVersion

        and:
        // Note: AbstractGradleRunnerIntegTest configures the test env to use this gradle user home dir
        file("gradleHomeDir.txt").text.startsWith(buildContext.gradleUserHomeDir.absolutePath)

        and:
        testKitDir.eachFileRecurse {
            assert !it.name.contains("gradle-$lowestMajorGradleVersion-bin.zip")
        }

        cleanup:
        killDaemons(lowestMajorGradleVersion)
    }

    private void killDaemons(String version) {
        if (!debug) {
            testKitDaemons(GradleVersion.version(version)).killAll()
        }
    }
}
