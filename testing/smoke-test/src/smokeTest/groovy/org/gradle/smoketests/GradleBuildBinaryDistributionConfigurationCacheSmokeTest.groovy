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

class GradleBuildBinaryDistributionConfigurationCacheSmokeTest extends AbstractGradleBuildConfigurationCacheSmokeTest {
    def "can build and install Gradle binary distribution with configuration cache enabled"() {

        given:
        def tasks = [
            ':distributions-full:binDistributionZip',
            ':distributions-full:binInstallation'
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        result.assertConfigurationCacheStateStored()

        when:
        run([":distributions-full:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        result.assertConfigurationCacheStateLoaded()
        result.task(":distributions-full:binDistributionZip").outcome == TaskOutcome.SUCCESS
        result.task(":distributions-full:binInstallation").outcome == TaskOutcome.SUCCESS
    }
}
