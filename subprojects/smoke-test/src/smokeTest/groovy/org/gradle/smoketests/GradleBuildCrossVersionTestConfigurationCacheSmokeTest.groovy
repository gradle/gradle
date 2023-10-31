/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Ignore

@Ignore("Doesn't temporarily work, because we have the embedded test runner disabled, see the unittest-and-compile internal plugin")
class GradleBuildCrossVersionTestConfigurationCacheSmokeTest extends AbstractGradleBuildConfigurationCacheSmokeTest {
    def "can run Gradle cross-version tests with configuration cache enabled"() {

        given:
        def tasks = [
            ':configuration-cache:embeddedCrossVersionTest',
            '--tests=org.gradle.configurationcache.ConfigurationCacheCrossVersionTest'
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":configuration-cache:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":configuration-cache:embeddedCrossVersionTest").outcome == TaskOutcome.FROM_CACHE
    }
}
