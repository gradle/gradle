/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.util.GradleVersion

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class AndroidSantaTrackerSmokeTest extends AbstractAndroidSantaTrackerSmokeTest {

    // TODO:configuration-cache remove once fixed upstream
    @Override
    protected int maxConfigurationCacheProblems() {
        return 150
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [AGP_4_0_ITERATION_MATCHER, AGP_4_1_ITERATION_MATCHER])
    def "check deprecation warnings produced by building Santa Tracker (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        when:
        buildLocationMaybeExpectingWorkerExecutorDeprecation(checkoutDir, agpVersion)

        then:
        assertConfigurationCacheStateStored()

        where:
        agpVersion << TESTED_AGP_VERSIONS
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [AGP_4_0_ITERATION_MATCHER, AGP_4_1_ITERATION_MATCHER])
    def "incremental Java compilation works for Santa Tracker (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        and:
        def pathToClass = "com/google/android/apps/santatracker/tracker/ui/BottomSheetBehavior"
        def fileToChange = checkoutDir.file("tracker/src/main/java/${pathToClass}.java")
        def compiledClassFile = checkoutDir.file("tracker/build/intermediates/javac/debug/classes/${pathToClass}.class")

        when:
        def result = buildLocationMaybeExpectingWorkerExecutorDeprecation(checkoutDir, agpVersion)
        def md5Before = compiledClassFile.md5Hash

        then:
        result.task(":tracker:compileDebugJavaWithJavac").outcome == SUCCESS
        assertConfigurationCacheStateStored()

        when:
        fileToChange.replace("computeCurrentVelocity(1000", "computeCurrentVelocity(2000")
        buildLocationMaybeExpectingWorkerExecutorDeprecation(checkoutDir, agpVersion)
        def md5After = compiledClassFile.md5Hash

        then:
        result.task(":tracker:compileDebugJavaWithJavac").outcome == SUCCESS
        assertConfigurationCacheStateLoaded()
        md5After != md5Before

        where:
        agpVersion << TESTED_AGP_VERSIONS
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [AGP_4_0_ITERATION_MATCHER, AGP_4_1_ITERATION_MATCHER, AGP_4_2_ITERATION_MATCHER])
    def "can lint Santa-Tracker (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        when:
        def runner = runnerForLocationExpectingLintDeprecations(checkoutDir, agpVersion, "lintDebug",
            [
                "wearable-2.3.0.jar (com.google.android.wearable:wearable:2.3.0)",
                "kotlin-android-extensions-runtime-${kotlinVersion}.jar (org.jetbrains.kotlin:kotlin-android-extensions-runtime:${kotlinVersion})"
            ])
        def result = runner.buildAndFail()

        then:
        assertConfigurationCacheStateStored()
        result.output.contains("Lint found errors in the project; aborting build.")

        when:
        result = runnerForLocationExpectingLintDeprecations(checkoutDir, agpVersion, "lintDebug",
            [
                "wearable-2.3.0.jar (com.google.android.wearable:wearable:2.3.0)",
                "kotlin-android-extensions-runtime-${kotlinVersion}.jar (org.jetbrains.kotlin:kotlin-android-extensions-runtime:${kotlinVersion})",
                "appcompat-1.0.2.aar (androidx.appcompat:appcompat:1.0.2)"
            ])
            .buildAndFail()

        then:
        assertConfigurationCacheStateLoaded()
        result.output.contains("Lint found errors in the project; aborting build.")

        where:
        agpVersion << TESTED_AGP_VERSIONS
    }

    private SmokeTestGradleRunner runnerForLocationExpectingLintDeprecations(File location, String agpVersion, String task, List<String> artifacts) {
        SmokeTestGradleRunner runner = runnerForLocationMaybeExpectingWorkerExecutorDeprecation(location, agpVersion, task)
        artifacts.each { artifact ->
            runner.expectLegacyDeprecationWarningIf(
                agpVersion.startsWith("4.1"),
                "In plugin 'com.android.internal.version-check' type 'com.android.build.gradle.tasks.LintPerVariantTask' property 'allInputs' cannot be resolved:  " +
                    "Cannot convert the provided notation to a File or URI: $artifact. " +
                    "The following types/formats are supported:  - A String or CharSequence path, for example 'src/main/java' or '/usr/include'. - A String or CharSequence URI, for example 'file:/usr/include'. - A File instance. - A Path instance. - A Directory instance. - A RegularFile instance. - A URI or URL instance. - A TextResource instance. " +
                    "Reason: An input file collection couldn't be resolved, making it impossible to determine task inputs. " +
                    "Please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/validation_problems.html#unresolvable_input for more details about this problem. " +
                    "This behaviour has been deprecated and is scheduled to be removed in Gradle 8.0. " +
                    "Execution optimizations are disabled to ensure correctness. See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.")
        }
        return runner
    }
}
