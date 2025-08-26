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

import org.gradle.ide.starter.IdeCommand
import org.gradle.ide.sync.fixtures.IsolatedProjectsIdeSyncFixture
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.fixtures.file.TestFile

class IsolatedProjectsGradleceptionSyncTest extends AbstractIdeSyncTest {

    private IsolatedProjectsIdeSyncFixture fixture = new IsolatedProjectsIdeSyncFixture(testDirectory)

    def "can sync gradle/gradle build without problems"() {
        given:
        gradle()

        when:
        ideaSync(IDEA_COMMUNITY_VERSION)

        then:
        fixture.assertHtmlReportHasNoProblems()
    }

    def "can sync gradle/gradle incrementally without error"() {
        given:
        gradle()

        and:
        ideXmxMb = 4096

        expect:
        ideaSync(
            IDEA_COMMUNITY_VERSION,
            [
                new IdeCommand.AppendTextToFile("subprojects/core-api/build.gradle.kts", "dependencies {}"),
                IdeCommand.ImportGradleProject.INSTANCE
            ]
        )
    }

    private void gradle() {
        new TestFile("build/gradleSources").copyTo(testDirectory)

        file("gradle.properties") << """
            org.gradle.unsafe.isolated-projects=true

            # gradle/gradle build contains gradle/gradle-daemon-jvm.properties, which requires daemon to be run with Java 17.
            # However, on CI JDK's installed not in the default location, and Gradle can't find appropriate toolchain to run.
            # So we need to specify required JDK explicitly.
            org.gradle.java.installations.paths=$AvailableJavaHomes.jdk17.javaHome.absolutePath
        """
    }
}
