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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.text.SimpleDateFormat

@Requires(value = TestPrecondition.JDK9_OR_LATER, adhoc = {
    GradleContextualExecuter.isNotConfigCache() && GradleBuildJvmSpec.isAvailable()
})
abstract class AbstractGradleceptionSmokeTest extends AbstractSmokeTest {
    private static final List<String> GRADLE_BUILD_TEST_ARGS = [
        "-PbuildTimestamp=" + newTimestamp()
    ]

    protected BuildResult result

    def setup() {
        new TestFile("build/gradleBuildCurrent").copyTo(testProjectDir.root)

        and:
        def buildJavaHome = AvailableJavaHomes.getAvailableJdks(new GradleBuildJvmSpec()).last().javaHome
        file("gradle.properties") << "\norg.gradle.java.home=${buildJavaHome}\n"

    }

    BuildResult getResult() {
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

    private GradleRunner runnerFor(List<String> tasks, File testKitDir) {
        List<String> gradleArgs = tasks + GRADLE_BUILD_TEST_ARGS
        return testKitDir != null
            ? runnerWithTestKitDir(testKitDir, gradleArgs)
            : runner(*gradleArgs)
    }

    private GradleRunner runnerWithTestKitDir(File testKitDir, List<String> gradleArgs) {
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

        static boolean isAvailable() {
            return AvailableJavaHomes.getAvailableJdk(new GradleBuildJvmSpec()) != null
        }

        @Override
        boolean isSatisfiedBy(JvmInstallationMetadata jvm) {
            def version = jvm.languageVersion
            return version >= JavaVersion.VERSION_1_9 && version <= JavaVersion.VERSION_11
        }
    }
}

