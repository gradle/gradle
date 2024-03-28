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
trait WithAndroidDeprecations {
    private static final VersionNumber AGP_VERSION_WITHOUT_CONFIGURATION_MUTATION = VersionNumber.parse('8.3.0-alpha11')

    private boolean versionIsLower(String version, VersionNumber threshold) {
        VersionNumber versionNumber = VersionNumber.parse(version)
        versionNumber < threshold
    }

    void expectConfigurationMutationDeprecationWarnings(String agpVersion, List<String> confs) {
        confs.each { conf ->
            runner.expectLegacyDeprecationWarningIf(
                versionIsLower(agpVersion, AGP_VERSION_WITHOUT_CONFIGURATION_MUTATION),
                "Mutating the dependencies of configuration '${conf}' after it has been resolved or consumed. " +
                    "This behavior has been deprecated. " +
                    "This will fail with an error in Gradle 9.0. " +
                    "After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#mutate_configuration_after_locking"
            )
        }
    }

    void maybeExpectConfigurationMutationDeprecationWarnings(String agpVersion, List<String> confs) {
        confs.each { conf ->
            runner.maybeExpectLegacyDeprecationWarningIf(
                versionIsLower(agpVersion, AGP_VERSION_WITHOUT_CONFIGURATION_MUTATION),
                "Mutating the dependencies of configuration '${conf}' after it has been resolved or consumed. " +
                    "This behavior has been deprecated. " +
                    "This will fail with an error in Gradle 9.0. " +
                    "After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#mutate_configuration_after_locking"
            )
        }
    }

    void expectFilteredFileCollectionDeprecationWarning() {
        // This warning is emitted by the Kotlin Kapt plugin
        runner.expectLegacyDeprecationWarning(
            "The Configuration.fileCollection(Spec) method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Use Configuration.getIncoming().artifactView(Action) with a componentFilter instead. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecate_filtered_configuration_file_and_filecollection_methods"
        )
    }

}
