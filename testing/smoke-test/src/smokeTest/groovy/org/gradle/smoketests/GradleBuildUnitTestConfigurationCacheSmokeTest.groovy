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

class GradleBuildUnitTestConfigurationCacheSmokeTest extends AbstractGradleBuildConfigurationCacheSmokeTest {
    def "can run Gradle unit tests with configuration cache enabled"() {

        given:
        def supportedTasks = [
            ":tooling-api:publishLocalPublicationToLocalRepository",
            ":java-language-extensions:test", "--tests=org.gradle.api.JavaVersionSpec"
        ]

        when:
        configurationCacheRun supportedTasks, 0

        then:
        result.assertConfigurationCacheStateStored()

        when:
        run([":tooling-api:clean", ":base-services:clean"])

        and:
        configurationCacheRun supportedTasks + ["--info"], 1

        then:
        result.assertConfigurationCacheStateLoaded()
        result.output.contains("Starting build in new daemon")
        result.task(":tooling-api:publishLocalPublicationToLocalRepository").outcome == TaskOutcome.SUCCESS
    }
}
