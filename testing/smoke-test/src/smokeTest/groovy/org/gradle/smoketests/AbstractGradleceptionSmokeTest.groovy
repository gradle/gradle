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

import org.gradle.api.JavaVersion
import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.SmokeTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions

import java.text.SimpleDateFormat

@Requires([
    UnitTestPreconditions.Jdk9OrLater,
    IntegTestPreconditions.NotConfigCached,
    SmokeTestPreconditions.GradleBuildJvmSpecAvailable
])
abstract class AbstractGradleceptionSmokeTest extends AbstractSmokeTest {

    public static final String TEST_BUILD_TIMESTAMP = "-PbuildTimestamp=" + newTimestamp()
    private static final List<String> GRADLE_BUILD_TEST_ARGS = [TEST_BUILD_TIMESTAMP]

    private SmokeTestGradleRunner.SmokeTestBuildResult result

    def setup() {
        new TestFile("build/gradleBuildCurrent").copyTo(testProjectDir)

        and:
        def buildJavaHome = AvailableJavaHomes.getAvailableJdks(new GradleBuildJvmSpec()).last().javaHome
        file("gradle.properties") << "\norg.gradle.java.home=${buildJavaHome}\n"
    }

    SmokeTestGradleRunner.SmokeTestBuildResult getResult() {
        if (result == null) {
            throw new IllegalStateException("Need to run a build before result is available.")
        }
        return result
    }

    protected void run(List<String> tasks, File testKitDir = null) {
        result = null
        result = runnerFor(tasks, testKitDir).build()
    }

    protected void fails(List<String> tasks, File testKitDir = null) {
        result = null
        result = runnerFor(tasks, testKitDir).buildAndFail()
    }

    private SmokeTestGradleRunner runnerFor(List<String> tasks, File testKitDir) {
        List<String> gradleArgs = tasks + GRADLE_BUILD_TEST_ARGS
        def runner = testKitDir != null
            ? runnerWithTestKitDir(testKitDir, gradleArgs)
            : runner(*gradleArgs)

        runner.ignoreDeprecationWarnings("Gradleception smoke tests don't check for deprecation warnings; TODO: we should add expected deprecations for each task being called")
        runner.withJdkWarningChecksDisabled() // The Gradle build somehow still emits these warnings

        return runner
    }

    private SmokeTestGradleRunner runnerWithTestKitDir(File testKitDir, List<String> gradleArgs) {
        runner(*(gradleArgs + ["-g", IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir.absolutePath]))
            .withTestKitDir(testKitDir)
    }

    private static String newTimestamp() {
        newTimestampDateFormat().format(new Date())
    }

    static SimpleDateFormat newTimestampDateFormat() {
        new SimpleDateFormat('yyyyMMddHHmmssZ').tap {
            setTimeZone(TimeZone.getTimeZone("UTC"))
        }
    }

    static class GradleBuildJvmSpec implements Spec<JvmInstallationMetadata> {

        @Override
        boolean isSatisfiedBy(JvmInstallationMetadata jvm) {
            return jvm.languageVersion == JavaVersion.VERSION_17
        }

    }
}

