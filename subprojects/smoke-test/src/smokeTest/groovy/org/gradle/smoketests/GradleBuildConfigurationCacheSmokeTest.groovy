/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.jvm.JvmInstallation
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.text.SimpleDateFormat


/**
 * Smoke test building gradle/gradle with configuration cache enabled.
 *
 * gradle/gradle requires Java >=9 and <=11 to build, see {@link GradleBuildJvmSpec}.
 *
 * This test takes a while to run so is disabled for the Java 8 smoke test CI job and
 * only run on the Java 14 smoke test CI job, see the {@link Requires} annotation below.
 */
@Requires(value = TestPrecondition.JDK9_OR_LATER, adhoc = {
    GradleContextualExecuter.isNotConfigCache() && GradleBuildJvmSpec.isAvailable()
})
class GradleBuildConfigurationCacheSmokeTest extends AbstractSmokeTest {
    private BuildResult result

    BuildResult getResult() {
        if (result == null) {
            throw new IllegalStateException("Need to run a build before result is availble.")
        }
        return result
    }

    def "can build gradle with configuration cache enabled"() {

        given:
        new TestFile("build/gradleBuildCurrent").copyTo(testProjectDir.root)

        and:
        def buildJavaHome = AvailableJavaHomes.getAvailableJdks(new GradleBuildJvmSpec()).last().javaHome
        file("gradle.properties") << "\norg.gradle.java.home=${buildJavaHome}\n"

        and:
        def supportedTasks = [
            // todo broken by kotlin upgrade
            // ":distributions-full:binDistributionZip",
            ":tooling-api:publishLocalPublicationToLocalRepository",
            // ":configuration-cache:embeddedIntegTest", "--tests=org.gradle.configurationcache.ConfigurationCacheIntegrationTest"
        ]

        when:
        configurationCacheRun(*supportedTasks)

        then:
        result.output.count("Calculating task graph as no configuration cache is available") == 1

        when:
        configurationCacheRun(*supportedTasks)

        then:
        result.output.count("Reusing configuration cache") == 1
        // result.task(":distributions-full:binDistributionZip").outcome == TaskOutcome.UP_TO_DATE
        result.task(":tooling-api:publishLocalPublicationToLocalRepository").outcome == TaskOutcome.SUCCESS
        // result.task(":configuration-cache:embeddedIntegTest").outcome == TaskOutcome.UP_TO_DATE

        when:
        run("clean")

        and:
        configurationCacheRun(*supportedTasks)

        then:
        result.output.count("Reusing configuration cache") == 1

        /*
        and:
        file("subprojects/distributions-full/build/distributions").allDescendants().count { it ==~ /gradle-.*-bin.zip/ } == 1
        result.task(":configuration-cache:embeddedIntegTest").outcome == TaskOutcome.SUCCESS
        assertTestClassExecutedIn("subprojects/configuration-cache", "org.gradle.configurationcache.ConfigurationCacheIntegrationTest")
        */
    }

    private TestExecutionResult assertTestClassExecutedIn(String subProjectDir, String testClass) {
        new DefaultTestExecutionResult(file(subProjectDir), "build", "", "", "embeddedIntegTest")
            .assertTestClassesExecuted(testClass)
    }

    private void configurationCacheRun(String... tasks) {
        result = run(
            "--${ConfigurationCacheOption.LONG_OPTION}",
            "--${ConfigurationCacheProblemsOption.LONG_OPTION}=warn", // TODO remove
            *tasks
        )
    }

    BuildResult run(String... tasks) {
        result = null
        return runner(*(tasks + GRADLE_BUILD_TEST_ARGS)).build()
    }

    private static final String[] GRADLE_BUILD_TEST_ARGS = [
        "-PbuildTimestamp=" + newTimestamp()
    ]

    private static String newTimestamp() {
        newTimestampDateFormat().format(new Date())
    }

    static SimpleDateFormat newTimestampDateFormat() {
        new SimpleDateFormat('yyyyMMddHHmmssZ').tap {
            setTimeZone(TimeZone.getTimeZone("UTC"))
        }
    }
}


class GradleBuildJvmSpec implements Spec<JvmInstallation> {

    static boolean isAvailable() {
        return AvailableJavaHomes.getAvailableJdk(new GradleBuildJvmSpec()) != null
    }

    @Override
    boolean isSatisfiedBy(JvmInstallation jvm) {
        return jvm.javaVersion >= JavaVersion.VERSION_1_9 && jvm.javaVersion <= JavaVersion.VERSION_11
    }
}
