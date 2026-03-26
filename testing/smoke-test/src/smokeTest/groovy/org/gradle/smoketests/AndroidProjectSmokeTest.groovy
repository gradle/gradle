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

abstract class AndroidProjectSmokeTest extends AbstractAndroidProjectSmokeTest {
}

class AndroidProjectDeprecationSmokeTest extends AndroidProjectSmokeTest {
    def "check deprecation warnings produced by building android project (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfAndroidProject(checkoutDir)

        when:
        def result = buildLocation(checkoutDir, agpVersion)

        then:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        where:
        agpVersion << TestedVersions.androidGradle9AndAbove.versions
    }
}

class AndroidProjectIncrementalCompilationSmokeTest extends AndroidProjectSmokeTest {
    def "incremental compilation works for android project (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfAndroidProject(checkoutDir)

        and:
        def fileToChange = checkoutDir.file("core/ui/src/main/kotlin/com/google/samples/apps/nowinandroid/core/ui/NewsFeed.kt")

        when:
        def result = buildLocation(checkoutDir, agpVersion)

        then:
        result.task(":core:ui:compileProdDebugKotlin").outcome == SUCCESS
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        when:
        fileToChange.replace("StaggeredGridCells.Adaptive(300.dp)", "StaggeredGridCells.Adaptive(600.dp)")
        result = buildCachedLocation(checkoutDir, agpVersion)

        then:
        result.task(":core:ui:compileProdDebugKotlin").outcome == SUCCESS
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }

        where:
        agpVersion << TestedVersions.androidGradle9AndAbove.versions
    }
}

class AndroidProjectLintSmokeTest extends AndroidProjectSmokeTest {
    def "can lint android project (agp=#agpVersion)"() {
        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfAndroidProject(checkoutDir)

        when:
        def runner = runnerForLocation(
            checkoutDir, agpVersion, "app:lint", "-Dandroid.lintWarningsAsErrors=true"
        )
        // Use --continue so that a deterministic set of tasks runs when some tasks fail
        runner.withArguments(runner.arguments + "--continue")
        def result = runner
            .deprecations(AndroidProjectDeprecations) {
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
            checkoutDir, agpVersion, "app:lint", "-Dandroid.lintWarningsAsErrors=true"
        )
        result = runner.withArguments(runner.arguments + "--continue")
                .deprecations(AndroidProjectDeprecations) {}
                .buildAndFail()

        then:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }
        result.output.contains("Lint found errors in the project; aborting build.")

        where:
        agpVersion << TestedVersions.androidGradle9AndAbove.versions
    }
}
