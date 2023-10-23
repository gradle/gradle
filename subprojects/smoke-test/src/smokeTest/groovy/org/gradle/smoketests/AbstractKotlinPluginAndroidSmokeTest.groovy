/*
 * Copyright 2023 the original author or authors.
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

import com.gradle.enterprise.testing.annotations.LocalOnly
import org.gradle.integtests.fixtures.android.AndroidHome
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.util.internal.VersionNumber

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Abstract base class for testing the Kotlin plugin with Android projects.
 *
 * This test exists to avoid duplicating test logic for Groovy and Kotlin DSLs.
 */
@LocalOnly(because = "Needs Android environment")
abstract class AbstractKotlinPluginAndroidSmokeTest extends AbstractSmokeTest implements KotlinRunnerFactory {
    abstract String getSampleName()
    abstract GradleDsl getDSL()

    def "kotlin android on android-kotlin-example using #dsl DSL (kotlin=#kotlinPluginVersion, agp=#androidPluginVersion, workers=#parallelTasksInProject)"(String kotlinPluginVersion, String androidPluginVersion, ParallelTasksInProject parallelTasksInProject) {
        given:
        AndroidHome.assertIsSet()
        AGP_VERSIONS.assumeAgpSupportsCurrentJavaVersionAndKotlinVersion(androidPluginVersion, kotlinPluginVersion)
        useSample(getSampleName())

        def buildFileName = getDSL().fileNameFor("build")
        [buildFileName, "app/$buildFileName"].each { sampleBuildFileName ->
            replaceVariablesInFile(
                    file(sampleBuildFileName),
                    kotlinVersion: kotlinPluginVersion,
                    androidPluginVersion: androidPluginVersion,
                    androidBuildToolsVersion: TestedVersions.androidTools)
        }
        def kotlinPluginVersionNumber = VersionNumber.parse(kotlinPluginVersion)
        def androidPluginVersionNumber = VersionNumber.parse(androidPluginVersion)

        when:
        def runner = createRunner(parallelTasksInProject, kotlinPluginVersionNumber, androidPluginVersionNumber, 'clean', ":app:testDebugUnitTestCoverage")
        def result = useAgpVersion(androidPluginVersion, runner)
                .deprecations(KotlinAndroidDeprecations) {
                    expectKotlinConfigurationAsDependencyDeprecation(kotlinPluginVersionNumber)
                    expectAndroidOrKotlinWorkerSubmitDeprecation(androidPluginVersionNumber, parallelTasksInProject, kotlinPluginVersionNumber)
                    expectReportDestinationPropertyDeprecation(androidPluginVersion) {
                        cause = "plugin 'com.android.internal.application'"
                    }
                    expectKotlinCompileDestinationDirPropertyDeprecation(kotlinPluginVersionNumber)
                    if (GradleContextualExecuter.configCache || kotlinPluginVersionNumber >= VersionNumber.parse("1.8.0")) {
                        expectBuildIdentifierIsCurrentBuildDeprecation(androidPluginVersion) {
                            cause = "plugin 'com.android.internal.application'"
                        }
                    }
                    maybeExpectOrgGradleUtilGUtilDeprecation(androidPluginVersion) {
                        cause = "plugin 'com.android.internal.application'"
                    }
                    if (GradleContextualExecuter.configCache) {
                        expectForUseAtConfigurationTimeDeprecation(kotlinPluginVersionNumber)
                    }
                    expectBasePluginExtensionArchivesBaseNameDeprecation(kotlinPluginVersionNumber, androidPluginVersionNumber) {
                        causes = [
                            null,
                            "plugin 'com.android.internal.application'"
                        ]
                    }
                }.build()

        then:
        result.task(':app:testDebugUnitTestCoverage').outcome == SUCCESS

        where:
// To run a specific combination, set the values here, uncomment the following four lines
//  and comment out the lines coming after
//        kotlinPluginVersion = TestedVersions.kotlin.versions.last()
//        androidPluginVersion = TestedVersions.androidGradle.versions.last()
//        parallelTasksInProject = ParallelTasksInProject.FALSE

        [kotlinPluginVersion, androidPluginVersion, parallelTasksInProject] << [
                TestedVersions.kotlin.versions,
                TestedVersions.androidGradle.versions,
                ParallelTasksInProject.values(),
        ].combinations()

        dsl = getDSL().name()
    }
}
