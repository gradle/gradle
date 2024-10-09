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

import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedAttribute;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.describer.AbstractResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException;
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure;
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure;
import org.gradle.util.GradleVersion;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A {@link ResolutionFailureDescriber} that describes a {@link ResolutionFailure} caused by a plugin requiring
 * a newer Gradle version than the one currently running the build.
 *
 * This is determined by assessing the incompatibility of the {@link GradlePluginApiVersion#GRADLE_PLUGIN_API_VERSION_ATTRIBUTE} attribute.
 */
public abstract class NewerGradleNeededByPluginFailureDescriber extends AbstractResolutionFailureDescriber<NoCompatibleVariantsFailure> {
    private static final String GRADLE_VERSION_TOO_OLD_TEMPLATE = "Plugin %s requires at least Gradle %s. This build uses %s.";
    private static final String NEEDS_NEWER_GRADLE_SECTION = "sub:updating-gradle";

    private final GradleVersion currentGradleVersion = GradleVersion.current();

    @Override
    public boolean canDescribeFailure(NoCompatibleVariantsFailure failure) {
        return allCandidatesIncompatibleDueToGradleVersionTooLow(failure);
    }

    @Override
    public AbstractResolutionFailureException describeFailure(NoCompatibleVariantsFailure failure) {
        GradleVersion minGradleApiVersionSupportedByPlugin = findMinGradleVersionSupportedByPlugin(failure.getCandidates());
        String message = buildPluginNeedsNewerGradleVersionFailureMsg(failure.describeRequestTarget(), minGradleApiVersionSupportedByPlugin);
        List<String> resolutions = buildResolutions(suggestUpdateGradle(minGradleApiVersionSupportedByPlugin), suggestDowngradePlugin(failure.describeRequestTarget()));
        return new VariantSelectionByAttributesException(message, failure, resolutions);
    }

    private boolean allCandidatesIncompatibleDueToGradleVersionTooLow(NoCompatibleVariantsFailure failure) {
        boolean requestingPluginApi = failure.getRequestedAttributes().contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE);
        boolean allIncompatibleDueToGradleVersion = failure.getCandidates().stream()
            .allMatch(candidate -> candidate.getIncompatibleAttributes().stream()
                .anyMatch(this::isGradlePluginApiAttribute));
        return requestingPluginApi && allIncompatibleDueToGradleVersion;
    }

    private GradleVersion findMinGradleVersionSupportedByPlugin(List<AssessedCandidate> candidates) {
        return candidates.stream()
            .map(this::findMinGradleVersionSupportedByPlugin)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .min(Comparator.comparing(GradleVersion::getVersion))
            .orElseThrow(IllegalStateException::new);
    }

    private Optional<GradleVersion> findMinGradleVersionSupportedByPlugin(AssessedCandidate candidate) {
        return candidate.getIncompatibleAttributes().stream()
            .filter(this::isGradlePluginApiAttribute)
            .map(apiVersionAttribute -> GradleVersion.version(String.valueOf(apiVersionAttribute.getProvided())))
            .min(Comparator.comparing(GradleVersion::getVersion));
    }

    private boolean isGradlePluginApiAttribute(AssessedAttribute<?> attribute) {
        return attribute.getAttribute().getName().equals(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE.getName());
    }

    private String buildPluginNeedsNewerGradleVersionFailureMsg(String pluginId, GradleVersion minRequiredGradleVersion) {
        return String.format(GRADLE_VERSION_TOO_OLD_TEMPLATE, pluginId, minRequiredGradleVersion.getVersion(), currentGradleVersion);
    }

    private String suggestUpdateGradle(GradleVersion minRequiredGradleVersion) {
        return "Upgrade to at least Gradle " + minRequiredGradleVersion.getVersion() + ". See the instructions at " + getDocumentationRegistry().getDocumentationFor("upgrading_version_8", NEEDS_NEWER_GRADLE_SECTION + ".");
    }

    private String suggestDowngradePlugin(String pluginId) {
        return "Downgrade plugin " + pluginId + " to an older version compatible with " + currentGradleVersion + ".";
    }
}
