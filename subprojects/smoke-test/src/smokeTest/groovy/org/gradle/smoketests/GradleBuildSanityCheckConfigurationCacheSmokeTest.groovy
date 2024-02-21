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

class GradleBuildSanityCheckConfigurationCacheSmokeTest extends AbstractGradleBuildConfigurationCacheSmokeTest {

    def "can run Gradle sanityCheck with configuration cache enabled"() {
        given:
        // This is an approximation, running the whole build lifecycle 'sanityCheck' is too expensive
        // See build-logic/lifecycle/src/main/kotlin/gradlebuild.lifecycle.gradle.kts
        def tasks = [
            ":configuration-cache:sanityCheck",
            ":docs:checkstyleApi",
            ":internal-build-reports:allIncubationReportsZip",
            ":architecture-test:checkBinaryCompatibility",
            ":docs:javadocAll",
            ":architecture-test:test",
            ":tooling-api:toolingApiShadedJar",
            ":performance:verifyPerformanceScenarioDefinitions",
            ":checkSubprojectsInfo",
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        assertConfigurationCacheStateStored()

        when:
        run([
            ":configuration-cache:clean",
            ":docs:clean",
            ":internal-build-reports:clean",
            ":architecture-test:clean",
            ":tooling-api:clean",
            ":performance:clean",
        ])

        then:
        configurationCacheRun(tasks, 1)

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":configuration-cache:runKtlintCheckOverMainSourceSet").outcome == TaskOutcome.FROM_CACHE
        result.task(":configuration-cache:validatePlugins").outcome == TaskOutcome.FROM_CACHE
        result.task(":configuration-cache:codenarcIntegTest").outcome == TaskOutcome.UP_TO_DATE
        result.task(":configuration-cache:checkstyleIntegTestGroovy").outcome == TaskOutcome.FROM_CACHE
        result.task(":configuration-cache:archTest").outcome == TaskOutcome.FROM_CACHE
        result.task(":configuration-cache:codeQuality").outcome == TaskOutcome.UP_TO_DATE
        result.task(":docs:checkstyleApi").outcome == TaskOutcome.FROM_CACHE
        result.task(":internal-build-reports:allIncubationReportsZip").outcome == TaskOutcome.SUCCESS
        result.task(":architecture-test:checkBinaryCompatibility").outcome == TaskOutcome.FROM_CACHE
        result.task(":docs:javadocAll").outcome == TaskOutcome.FROM_CACHE
        result.task(":architecture-test:test").outcome == TaskOutcome.FROM_CACHE
        result.task(":tooling-api:toolingApiShadedJar").outcome == TaskOutcome.SUCCESS
        result.task(":performance:verifyPerformanceScenarioDefinitions").outcome == TaskOutcome.SUCCESS
        result.task(":checkSubprojectsInfo").outcome == TaskOutcome.SUCCESS
    }
}
