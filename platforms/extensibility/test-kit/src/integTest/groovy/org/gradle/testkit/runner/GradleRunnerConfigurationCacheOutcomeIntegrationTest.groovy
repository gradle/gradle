/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.testkit.runner.internal.feature.TestKitFeature

import static org.gradle.testkit.runner.ConfigurationCacheOutcome.STORE_FAILED
import static org.gradle.testkit.runner.ConfigurationCacheOutcome.NOT_ENABLED
import static org.gradle.testkit.runner.ConfigurationCacheOutcome.STORE_SKIPPED
import static org.gradle.testkit.runner.ConfigurationCacheOutcome.REUSED
import static org.gradle.testkit.runner.ConfigurationCacheOutcome.STORED

@NonCrossVersion
class GradleRunnerConfigurationCacheOutcomeIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "reports NOT_ENABLED when the configuration cache is not used"() {
        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld').build()

        then:
        result.configurationCacheOutcome == NOT_ENABLED
    }

    def "reports STORED on a cache miss and REUSED on a cache hit"() {
        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld', '--configuration-cache').build()

        then:
        result.configurationCacheOutcome == STORED

        when:
        result = runner('helloWorld', '--configuration-cache').build()

        then:
        result.configurationCacheOutcome == REUSED
    }

    def "reports the outcome when running with --quiet"() {
        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld', '--configuration-cache', '--quiet').build()

        then:
        def output = result.output
        result.configurationCacheOutcome == STORED
        output.empty || !output.contains("Configuration cache entry stored.")
    }

    def "reports STORE_SKIPPED when an incompatible task is scheduled"() {
        given:
        buildFile << """
            task incompatible {
                notCompatibleWithConfigurationCache("declarative reason")
                doLast { }
            }
        """

        when:
        def result = runner('incompatible', '--configuration-cache').build()

        then:
        result.configurationCacheOutcome == STORE_SKIPPED
    }

    def "reports STORE_FAILED when problems fail the build"() {
        given:
        buildFile << """
            gradle.buildFinished { }
            task broken
        """

        when:
        def result = runner('broken', '--configuration-cache').buildAndFail()

        then:
        result.configurationCacheOutcome == STORE_FAILED
    }

    def "reports STORE_SKIPPED on a cache miss in read-only mode"() {
        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld', '--configuration-cache', '-Dorg.gradle.configuration-cache.read-only=true').build()

        then:
        result.configurationCacheOutcome == STORE_SKIPPED
    }

    def "fails informatively when trying to inspect the configuration cache outcome with unsupported gradle version"() {
        def maxUnsupportedVersion = new ReleasedVersionDistributions().all
            .collect { it.version }
            .findAll { it < TestKitFeature.CAPTURE_CONFIGURATION_CACHE_OUTCOME.since }
            .max()
            .version
        def minSupportedVersion = TestKitFeature.CAPTURE_CONFIGURATION_CACHE_OUTCOME.since.version

        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion)
            .build()

        and:
        result.configurationCacheOutcome

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not inspect the configuration cache outcome with the GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }
}
