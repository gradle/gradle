/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.component.ResolutionFailure;
import org.gradle.internal.component.ResolutionFailure.ResolutionFailureType;
import org.gradle.internal.component.ResolutionFailureDescriber;
import org.gradle.internal.component.NoMatchingGraphVariantsException;
import org.gradle.internal.component.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.util.GradleVersion;

import java.util.List;
import java.util.Optional;

public class GradlePluginVariantsResolutionFailureDescriber implements ResolutionFailureDescriber<NoMatchingGraphVariantsException> {
    private static final String GRADLE_VERSION_TOO_OLD_TEMPLATE = "Plugin %s requires at least Gradle %s (this build used %s).";
    private static final String NEEDS_NEWER_GRADLE_SECTION = "sub:updating-gradle";

    private final DocumentationRegistry documentationRegistry;
    private final GradleVersion currentGradleVersion;

    protected GradlePluginVariantsResolutionFailureDescriber(DocumentationRegistry documentationRegistry, GradleVersion currentGradleVersion) {
        this.documentationRegistry = documentationRegistry;
        this.currentGradleVersion = currentGradleVersion;
    }

    @Override
    public boolean canDescribeFailure(ResolutionFailure failure) {
        return failure.getType() == ResolutionFailureType.NO_MATCHING_VARIANTS
            && isPluginRequestUsingApiVersionAttribute(failure.getRequestedAttributes())
            && !failure.allCandidatesAreIncompatible();
    }

    @Override
    public NoMatchingGraphVariantsException describeFailure(ResolutionFailure failure) {
        Optional<String> minRequiredGradleVersion = findHigherRequiredVersionOfGradle(failure.getCandidates());
        if (minRequiredGradleVersion.isPresent()) {
            String message = buildPluginNeedsNewerGradleVersionFailureMsg(failure.getRequestedName(), minRequiredGradleVersion.get());
            NoMatchingGraphVariantsException result = new NoMatchingGraphVariantsException(message);
            suggestUpdateGradle(result, minRequiredGradleVersion.get());
            suggestDowngradePlugin(result, failure.getRequestedName());
            return result;
        } else {
            throw new IllegalStateException("Expected to find a higher required version of Gradle.");
        }
    }

    private boolean isPluginRequestUsingApiVersionAttribute(AttributeContainer requestedAttributes) {
        return requestedAttributes.contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE);
    }

    private Optional<String> findHigherRequiredVersionOfGradle(List<AssessedCandidate> candidates) {
        return findIncompatibleRequiredMinimumVersionOfGradle(candidates);
    }

    private Optional<String> findIncompatibleRequiredMinimumVersionOfGradle(List<AssessedCandidate> candidates) {
        String requiredMinimumVersionOfGradle = null;

        for (AssessedCandidate candidate : candidates) {
            Optional<GradleVersion> providedVersion = candidate.getIncompatibleAttributes().stream()
                .filter(incompatibleAttribute -> incompatibleAttribute.getAttribute().getName().equals(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE.getName()))
                .map(apiVersionAttribute -> GradleVersion.version(String.valueOf(apiVersionAttribute.getProvided())))
                .findFirst();

            if (providedVersion.isPresent()) {
                GradleVersion version = providedVersion.get();
                if (requiredMinimumVersionOfGradle == null || version.compareTo(GradleVersion.version(requiredMinimumVersionOfGradle)) > 0) {
                    requiredMinimumVersionOfGradle = version.getVersion();
                }
            }
        }

        return Optional.ofNullable(requiredMinimumVersionOfGradle);
    }

    private String buildPluginNeedsNewerGradleVersionFailureMsg(String pluginId, String minRequiredGradleVersion) {
        return String.format(GRADLE_VERSION_TOO_OLD_TEMPLATE, pluginId, minRequiredGradleVersion, currentGradleVersion);
    }

    private void suggestUpdateGradle(NoMatchingGraphVariantsException result, String minRequiredGradleVersion) {
        result.addResolution("Upgrade Gradle to at least version " + minRequiredGradleVersion + ". See the instructions at " + documentationRegistry.getDocumentationFor("upgrading_version_8", NEEDS_NEWER_GRADLE_SECTION + "."));
    }

    private void suggestDowngradePlugin(NoMatchingGraphVariantsException result, String pluginId) {
        result.addResolution("Downgrade plugin " + pluginId + " to an older version compatible with " + currentGradleVersion + ".");
    }
}
