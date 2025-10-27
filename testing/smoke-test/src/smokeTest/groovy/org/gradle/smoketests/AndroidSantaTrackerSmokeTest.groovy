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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

abstract class AndroidSantaTrackerSmokeTest extends AbstractAndroidSantaTrackerSmokeTest {
}

class AndroidSantaTrackerDeprecationSmokeTest extends AndroidSantaTrackerSmokeTest {
    def "check deprecation warnings produced by building Santa Tracker (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        when:
        def result = runnerForLocation(checkoutDir, agpVersion, "assembleDebug")
            .deprecations(AndroidDeprecations) {
                expectMultiStringNotationDeprecation(agpVersion)
            }
            .build()

        then:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        where:
        agpVersion << TestedVersions.androidGradleBefore9.versions
    }
}

class AndroidSantaTrackerIncrementalCompilationSmokeTest extends AndroidSantaTrackerSmokeTest {
    def "incremental Java compilation works for Santa Tracker (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir)

        and:
        def pathToClass = "com/google/android/apps/santatracker/tracker/ui/BottomSheetBehavior"
        def fileToChange = checkoutDir.file("tracker/src/main/java/${pathToClass}.java")
        def compiledClassFile = checkoutDir.file("tracker/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes/${pathToClass}.class")

        when:
        def result = buildLocation(checkoutDir, agpVersion)
        def md5Before = compiledClassFile.md5Hash

        then:
        result.task(":tracker:compileDebugJavaWithJavac").outcome == SUCCESS
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        when:
        fileToChange.replace("computeCurrentVelocity(1000", "computeCurrentVelocity(2000")
        result = buildCachedLocation(checkoutDir, agpVersion)

        def md5After = compiledClassFile.md5Hash

        then:
        result.task(":tracker:compileDebugJavaWithJavac").outcome == SUCCESS
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }
        md5After != md5Before

        where:
        agpVersion << TestedVersions.androidGradleBefore9.versions
    }
}

class AndroidSantaTrackerLintSmokeTest extends AndroidSantaTrackerSmokeTest {
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
        )
        // Use --continue so that a deterministic set of tasks runs when some tasks fail
        runner.withArguments(runner.arguments + "--continue")
        def result = runner
            .deprecations(SantaTrackerDeprecations) {
                expectMultiStringNotationDeprecation(agpVersion)
            }
            .buildAndFail()

        then:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }
        result.output.contains("Lint found errors in the project; aborting build.")

        when:
        runner = runnerForLocation(
            checkoutDir, agpVersion,
            "common:lintDebug", "playgames:lintDebug", "doodles-lib:lintDebug"
        )
        result = runner.withArguments(runner.arguments + "--continue")
            .deprecations(SantaTrackerDeprecations) {
                expectMultiStringNotationDeprecationIf(agpVersion, GradleContextualExecuter.isNotConfigCache())
            }
            .buildAndFail()

        then:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }
        result.output.contains("Lint found errors in the project; aborting build.")

        where:
        agpVersion << TestedVersions.androidGradleBefore9.versions
    }
}
