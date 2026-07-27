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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.profiler.mutations.ApplyBuildScriptChangeFileMutator
import org.gradle.test.fixtures.file.TestFile

class IsolatedProjectsGradleceptionSyncTest extends AbstractIdeSyncTest {

    def setup() {
        ideJvmArgs = ["-Xmx4G"]
    }

    def "can sync gradle/gradle build without problems"() {
        given:
        gradle()

        when:
        ideaSync()

        then:
        report.htmlReport().assertHasNoProblems()
    }

    def "can sync gradle/gradle incrementally without error"() {
        given:
        gradle()

        expect:
        ideaSync([new ApplyBuildScriptChangeFileMutator(projectFile("subprojects/core-api/build.gradle.kts"))])
    }

    private void gradle() {
        new TestFile("build/gradleSources").copyTo(projectDirectory)
        def installationPaths = AvailableJavaHomes.availableJvms.collect { it.javaHome.absolutePath.replace("\\", "/") }.join(",")
        projectFile("gradle.properties") << """
            # Forward all known JDK installations so the inner gradle/gradle build can locate the daemon toolchain it requires (gradle-daemon-jvm.properties).
            org.gradle.java.installations.paths=$installationPaths

            # we don't want to publish scans
            systemProp.scan.dump=true
        """
    }
}
