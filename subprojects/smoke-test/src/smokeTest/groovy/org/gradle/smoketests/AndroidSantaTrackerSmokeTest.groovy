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

    @UnsupportedWithConfigurationCache(iterationMatchers = [AGP_NO_CC_ITERATION_MATCHER])
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

    @UnsupportedWithConfigurationCache(iterationMatchers = [AGP_NO_CC_ITERATION_MATCHER])
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

    @UnsupportedWithConfigurationCache(iterationMatchers = [AGP_NO_CC_ITERATION_MATCHER])
    def "can lint Santa-Tracker (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        when:
        def runner = runnerForLocation(
            checkoutDir, agpVersion,
            "common:lintDebug", "playgames:lintDebug", "doodles-lib:lintDebug"
        ).deprecations(AndroidLintDeprecations) {
            expectAndroidWorkerExecutionSubmitDeprecationWarning(agpVersion)
            expectAndroidLintDeprecations(agpVersion, [
                "kotlin-android-extensions-runtime-${kotlinVersion}.jar (org.jetbrains.kotlin:kotlin-android-extensions-runtime:${kotlinVersion})",
                "appcompat-1.0.2.aar (androidx.appcompat:appcompat:1.0.2)"
            ])
        }
        // Use --continue so that a deterministic set of tasks runs when some tasks fail
        runner.withArguments(runner.arguments + "--continue")
        def result = runner.buildAndFail()

        then:
        assertConfigurationCacheStateStored()
        result.output.contains("Lint found errors in the project; aborting build.")

        when:
        runner = runnerForLocation(
            checkoutDir, agpVersion,
            "common:lintDebug", "playgames:lintDebug", "doodles-lib:lintDebug"
        ).deprecations(AndroidLintDeprecations) {
            expectAndroidLintDeprecations(agpVersion, [
                "kotlin-android-extensions-runtime-${kotlinVersion}.jar (org.jetbrains.kotlin:kotlin-android-extensions-runtime:${kotlinVersion})",
                "appcompat-1.0.2.aar (androidx.appcompat:appcompat:1.0.2)"
            ])
        }
        runner.withArguments(runner.arguments + "--continue")
        result = runner.buildAndFail()

        then:
        assertConfigurationCacheStateLoaded()
        result.output.contains("Lint found errors in the project; aborting build.")

        where:
        agpVersion << TESTED_AGP_VERSIONS
    }

    static class AndroidLintDeprecations extends BaseDeprecations implements WithAndroidDeprecations {
        AndroidLintDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }

        void expectAndroidLintDeprecations(String agpVersion, List<String> artifacts) {
            artifacts.each { artifact ->
                runner.expectLegacyDeprecationWarningIf(
                    agpVersion.startsWith("4.1"),
                    "In plugin 'com.android.internal.version-check' type 'com.android.build.gradle.tasks.LintPerVariantTask' property 'allInputs' cannot be resolved:  " +
                        "Cannot convert the provided notation to a File or URI: $artifact. " +
                        "The following types/formats are supported:  - A String or CharSequence path, for example 'src/main/java' or '/usr/include'. - A String or CharSequence URI, for example 'file:/usr/include'. - A File instance. - A Path instance. - A Directory instance. - A RegularFile instance. - A URI or URL instance. - A TextResource instance. " +
                        "Reason: An input file collection couldn't be resolved, making it impossible to determine task inputs. " +
                        "Please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/validation_problems.html#unresolvable_input for more details about this problem. " +
                        "This behaviour has been deprecated and is scheduled to be removed in Gradle 8.0. " +
                        "Execution optimizations are disabled to ensure correctness. See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details."
                )
            }
            expectAndroidFileTreeForEmptySourcesDeprecationWarnings(agpVersion, "sourceFiles", "sourceDirs")
            if (agpVersion.startsWith("7.")) {
                expectAndroidFileTreeForEmptySourcesDeprecationWarnings(agpVersion, "inputFiles", "resources")
            }
        }
    }
}
