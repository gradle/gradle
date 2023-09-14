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

    void expectKotlinCompileDestinationDirPropertyDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber >= VersionNumber.parse('1.5.20') && versionNumber <= VersionNumber.parse('1.6.21'),
            ABSTRACT_COMPILE_DESTINATION_DIR_DEPRECATION
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

    void expectOrgGradleUtilWrapUtilDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.20"),
            "The org.gradle.util.WrapUtil type has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "${DOCUMENTATION_REGISTRY.getDocumentationFor("upgrading_version_7", "org_gradle_util_reports_deprecations")}"
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

    void expectProjectConventionDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.22"),
            PROJECT_CONVENTION_DEPRECATION
        )
    }

    void expectBasePluginConventionDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.0"),
            BASE_PLUGIN_CONVENTION_DEPRECATION
        )
    }

    void expectBasePluginConventionDeprecation(VersionNumber kotlinVersionNumber, VersionNumber agpVersionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            agpVersionNumber < VersionNumber.parse("7.4.0") || kotlinVersionNumber < VersionNumber.parse("1.7.0"),
            BASE_PLUGIN_CONVENTION_DEPRECATION
        )
    }

    void expectJavaPluginConventionDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.7.22"),
            JAVA_PLUGIN_CONVENTION_DEPRECATION
        )
    }

    void expectProjectConventionDeprecation(VersionNumber kotlinVersionNumber, VersionNumber agpVersionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            agpVersionNumber < VersionNumber.parse("7.4.0") || (agpVersionNumber >= VersionNumber.parse("7.4.0") && kotlinVersionNumber < VersionNumber.parse("1.7.0")),
            PROJECT_CONVENTION_DEPRECATION
        )
    }

    void expectConventionTypeDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            shouldExpectConventionTypeDeprecation(versionNumber),
            CONVENTION_TYPE_DEPRECATION
        )
    }

    void maybeExpectConventionTypeDeprecation(VersionNumber versionNumber) {
        runner.maybeExpectLegacyDeprecationWarningIf(
            shouldExpectConventionTypeDeprecation(versionNumber),
            CONVENTION_TYPE_DEPRECATION
        )
    }

    private boolean shouldExpectConventionTypeDeprecation(VersionNumber versionNumber) {
        versionNumber < VersionNumber.parse("1.7.22")
    }

    void expectConventionTypeDeprecation(VersionNumber kotlinVersionNumber, VersionNumber agpVersionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            agpVersionNumber < VersionNumber.parse("7.4.0") || (agpVersionNumber >= VersionNumber.parse("7.4.0") && kotlinVersionNumber < VersionNumber.parse("1.7.22")),
            CONVENTION_TYPE_DEPRECATION
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

    void expectForUseAtConfigurationTimeDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse("1.8.0"),
            FOR_USE_AT_CONFIGURATION_TIME_DEPRECATION
        )
    }

    void expectVersionSpecificMultiplatformDeprecations(VersionNumber versionNumber, List<ProjectTypes> projectTypes) {
        expectKotlinParallelTasksDeprecation(versionNumber, ParallelTasksInProject.OMIT)
        if (projectTypes.contains(ProjectTypes.JS)) {
            expectAbstractCompileDestinationDirDeprecation(versionNumber)
        }
        expectOrgGradleUtilWrapUtilDeprecation(versionNumber)
        expectConventionTypeDeprecation(versionNumber)
        expectJavaPluginConventionDeprecation(versionNumber)
        expectProjectConventionDeprecation(versionNumber)
        expectTestReportReportOnDeprecation(versionNumber)
        expectTestReportDestinationDirOnDeprecation(versionNumber)
        if (GradleContextualExecuter.configCache || versionNumber == VersionNumber.parse("1.7.22")) {
            expectForUseAtConfigurationTimeDeprecation(versionNumber)
        }
    }

    protected static enum ProjectTypes {
        JVM,
        JS;
    }
}
