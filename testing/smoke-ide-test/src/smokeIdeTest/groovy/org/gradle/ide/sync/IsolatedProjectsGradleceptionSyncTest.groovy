/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.ide.sync

import org.gradle.ide.starter.IdeScenarioBuilder
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.fixtures.file.TestFile

class IsolatedProjectsGradleceptionSyncTest extends AbstractIdeSyncTest {

    def "can sync gradle/gradle build without problems"() {
        given:
        gradle()

        and:
        ideXmxMb = 4096

        when:
        ideaSync(IDEA_COMMUNITY_VERSION)

        then:
        report.assertHtmlReportHasProblems {
            totalProblemsCount = 12
            withUniqueProblems(
                "Project :declarative-dsl-core cannot dynamically look up a property in the parent project :",
                "Project :declarative-dsl-evaluator cannot dynamically look up a property in the parent project :",
                "Project :declarative-dsl-tooling-models cannot dynamically look up a property in the parent project :",
                "Project :kotlin-dsl-plugins cannot dynamically look up a property in the parent project :"
            )
        }
    }

    def "can sync gradle/gradle incrementally without error"() {
        given:
        gradle()

        and:
        ideXmxMb = 4096

        expect:
        ideaSync(
            IDEA_COMMUNITY_VERSION,
            IdeScenarioBuilder
                .initialImportProject()
                .appendTextToFile("subprojects/core-api/build.gradle.kts", "dependencies {}")
                .importProject()
                .finish()
        )
    }

    private void gradle() {
        new TestFile("build/gradleSources").copyTo(projectDirectory)
        projectFile("gradle.properties") << """
            org.gradle.configuration-cache.problems=warn
            org.gradle.unsafe.isolated-projects=true

            # gradle/gradle build contains gradle/gradle-daemon-jvm.properties, which requires daemon to be run with Java 17.
            # However, on CI JDK's installed not in the default location, and Gradle can't find appropriate toolchain to run.
            # So we need to specify required JDK explicitly.
            org.gradle.java.installations.paths=$AvailableJavaHomes.jdk17.javaHome.absolutePath

            # we don't want to publish scans
            systemProp.scan.dump=true
        """
    }
}
