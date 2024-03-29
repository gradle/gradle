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

package org.gradle.api.plugins.internal;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.describer.AbstractResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionException;
import org.gradle.internal.component.resolution.failure.type.IncompatibleGraphVariantFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleResolutionFailure;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

// TODO: many of the types in org.gradle.internal.component.resolution.failure imported above would have to be publicized to allow the Kotlin plugin to implement this
/**
 * Attempt at implementing a failure describer based on <a href="https://github.com/dsavvinov/kmp-var-failure-showcase">the idea here</a>.
 */
public abstract class KMPFailureDescriber extends AbstractResolutionFailureDescriber<IncompatibleGraphVariantFailure> {
    // TODO: Grab this attribute from the KotlinPlatformType in the Kotlin Platform
    private final static Attribute<String> PLATFORM_TYPE_ATTRIBUTE = Attribute.of("org.jetbrains.kotlin.platform.type", String.class);

    @Override
    public boolean canDescribeFailure(IncompatibleGraphVariantFailure failure) {
        return isIncompatibleKMPRequestDueToUnavailablePlatformType(failure);
    }

    private boolean isIncompatibleKMPRequestDueToUnavailablePlatformType(IncompatibleResolutionFailure failure) {
        List<ResolutionCandidateAssessor.AssessedCandidate> libraryCandidates = failure.getCandidates().stream()
            .filter(this::isLibraryCandidate)
            .collect(Collectors.toList());
        return !libraryCandidates.isEmpty() && libraryCandidates.stream().allMatch(this::isPlatformTypeAttributeIncompatible);
    }

    // TODO: this seems a common check, shared by AbstractJVMVersionTooNewFailureDescriber, should be extracted to some kind of utils?
    private boolean isLibraryCandidate(ResolutionCandidateAssessor.AssessedCandidate candidate) {
        AttributeContainer candidateAttributes = candidate.getAllCandidateAttributes().getAttributes();
        for (Attribute<?> attribute : candidateAttributes.keySet()) {
            if (Objects.equals(attribute.getName(), Category.CATEGORY_ATTRIBUTE.getName())) {
                String category = String.valueOf(candidateAttributes.getAttribute(attribute));
                return Objects.equals(category, Category.LIBRARY);
            }
        }
        return false;
    }

    // TODO: Common pattern here, too - "is attribute X incompatible?"
    private boolean isPlatformTypeAttributeIncompatible(ResolutionCandidateAssessor.AssessedCandidate candidate) {
        return candidate.getIncompatibleAttributes().stream().anyMatch(a -> Objects.equals(a.getAttribute().getName(), PLATFORM_TYPE_ATTRIBUTE.getName()));
    }

    @Override
    public VariantSelectionException describeFailure(IncompatibleGraphVariantFailure failure, Optional<AttributesSchemaInternal> schema) {
        String platformTypeRequested = findRequestedPlatform(failure.getRequestedAttributes()).orElseThrow(IllegalStateException::new);
        String message = buildNeedsDifferentPlatformFailureMsg(failure.getRequestedName(), platformTypeRequested);
        List<String> resolutions = buildResolutions(suggestAddPlatformSupportToRequested(failure.getRequestedName(), platformTypeRequested), suggestDropPlatformSupportFromThis(platformTypeRequested));
        return new VariantSelectionException(message, failure, resolutions);
    }

    private Optional<String> findRequestedPlatform(AttributeContainer requestedAttributes) {
        return requestedAttributes.keySet().stream()
            .filter(a -> Objects.equals(a.getName(), PLATFORM_TYPE_ATTRIBUTE.getName()))
            .map(requestedAttributes::getAttribute)
            .map(a -> a.toString()) // TODO: this should use the KotlinPlatformType enum's methods, but that type isn't available here
            .findFirst();
    }

    private String buildNeedsDifferentPlatformFailureMsg(String requestedName, String requestedPlatform) {
        return String.format("Kotlin Multiplatform project requested a %s target, but %s doesn't provide %s support.", requestedPlatform, requestedName, requestedPlatform);
    }

    private String suggestAddPlatformSupportToRequested(String requestedName, String requestedPlatform) {
        return String.format("Update the dependency on '%s' to a different version that supports the %s platform.", requestedName, requestedPlatform);
    }

    private String suggestDropPlatformSupportFromThis(String requestedPlatform) {
        return String.format("Remove support for the %s platform from this project.", requestedPlatform);
    }
}
