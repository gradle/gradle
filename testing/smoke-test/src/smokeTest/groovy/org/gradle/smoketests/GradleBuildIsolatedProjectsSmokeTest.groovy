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

        // Prevents the power assert from dumping all the output if the check below fails.
        def report = fixture.htmlReport(result.output)

        report.assertContents {
            totalProblemsCount = 1
            withUniqueProblems(
                "Project ':docs' cannot dynamically look up a property in the parent project ':'",
            )
        }
    }

    def "can schedule all Gradle build tasks with isolated projects enabled"() {
        def scheduleAllTasksScript = "schedule-all-tasks.gradle"

        File scheduleAllTasksScriptFile = new File(testProjectDir, scheduleAllTasksScript)
        scheduleAllTasksScriptFile << getClass().getResource(scheduleAllTasksScript).text
        def fixture = new ConfigurationCacheProblemsFixture(testProjectDir)

        given:
        // sets properties that are required by tasks being realized
        def requiredGradleProperties = [
            "-Pgradle_installPath=/dev/null",
            "-PartifactoryUserName=foo",
            "-PartifactoryUserPassword=bar",
            "-PtoolingApiShadedJarInstallPath=/tmp"
        ]
        def requiredEnvironmentVars = [
            "GRADLE_INTERNAL_REPO_URL": "file:///bogus-repository",
        ]
        def tasks = [
            "--init-script",
            scheduleAllTasksScriptFile.absolutePath,
            "scheduleAll",
            // see https://github.com/gradle/gradle-org-conventions-plugin/blob/185ed5cd4923c061a1c70d77c27758df4c80c6d9/src/main/java/io/github/gradle/conventions/customvalueprovider/GitInformationCustomValueProvider.java#L24
            "--no-scan",
            // avoid hitting Develocity features that require further configuration
            "--no-build-cache"
        ] + requiredGradleProperties

        when:
        maxIsolatedProjectProblems = 200000
        run(isolatedProjectsRunner(tasks).withEnvironment(requiredEnvironmentVars))

        then:
        fixture.htmlReport(result.output).assertContents {
            withUniqueProblems(
                "Project ':' cannot access 'Project.plugins' functionality on subprojects via 'allprojects'",
                "Project ':' cannot access 'Project.extensions' functionality on subprojects via 'allprojects'",
            )
            // maximum number of problems we collect (should be 86520)
            totalProblemsCount = 4096
        }
    }
}
