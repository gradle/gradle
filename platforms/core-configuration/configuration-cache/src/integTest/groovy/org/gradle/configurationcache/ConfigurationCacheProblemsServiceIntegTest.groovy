/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache

class ConfigurationCacheProblemsServiceIntegTest extends AbstractConfigurationCacheIntegrationTest {
    @Override
    def setup() {
        enableProblemsApiCheck()
    }

    def "problems are reported through the Problems API"() {
        given:
        buildFile << """
            gradle.buildFinished { }

            task run
        """

        when:
        configurationCacheFails 'run'

        then:
        executed(':run')
        collectedProblems.size() == 1
        with(collectedProblems.get(0)) {
            label == "registration of listener on 'Gradle.buildFinished' is unsupported"
            severity == "ERROR"
        }
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 2: registration of listener on 'Gradle.buildFinished' is unsupported")
        }
        failure.assertHasFailures(1)

        when:
        configurationCacheRunLenient 'run'

        then:
        executed(':run')
        collectedProblems.size() == 1
        with(collectedProblems.get(0)) {
            label == "registration of listener on 'Gradle.buildFinished' is unsupported"
            severity == "WARNING"
        }
    }

    def "max problems are still reported as warnings"() {
        given:
        buildFile << """
            gradle.buildFinished { }

            task run
        """

        when:
        configurationCacheFails WARN_PROBLEMS_CLI_OPT, "-D$MAX_PROBLEMS_GRADLE_PROP=0", 'run'

        then:
        executed(':run')
        collectedProblems.size() == 1
        with(collectedProblems.get(0)) {
            label == "registration of listener on 'Gradle.buildFinished' is unsupported"
            severity == "WARNING"
        }
    }
}
