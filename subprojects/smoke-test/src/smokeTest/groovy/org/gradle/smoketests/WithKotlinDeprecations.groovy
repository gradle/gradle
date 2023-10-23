/*
 * Copyright 2022 the original author or authors.
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

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.smoketests.KotlinRunnerFactory.ParallelTasksInProject
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

import static org.gradle.api.internal.DocumentationRegistry.RECOMMENDATION

@SelfType(BaseDeprecations)
trait WithKotlinDeprecations extends WithReportDeprecations {
    private static final VersionNumber KOTLIN_VERSION_USING_NEW_WORKERS_API = VersionNumber.parse('1.5.0')
    private static final VersionNumber KOTLIN_VERSION_WITH_OLD_WORKERS_API_REMOVED = VersionNumber.parse('1.6.10')

    private static final String ABSTRACT_COMPILE_DESTINATION_DIR_DEPRECATION = "The AbstractCompile.destinationDir property has been deprecated. " +
        "This is scheduled to be removed in Gradle 9.0. " +
        "Please use the destinationDirectory property instead. " +
        "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#compile_task_wiring"

    private static final String ARCHIVE_NAME_DEPRECATION = "The AbstractArchiveTask.archiveName property has been deprecated. " +
        "This is scheduled to be removed in Gradle 8.0. Please use the archiveFileName property instead. " +
        String.format(RECOMMENDATION, "information", DOCUMENTATION_REGISTRY.getDslRefForProperty("org.gradle.api.tasks.bundling.AbstractArchiveTask", "archiveName"))

    boolean kotlinPluginUsesOldWorkerApi(VersionNumber versionNumber) {
        versionNumber >= KOTLIN_VERSION_USING_NEW_WORKERS_API && versionNumber <= KOTLIN_VERSION_WITH_OLD_WORKERS_API_REMOVED
    }

    void expectKotlinCompileDestinationDirPropertyDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber >= VersionNumber.parse('1.5.20') && versionNumber <= VersionNumber.parse('1.6.21'),
            ABSTRACT_COMPILE_DESTINATION_DIR_DEPRECATION,
            action
        )
    }

    void maybeExpectKotlinCompileDestinationDirPropertyDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.maybeExpectLegacyDeprecationWarningIf(
            versionNumber >= VersionNumber.parse('1.5.20') && versionNumber <= VersionNumber.parse('1.6.21'),
            ABSTRACT_COMPILE_DESTINATION_DIR_DEPRECATION,
            action
        )
    }

    void expectKotlinArchiveNameDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(versionNumber.minor == 3, ARCHIVE_NAME_DEPRECATION)
    }

    void expectKotlin2JsPluginDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(versionNumber >= VersionNumber.parse('1.4.0'),
            "The `kotlin2js` Gradle plugin has been deprecated."
        )
    }

    void expectKotlinParallelTasksDeprecation(VersionNumber versionNumber, ParallelTasksInProject parallelTasksInProject) {
        runner.expectLegacyDeprecationWarningIf(
            parallelTasksInProject.isPropertyPresent() && kotlinPluginUsesOldWorkerApi(versionNumber),
            "Project property 'kotlin.parallel.tasks.in.project' is deprecated."
        )
    }

    void expectAbstractCompileDestinationDirDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.0"),
            "The AbstractCompile.destinationDir property has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Please use the destinationDirectory property instead. " +
                "Consult the upgrading guide for further information: ${DOCUMENTATION_REGISTRY.getDocumentationFor("upgrading_version_7", "compile_task_wiring")}"
        )
    }

    void expectOrgGradleUtilWrapUtilDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.20"),
            "The org.gradle.util.WrapUtil type has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "${DOCUMENTATION_REGISTRY.getDocumentationFor("upgrading_version_7", "org_gradle_util_reports_deprecations")}",
            action
        )
    }

    void expectTestReportReportOnDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber.baseVersion < VersionNumber.parse("1.8.20"),
            "The TestReport.reportOn(Object...) method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Please use the testResults method instead. " +
                String.format(RECOMMENDATION, "information", DOCUMENTATION_REGISTRY.getDslRefForProperty("org.gradle.api.tasks.testing.TestReport", "testResults"))
        )
    }

    void expectTestReportDestinationDirOnDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber.baseVersion < VersionNumber.parse("1.8.20"),
            "The TestReport.destinationDir property has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Please use the destinationDirectory property instead. " +
                String.format(RECOMMENDATION, "information", DOCUMENTATION_REGISTRY.getDslRefForProperty("org.gradle.api.tasks.testing.TestReport", "destinationDir"))
        )
    }

    void expectProjectConventionDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.22"),
            PROJECT_CONVENTION_DEPRECATION,
            action
        )
    }

    void expectBasePluginConventionDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.0"),
            BASE_PLUGIN_CONVENTION_DEPRECATION,
            action
        )
    }

    void expectAndroidBasePluginConventionDeprecation(VersionNumber kotlinVersionNumber, VersionNumber agpVersionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            agpVersionNumber < VersionNumber.parse("7.4.0") || kotlinVersionNumber < VersionNumber.parse("1.7.0"),
            BASE_PLUGIN_CONVENTION_DEPRECATION,
            action
        )
    }

    void expectJavaPluginConventionDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.22"),
            JAVA_PLUGIN_CONVENTION_DEPRECATION,
            action
        )
    }

    void expectAndroidProjectConventionDeprecation(VersionNumber kotlinVersionNumber, VersionNumber agpVersionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            agpVersionNumber < VersionNumber.parse("7.4.0") || (agpVersionNumber >= VersionNumber.parse("7.4.0") && kotlinVersionNumber < VersionNumber.parse("1.7.0")),
            PROJECT_CONVENTION_DEPRECATION,
            action
        )
    }

    void expectConventionTypeDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            shouldExpectConventionTypeDeprecation(versionNumber),
            CONVENTION_TYPE_DEPRECATION,
            action
        )
    }

    void maybeExpectConventionTypeDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.maybeExpectLegacyDeprecationWarningIf(
            shouldExpectConventionTypeDeprecation(versionNumber),
            CONVENTION_TYPE_DEPRECATION,
            action
        )
    }

    private boolean shouldExpectConventionTypeDeprecation(VersionNumber versionNumber) {
        versionNumber < VersionNumber.parse("1.7.22")
    }

    void expectAndroidConventionTypeDeprecation(VersionNumber kotlinVersionNumber, VersionNumber agpVersionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            agpVersionNumber < VersionNumber.parse("7.4.0") || (agpVersionNumber >= VersionNumber.parse("7.4.0") && kotlinVersionNumber < VersionNumber.parse("1.7.22")),
            CONVENTION_TYPE_DEPRECATION,
            action
        )
    }

    void expectConfigureUtilDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.22"),
            "The org.gradle.util.ConfigureUtil type has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                DOCUMENTATION_REGISTRY.getDocumentationFor("upgrading_version_8", "org_gradle_util_reports_deprecations")
        )
    }

    void expectForUseAtConfigurationTimeDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.8.0"),
            FOR_USE_AT_CONFIGURATION_TIME_DEPRECATION,
            action
        )
    }

    void maybeExpectForUseAtConfigurationTimeDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.maybeExpectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.8.0"),
            FOR_USE_AT_CONFIGURATION_TIME_DEPRECATION,
            action
        )
    }

    void expectBuildIdentifierNameDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(versionNumber >= VersionNumber.parse("1.8.20") && versionNumber.baseVersion < VersionNumber.parse('1.9.20'),
            "The BuildIdentifier.getName() method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Use getBuildPath() to get a unique identifier for the build. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation",
            action
        )
    }

    void expectVersionSpecificMultiplatformDeprecations(VersionNumber versionNumber, List<ProjectTypes> projectTypes) {
        expectKotlinParallelTasksDeprecation(versionNumber, ParallelTasksInProject.OMIT)
        if (projectTypes.contains(ProjectTypes.JS)) {
            expectAbstractCompileDestinationDirDeprecation(versionNumber)
        }
        expectOrgGradleUtilWrapUtilDeprecation(versionNumber) {
            causes = [null, "plugin 'org.jetbrains.kotlin.multiplatform'"]
        }
        expectConventionTypeDeprecation(versionNumber) {
            cause = "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
        }
        expectJavaPluginConventionDeprecation(versionNumber) {
            cause = "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
        }
        expectProjectConventionDeprecation(versionNumber) {
            cause = "plugin class 'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin'"
        }
        expectTestReportReportOnDeprecation(versionNumber)
        expectTestReportDestinationDirOnDeprecation(versionNumber)
        expectBuildIdentifierNameDeprecation(versionNumber) {
            cause = "plugin 'org.jetbrains.kotlin.multiplatform'"
        }
        if (GradleContextualExecuter.configCache || versionNumber == VersionNumber.parse("1.7.22")) {
            maybeExpectForUseAtConfigurationTimeDeprecation(versionNumber) {
                causes = [null, "plugin 'org.jetbrains.kotlin.multiplatform'"]
            }
        }
    }

    void expectKotlinBasePluginExtensionArchivesBaseNameDeprecation(VersionNumber versionNumber, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse('1.7.0'),
            "The BasePluginExtension.archivesBaseName property has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Please use the archivesName property instead. " +
                "For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.plugins.BasePluginExtension.html#org.gradle.api.plugins.BasePluginExtension:archivesName in the Gradle documentation.",
            action
        )
    }

    protected static enum ProjectTypes {
        JVM,
        JS;
    }
}
