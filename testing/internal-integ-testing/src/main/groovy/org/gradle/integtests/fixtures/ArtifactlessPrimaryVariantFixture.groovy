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
 * See {@code org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector.SelectionWarningHelper#nagIfConsideringPrimaryChangesResult}
 * for the production emitter. The expected message string here must stay in sync with
 * the {@code DeprecationLogger.deprecateBehaviour(...).startingWithGradle11(...)} call there.
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
     * Expect the deprecation emitted when considering the primary variant without artifacts would change the result, had the feature
     * preview been enabled: it, or a transformation of it, would have been selected, or it would have made the match ambiguous.
     *
     * @param producer display name of the producer, e.g. "project ':a'"
     * @param variant display name of the ignored variant, e.g. "configuration ':a:compile'"
     * @param attributesNotOnPrimary the attributes suggested to make the request more precise, as {@code name=value}, in the order listed
     */
    void expectArtifactlessPrimaryVariantDeprecation(String producer, String variant, List<String> attributesNotOnPrimary = []) {
        def moreAttributesInRequest = attributesNotOnPrimary.empty
            ? "Request attributes that only the intended variant matches"
            : "Request one of these attributes: ${attributesNotOnPrimary.join(', ')}"
        def advice = "${moreAttributesInRequest}; add attributes to that variant so that it no longer matches; " +
            "or enable the 'CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS' feature preview."
        executer.expectDocumentedDeprecationWarning(
            "Selecting artifacts from ${producer} ignored variant '${variant}' because it has no artifacts. " +
                "This behavior has been deprecated. " +
                "Starting with Gradle 11, primary variants without artifacts will be selected when they are the best match. " +
                "${advice} " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#variants_with_no_artifacts"
        )
    }
}
