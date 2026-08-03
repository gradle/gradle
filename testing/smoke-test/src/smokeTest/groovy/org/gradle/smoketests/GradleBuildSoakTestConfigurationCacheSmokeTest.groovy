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

import org.gradle.integtests.fixtures.modes.ToBeFixedForGroovy5
import org.gradle.testkit.runner.TaskOutcome

class GradleBuildSoakTestConfigurationCacheSmokeTest extends AbstractGradleBuildConfigurationCacheSmokeTest {
    @ToBeFixedForGroovy5(
        because = "Gradle workers carry Groovy on their classpath and Groovy 5 needs Java 11, so the Java 8 workers in the build under test cannot start",
        issue = "https://github.com/gradle/gradle/issues/38735"
    )
    def "can run Gradle soak tests with configuration cache enabled"() {

        given:
        def tasks = [
            ':soak:forkingIntegTest',
            '--tests=org.gradle.connectivity.MavenCentralDependencyResolveIntegrationTest'
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        result.assertConfigurationCacheStateStored()

        when:
        run([":soak:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        result.assertConfigurationCacheStateLoaded()
        result.task(":soak:forkingIntegTest").outcome == TaskOutcome.FROM_CACHE
    }
}
