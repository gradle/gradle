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
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations implements WithReportDeprecations {
    private static final VersionNumber AGP_VERSION_WITH_FIXED_NEW_WORKERS_API = VersionNumber.parse('4.2')
    private static final VersionNumber AGP_VERSION_WITHOUT_CONVENTION_USAGES = VersionNumber.parse('7.4')
    private static final VersionNumber AGP_VERSION_WITHOUT_CONFIG_UTIL = VersionNumber.parse('8.0.0-rc01')
    private static final VersionNumber AGP_VERSION_WITHOUT_CLIENT_MODULE = VersionNumber.parse('8.2.0-alpha06')

    boolean androidPluginUsesOldWorkerApi(String agpVersion) {
        versionIsLower(agpVersion, AGP_VERSION_WITH_FIXED_NEW_WORKERS_API)
    }

    boolean androidPluginUsesConventions(String agpVersion) {
        versionIsLower(agpVersion, AGP_VERSION_WITHOUT_CONVENTION_USAGES)
    }

    private boolean versionIsLower(String version, VersionNumber threshold) {
        VersionNumber versionNumber = VersionNumber.parse(version)
        versionNumber < threshold
    }

    void expectAndroidWorkerExecutionSubmitDeprecationWarning(String agpVersion) {
        runner.expectLegacyDeprecationWarningIf(androidPluginUsesOldWorkerApi(agpVersion), WORKER_SUBMIT_DEPRECATION)
    }

    void expectProjectConventionDeprecationWarning(String agpVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(androidPluginUsesConventions(agpVersion), PROJECT_CONVENTION_DEPRECATION, action)
    }

    void maybeExpectProjectConventionDeprecationWarning(String agpVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.maybeExpectLegacyDeprecationWarningIf(androidPluginUsesConventions(agpVersion), PROJECT_CONVENTION_DEPRECATION, action)
    }

    void expectAndroidConventionTypeDeprecationWarning(String agpVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(androidPluginUsesConventions(agpVersion), CONVENTION_TYPE_DEPRECATION, action)
    }

    void maybeExpectAndroidConventionTypeDeprecationWarning(String agpVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.maybeExpectLegacyDeprecationWarningIf(androidPluginUsesConventions(agpVersion), CONVENTION_TYPE_DEPRECATION, action)
    }

    void maybeExpectBasePluginConventionDeprecation(String agpVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.maybeExpectLegacyDeprecationWarningIf(androidPluginUsesConventions(agpVersion), BASE_PLUGIN_CONVENTION_DEPRECATION, action)
    }

    void expectBasePluginConventionDeprecation(String agpVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.expectLegacyDeprecationWarningIf(androidPluginUsesConventions(agpVersion), BASE_PLUGIN_CONVENTION_DEPRECATION, action)
    }

    void expectConfigUtilDeprecationWarning(String agpVersion) {
        runner.expectLegacyDeprecationWarningIf(
            versionIsLower(agpVersion, AGP_VERSION_WITHOUT_CONFIG_UTIL),
            "The org.gradle.util.ConfigureUtil type has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#org_gradle_util_reports_deprecations",
        )
    }

    private static getBuildIdentifierIsCurrentBuildDeprecationMessage() {
        return "The BuildIdentifier.isCurrentBuild() method has been deprecated. " +
            "This is scheduled to be removed in Gradle 9.0. " +
            "Use getBuildPath() to get a unique identifier for the build. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
    }

    void maybeExpectBuildIdentifierIsCurrentBuildDeprecation(String agpVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        runner.maybeExpectLegacyDeprecationWarningIf(
            agpVersionNumber < VersionNumber.parse("8.0.0-rc01"),
            getBuildIdentifierIsCurrentBuildDeprecationMessage(),
            action
        )
    }

    void expectBuildIdentifierIsCurrentBuildDeprecation(String agpVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        expectBuildIdentifierIsCurrentBuildDeprecation(agpVersion, "8.0.0", action)
    }

    void expectBuildIdentifierIsCurrentBuildDeprecation(String agpVersion, String fixedVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        runner.expectLegacyDeprecationWarningIf(
            agpVersionNumber.baseVersion < VersionNumber.parse(fixedVersion),
            getBuildIdentifierIsCurrentBuildDeprecationMessage(),
            action
        )
    }

    void expectBuildIdentifierNameDeprecation(String agpVersion) {
        VersionNumber agpVersionNumber = VersionNumber.parse(agpVersion)
        runner.expectLegacyDeprecationWarningIf(
            agpVersionNumber.baseVersion < VersionNumber.parse("8.2.0"),
            "The BuildIdentifier.getName() method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Use getBuildPath() to get a unique identifier for the build. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
        )
    }

    void maybeExpectOrgGradleUtilGUtilDeprecation(String agpVersion, @DelegatesTo(SmokeTestGradleRunner.DeprecationOptions) Closure<?> action = null) {
        runner.maybeExpectLegacyDeprecationWarningIf(
            VersionNumber.parse(agpVersion) < VersionNumber.parse("7.5"),
            GUTIL_DEPRECATION,
            action
        )
    }

    void expectAndroidBasePluginExtensionArchivesBaseNameDeprecation(VersionNumber versionNumber) {
        runner.expectLegacyDeprecationWarningIf(
            versionNumber < VersionNumber.parse('7.4.0'),
            "The BasePluginExtension.archivesBaseName property has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Please use the archivesName property instead. " +
                "For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.plugins.BasePluginExtension.html#org.gradle.api.plugins.BasePluginExtension:archivesName in the Gradle documentation."
        )
    }

}
