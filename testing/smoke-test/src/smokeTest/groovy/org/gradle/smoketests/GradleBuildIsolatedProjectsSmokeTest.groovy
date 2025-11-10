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

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheProblemsFixture

class GradleBuildIsolatedProjectsSmokeTest extends AbstractGradleBuildIsolatedProjectsSmokeTest {

    def "can run Gradle build tasks with isolated projects enabled"() {
        def fixture = new ConfigurationCacheProblemsFixture(testProjectDir)
        given:
        def tasks = [
            "build",
            "sanityCheck",
            "test",
            "embeddedIntegTest",
            // AsciidoctorTask only became CC compatible in version 5 (https://github.com/asciidoctor/asciidoctor-gradle-plugin/issues/564),
            // skip these tasks to avoid non-IP problems
            "-x", ":docs:samplesMultiPage",
            "-x", ":docs:userguideMultiPage",
            "-x", ":docs:userguideSinglePageHtml"
        ]

        when:
        maxIsolatedProjectProblems = 1
        isolatedProjectsRun(tasks)

        then:
        result.assertConfigurationCacheStateStoreDiscarded()

        fixture.assertHtmlReportHasProblems(result.output) {
            totalProblemsCount = 1
            withUniqueProblems(
                "Project :docs cannot dynamically look up a property in the parent project :",
            )
        }
    }
}
