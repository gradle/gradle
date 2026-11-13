/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * Tests for https://github.com/gradle/gradle/issues/39540: when an included build's
 * directory is read-only, the build used to exit 1 with no output at all - no FAILURE
 * section, no BUILD FAILED line, empty stderr - even though the same directory built
 * on its own fails with a proper report.
 */
class CompositeBuildReadOnlyIncludedBuildIntegrationTest extends AbstractIntegrationSpec {
    def inc

    def setup() {
        inc = file("inc")
        inc.file("settings.gradle.kts") << 'rootProject.name = "inc"\n'
        file("settings.gradle.kts") << 'rootProject.name = "app"\nincludeBuild("inc")\n'
        Files.setPosixFilePermissions(inc.toPath(), PosixFilePermissions.fromString("r-xr-xr-x"))
    }

    def cleanup() {
        Files.setPosixFilePermissions(inc.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    @Requires(OsTestPreconditions.Unix)
    def "reports the failure when the included build directory is read-only"() {
        when:
        def failure = executer.withTasks("help").runWithFailure()

        then:
        failure.output.contains("FAILURE: Build failed with an exception.")
        failure.output.contains("inc")
    }

    @Requires(OsTestPreconditions.Unix)
    def "building the read-only directory on its own reports the failure"() {
        when:
        executer.inDirectory(inc)
        def failure = executer.withTasks("help").runWithFailure()

        then:
        failure.output.contains("FAILURE: Build failed with an exception.")
        failure.output.contains("inc")
    }
}
