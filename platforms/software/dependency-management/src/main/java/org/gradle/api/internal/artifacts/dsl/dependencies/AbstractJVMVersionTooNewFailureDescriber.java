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

import org.gradle.api.JavaVersion;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.describer.AbstractResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure;
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Abstract base class for building {@link ResolutionFailureDescriber}s that describe {@link ResolutionFailure}s caused by
 * incompatibilities related to a dependency requiring a higher JVM version.
 */
public abstract class AbstractJVMVersionTooNewFailureDescriber extends AbstractResolutionFailureDescriber<NoCompatibleVariantsFailure> {
    /**
     * Returns the JVM version used in the comparison against the library that is causing the failure.
     *
     * @param failure the failure
     * @return the JVM version that is incompatible
     */
    protected abstract JavaVersion getJVMVersion(NoCompatibleVariantsFailure failure);

    protected final boolean isDueToJVMVersionTooNew(NoCompatibleVariantsFailure failure) {
        if (allLibraryCandidatesIncompatibleDueToJVMVersionTooLow(failure)) {
            JavaVersion minJVMVersionSupported = findMinJVMSupported(failure.getCandidates()).orElseThrow(IllegalStateException::new);
            return minJVMVersionSupported.compareTo(getJVMVersion(failure)) > 0;
        } else {
            return false;
        }
    }

    private boolean allLibraryCandidatesIncompatibleDueToJVMVersionTooLow(NoCompatibleVariantsFailure failure) {
        List<ResolutionCandidateAssessor.AssessedCandidate> libraryCandidates = failure.getCandidates().stream()
            .filter(this::isLibraryCandidate)
            .collect(Collectors.toList());
        if (!libraryCandidates.isEmpty()) {
            boolean requestingJDKVersion = failure.getRequestedAttributes().contains(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE);
            boolean allIncompatibleDueToJDKVersion = libraryCandidates.stream().allMatch(this::isJVMVersionAttributeIncompatible);
            return requestingJDKVersion && allIncompatibleDueToJDKVersion;
        } else {
            return false;
        }
    }

    protected final Optional<JavaVersion> findMinJVMSupported(List<ResolutionCandidateAssessor.AssessedCandidate> candidates) {
        return candidates.stream()
            .filter(this::isLibraryCandidate)
            .map(this::findMinJVMSupported)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .min(Comparator.comparing(JavaVersion::getMajorVersion));
    }

    private Optional<JavaVersion> findMinJVMSupported(ResolutionCandidateAssessor.AssessedCandidate candidate) {
        return candidate.getIncompatibleAttributes().stream()
            .filter(this::isJVMVersionAttribute)
            .map(jvmVersionAttribute -> JavaVersion.toVersion(Objects.requireNonNull(jvmVersionAttribute.getProvided())))
            .min(Comparator.comparing(JavaVersion::getMajorVersion));
    }

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

    private boolean isJVMVersionAttributeIncompatible(ResolutionCandidateAssessor.AssessedCandidate candidate) {
        return candidate.getIncompatibleAttributes().stream().anyMatch(this::isJVMVersionAttribute);
    }

    private boolean isJVMVersionAttribute(ResolutionCandidateAssessor.AssessedAttribute<?> attribute) {
        return attribute.getAttribute().getName().equals(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.getName());
    }
}
