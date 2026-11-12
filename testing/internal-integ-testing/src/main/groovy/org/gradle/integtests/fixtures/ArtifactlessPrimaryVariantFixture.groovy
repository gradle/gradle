/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType

/**
 * Apply this trait to tests that resolve artifacts from a configuration whose primary variant
 * has no artifacts but which declares secondary variants.
 * <p>
 * See {@code org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector#nagAboutIgnoredVariant}
 * for the production emitter. The expected message string here must stay in sync with
 * the {@code DeprecationLogger.deprecateBehaviour(...).startingWithGradle10(...)} call there.
 */
@SelfType(AbstractIntegrationSpec)
trait ArtifactlessPrimaryVariantFixture {
    /**
     * Enables the {@code CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS} feature preview, which makes such a
     * primary variant eligible for selection, so that it is selected when it is the best match.
     */
    void considerArtifactlessPrimaryVariants(boolean enabled = true) {
        if (enabled) {
            settingsFile << 'enableFeaturePreview("CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS")\n'
        }
    }

    /**
     * Expect the deprecation emitted when the primary variant without artifacts would have been selected,
     * had the feature preview been enabled.
     *
     * @param producer display name of the producer, e.g. "project ':a'"
     * @param variant display name of the ignored variant, e.g. "configuration ':a:compile'"
     */
    void expectArtifactlessPrimaryVariantDeprecation(String producer, String variant) {
        executer.expectDocumentedDeprecationWarning(
            "Selecting artifacts from ${producer} ignored variant '${variant}' because it has no artifacts. " +
                "This behavior has been deprecated. " +
                "Starting with Gradle 10, primary variants without artifacts will be selected when they are the best match. " +
                "Add attributes to the request so that it only matches the intended variant, or enable the 'CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS' feature preview to opt into the new behavior. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#variants_with_no_artifacts"
        )
    }
}
