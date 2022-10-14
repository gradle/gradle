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
trait WithKotlinDeprecations extends WithReportDeprecations {
    private static final VersionNumber KOTLIN_VERSION_USING_NEW_WORKERS_API = VersionNumber.parse('1.5.0')
    private static final VersionNumber KOTLIN_VERSION_USING_INPUT_CHANGES_API = VersionNumber.parse('1.6.0')

    private static final String ABSTRACT_COMPILE_DESTINATION_DIR_DEPRECATION = "The AbstractCompile.destinationDir property has been deprecated. " +
        "This is scheduled to be removed in Gradle 9.0. " +
        "Please use the destinationDirectory property instead. " +
        "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#compile_task_wiring"

    void expectKotlinWorkerSubmitDeprecation(boolean workers, String kotlinPluginVersion) {
        runner.expectLegacyDeprecationWarningIf(workers && kotlinPluginUsesOldWorkerApi(kotlinPluginVersion), WORKER_SUBMIT_DEPRECATION)
    }

    boolean kotlinPluginUsesOldWorkerApi(String kotlinPluginVersion) {
        VersionNumber kotlinVersionNumber = VersionNumber.parse(kotlinPluginVersion)
        kotlinVersionNumber < KOTLIN_VERSION_USING_NEW_WORKERS_API
    }

    void expectKotlinCompileDestinationDirPropertyDeprecation(String version) {
        VersionNumber versionNumber = VersionNumber.parse(version)
        runner.expectLegacyDeprecationWarningIf(
            versionNumber >= VersionNumber.parse('1.5.20') && versionNumber <= VersionNumber.parse('1.6.21'),
            ABSTRACT_COMPILE_DESTINATION_DIR_DEPRECATION
        )
    }
}
