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
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.describer.AbstractResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionException;
import org.gradle.internal.component.resolution.failure.type.IncompatibleGraphVariantFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleResolutionFailure;
import org.gradle.internal.component.resolution.failure.type.ResolutionFailure;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link ResolutionFailureDescriber} that describes a {@link ResolutionFailure} caused by a requested component
 * requiring a higher JVM version than the current build is using.
 *
 * This is determined by assessing the incompatibility of the {@link TargetJvmVersion#TARGET_JVM_VERSION_ATTRIBUTE}
 * attribute and comparing the provided JVM version with the current JVM version.  Note that the requested
 * JVM version is <strong>NOT</strong> relevant to this failure - it's about an incompatibility
 * due to the candidate's JVM version being too high to work on this JVM, regardless of the requested version.
 */
public abstract class TargetJVMVersionTooHighFailureDescriber extends AbstractResolutionFailureDescriber<IncompatibleGraphVariantFailure> {
    private static final String JVM_VERSION_TOO_HIGH_TEMPLATE = "%s requires at least a Java %s JVM. This build uses a Java %s JVM.";

    private final JavaVersion currentJVMVersion = JavaVersion.current();

    @Override
    public boolean canDescribeFailure(IncompatibleGraphVariantFailure failure) {
        if (allLibraryCandidatesIncompatibleDueToJVMVersionTooLow(failure)) {
            JavaVersion minJVMVersionSupported = findMinJVMSupported(failure.getCandidates()).orElseThrow(IllegalStateException::new);
            return minJVMVersionSupported.compareTo(currentJVMVersion) > 0;
        } else {
            return false;
        }
    }

    @Override
    public AbstractResolutionFailureException describeFailure(IncompatibleGraphVariantFailure failure, Optional<AttributesSchemaInternal> schema) {
        JavaVersion minJVMVersionSupported = findMinJVMSupported(failure.getCandidates()).orElseThrow(IllegalStateException::new);
        String message = buildNeedsNewerJDKFailureMsg(failure.getRequestedName(), minJVMVersionSupported);
        List<String> resolutions = buildResolutions(suggestUpdateJVM(minJVMVersionSupported));
        return new VariantSelectionException(message, failure, resolutions);
    }

    private boolean allLibraryCandidatesIncompatibleDueToJVMVersionTooLow(IncompatibleResolutionFailure failure) {
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
        for (ResolutionCandidateAssessor.AssessedAttribute<?> attribute : candidate.getIncompatibleAttributes()) {
            if (isJVMVersionAttribute(attribute)) {
                return true;
            }
        }
        return false;
    }

    private boolean isJVMVersionAttribute(ResolutionCandidateAssessor.AssessedAttribute<?> attribute) {
        return attribute.getAttribute().getName().equals(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.getName());
    }

    private Optional<JavaVersion> findMinJVMSupported(List<ResolutionCandidateAssessor.AssessedCandidate> candidates) {
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

    private String buildNeedsNewerJDKFailureMsg(String pluginId, JavaVersion minRequiredJVMVersion) {
        return String.format(JVM_VERSION_TOO_HIGH_TEMPLATE, pluginId, minRequiredJVMVersion.getMajorVersion(), currentJVMVersion.getMajorVersion());
    }

    private String suggestUpdateJVM(JavaVersion minRequiredJVMVersion) {
        return "Run this build using a Java " + minRequiredJVMVersion.getMajorVersion() + " JVM (or newer).";
    }
}
