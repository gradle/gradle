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

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations {

    void expectProjectDependencyNotationDeprecationIf(boolean condition) {
        if (condition) {
            expectProjectDependencyNotationDeprecation()
        }
    }

    void expectProjectDependencyNotationDeprecation() {
        runner.expectDeprecationWarning(
            "Using a Project object as a dependency notation has been deprecated. This will fail with an error in Gradle 10. Please use the project(String) method on DependencyHandler or the createProjectDependency(String) method on DependencyFactory instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_project_notation",
            "https://issuetracker.google.com/issues/495889752"
        )
    }

    void expectSetVisibleDeprecationIf(boolean condition) {
        if (condition) {
            expectSetVisibleDeprecation()
        }
    }

    void expectSetVisibleDeprecation() {
        runner.expectDeprecationWarning(
            "The Configuration.setVisible(boolean) method has been deprecated. This is scheduled to be removed in Gradle 11. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate-visible-property",
            "https://issuetracker.google.com/issues/540783786"
        )
    }

    void expectArtifactlessPrimaryVariantDeprecationsIf(boolean condition, String project, List<String> configurations) {
        if (condition) {
            expectArtifactlessPrimaryVariantDeprecations(project, configurations)
        }
    }

    /**
     * Expect the deprecation emitted for each of the given configurations of the project, whose primary variant has no artifacts,
     * since it would have been selected had the {@code CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS} feature preview been enabled.
     *
     * @param attributesNotOnPrimary the attributes the deprecation suggests requesting, as {@code name=value}, in the order listed
     */
    void expectArtifactlessPrimaryVariantDeprecations(String project, List<String> configurations, List<String> attributesNotOnPrimary = []) {
        configurations.each { configuration ->
            expectArtifactlessPrimaryVariantDeprecation("project '${project}'", "configuration '${project}:${configuration}'", attributesNotOnPrimary)
        }
    }

    /**
     * Expect the deprecation emitted when selecting artifacts from the producer ignores its primary variant, which has no artifacts.
     *
     * @param producer display name of the producer, e.g. "project ':library'"
     * @param variant display name of the ignored variant, e.g. "configuration ':library:debugRuntimeElements'"
     * @param attributesNotOnPrimary the attributes the deprecation suggests requesting, as {@code name=value}, in the order listed
     */
    void expectArtifactlessPrimaryVariantDeprecation(String producer, String variant, List<String> attributesNotOnPrimary = []) {
        def moreAttributesInRequest = attributesNotOnPrimary.empty
            ? "Request attributes that only the intended variant matches"
            : "Request one of these attributes: ${attributesNotOnPrimary.join(', ')}"
        runner.expectDeprecationWarning(
            "Selecting artifacts from ${producer} ignored variant '${variant}' because it has no artifacts. " +
                "This behavior has been deprecated. " +
                "Starting with Gradle 11, primary variants without artifacts will be selected when they are the best match. " +
                "${moreAttributesInRequest}; " +
                "add attributes to that variant so that it no longer matches; " +
                "or enable the 'CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS' feature preview. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#variants_with_no_artifacts",
            "https://github.com/gradle/gradle/issues/29630"
        )
    }

}
