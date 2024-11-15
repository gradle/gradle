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

package org.gradle.internal.component.resolution.failure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory;
import org.gradle.api.problems.internal.DefaultResolutionFailureData;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.problems.internal.ResolutionFailureData;
import org.gradle.api.problems.internal.ResolutionFailureDataSpec;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure;
import org.gradle.internal.component.resolution.failure.transform.TransformationChainData;
import org.gradle.internal.component.resolution.failure.transform.TransformedVariantConverter;
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactTransformsFailure;
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactsFailure;
import org.gradle.internal.component.resolution.failure.type.AmbiguousVariantsFailure;
import org.gradle.internal.component.resolution.failure.type.ConfigurationDoesNotExistFailure;
import org.gradle.internal.component.resolution.failure.type.ConfigurationNotCompatibleFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleMultipleNodesValidationFailure;
import org.gradle.internal.component.resolution.failure.type.NoCompatibleArtifactFailure;
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure;
import org.gradle.internal.component.resolution.failure.type.NoVariantsWithMatchingCapabilitiesFailure;
import org.gradle.internal.component.resolution.failure.type.UnknownArtifactSelectionFailure;
import org.gradle.internal.instantiation.InstanceGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Provides a central location for handling failures encountered during
 * each stage of the variant selection process during dependency resolution.
 *
 * All resolution failures encountered during selection by the {@link GraphVariantSelector} or
 * {@link AttributeMatchingArtifactVariantSelector}
 * should be routed through this class.
 *
 * This class is responsible for packaging {@link ResolutionFailure} data into the appropriate failure types,
 * selecting the appropriate {@link ResolutionFailureDescriber} to describe that failure,
 * returning the result of running the describer.  It maintains a registry of <strong>default</strong>
 * describers for each failure type; but will first consult the {@link AttributesSchemaInternal} for
 * any custom describers registered on that schema for a given failure type.
 *
 * Class is non-{@code final} for testing via stubbing.
 *
 * @implNote The methods for reporting failures via this class are ordered to match the stages of the variant selection process and
 * within those stages alphabetically, with names aligned with the failure type hierarchy.  This makes it much easier to navigate.
 */
public class ResolutionFailureHandler {
    public static final String DEFAULT_MESSAGE_PREFIX = "Review the variant matching algorithm at ";

    private final TransformedVariantConverter transformedVariantConverter;

    private final ResolutionFailureDescriberRegistry defaultFailureDescribers;
    private final ResolutionFailureDescriberRegistry customFailureDescribers;

    public ResolutionFailureHandler(InstanceGenerator instanceGenerator, InternalProblems problemsService, TransformedVariantConverter transformedVariantConverter) {
        this.transformedVariantConverter = transformedVariantConverter;

        this.defaultFailureDescribers = ResolutionFailureDescriberRegistry.standardRegistry(instanceGenerator);
        this.customFailureDescribers = ResolutionFailureDescriberRegistry.emptyRegistry(instanceGenerator);

        configureAdditionalDataBuilder(problemsService.getAdditionalDataBuilderFactory());
    }

    private static void configureAdditionalDataBuilder(AdditionalDataBuilderFactory additionalDataBuilderFactory) {
        if (!additionalDataBuilderFactory.hasProviderForSpec(ResolutionFailureDataSpec.class)) {
            additionalDataBuilderFactory.registerAdditionalDataProvider(
                ResolutionFailureDataSpec.class,
                data -> DefaultResolutionFailureData.builder((ResolutionFailureData) data)
            );
        }
    }

    // region Component Selection failures
    // TODO: Route these failures through this handler in order to standardize their description logic, supply consistent failure reporting
    //  via the Problems API, and allow for the possible custom descriptions in specific scenarios
    // endregion Component Selection failures

    // region Variant Selection failures
    public AbstractResolutionFailureException configurationNotCompatibleFailure(
        AttributeMatcher matcher,
        ComponentGraphResolveState targetComponent,
        VariantGraphResolveState targetConfiguration,
        AttributeContainerInternal requestedAttributes,
        ImmutableCapabilities targetConfigurationCapabilities
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = Collections.singletonList(resolutionCandidateAssessor.assessCandidate(targetConfiguration.getName(), targetConfigurationCapabilities, targetConfiguration.getAttributes()));
        ConfigurationNotCompatibleFailure failure = new ConfigurationNotCompatibleFailure(targetComponent.getId(), targetConfiguration.getName(), requestedAttributes, assessedCandidates);
        return describeFailure(failure);
    }

    public AbstractResolutionFailureException configurationDoesNotExistFailure(
        ComponentGraphResolveState targetComponent,
        String targetConfigurationName
    ) {
        ConfigurationDoesNotExistFailure failure = new ConfigurationDoesNotExistFailure(targetComponent.getId(), targetConfigurationName);
        return describeFailure(failure);
    }

    public AbstractResolutionFailureException ambiguousVariantsFailure(
        AttributeMatcher matcher,
        ComponentGraphResolveState targetComponent,
        AttributeContainerInternal requestedAttributes,
        Set<CapabilitySelector> requestedCapabilities,
        List<? extends VariantGraphResolveState> matchingVariants
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariantStates(matchingVariants, targetComponent.getDefaultCapability());
        AmbiguousVariantsFailure failure = new AmbiguousVariantsFailure(targetComponent.getId(), requestedAttributes, ImmutableSet.copyOf(requestedCapabilities), assessedCandidates);
        return describeFailure(failure);
    }

    // TODO: This is the same logic as the noCompatibleVariantsFailure case for now.  We want to split the NoCompatibleVariantsFailureDescriber into
    // separate describers, with separate failures, for these different types.
    public AbstractResolutionFailureException noVariantsFailure(
        ComponentGraphResolveState targetComponent,
        AttributeContainerInternal requestedAttributes
    ) {
        List<AssessedCandidate> assessedCandidates = Collections.emptyList();
        NoCompatibleVariantsFailure failure = new NoCompatibleVariantsFailure(targetComponent.getId(), requestedAttributes, ImmutableSet.of(), assessedCandidates);
        return describeFailure(failure);
    }

    public AbstractResolutionFailureException noCompatibleVariantsFailure(
        AttributeMatcher matcher,
        ComponentGraphResolveState targetComponent,
        AttributeContainerInternal requestedAttributes,
        Set<CapabilitySelector> requestedCapabilities,
        GraphSelectionCandidates candidates
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessGraphSelectionCandidates(candidates);
        NoCompatibleVariantsFailure failure = new NoCompatibleVariantsFailure(targetComponent.getId(), requestedAttributes, ImmutableSet.copyOf(requestedCapabilities), assessedCandidates);
        return describeFailure(failure);
    }

    public AbstractResolutionFailureException noVariantsWithMatchingCapabilitiesFailure(
        AttributeMatcher matcher,
        ComponentGraphResolveState targetComponent,
        ImmutableAttributes requestedAttributes,
        Set<CapabilitySelector> requestedCapabilities,
        List<? extends VariantGraphResolveState> candidates
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariantStates(candidates, targetComponent.getDefaultCapability());
        NoVariantsWithMatchingCapabilitiesFailure failure = new NoVariantsWithMatchingCapabilitiesFailure(targetComponent.getId(), requestedAttributes, ImmutableSet.copyOf(requestedCapabilities), assessedCandidates);
        return describeFailure(failure);
    }
    // endregion Variant Selection failures

    // region Graph Validation failures
    public AbstractResolutionFailureException incompatibleMultipleNodesValidationFailure(
        AttributeMatcher matcher,
        ComponentGraphResolveMetadata selectedComponent,
        Set<VariantGraphResolveMetadata> incompatibleNodes
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(ImmutableAttributes.EMPTY, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessNodeMetadatas(incompatibleNodes);
        IncompatibleMultipleNodesValidationFailure failure = new IncompatibleMultipleNodesValidationFailure(selectedComponent, incompatibleNodes, assessedCandidates);
        return describeFailure(failure);
    }
    // endregion Graph Validation failures

    // region Artifact Selection failures
    public AbstractResolutionFailureException ambiguousArtifactTransformsFailure(
        ResolvedVariantSet targetVariantSet,
        ImmutableAttributes requestedAttributes,
        List<TransformedVariant> transformedVariants
    ) {
        ImmutableList<TransformationChainData> transformationChainDatas = transformedVariantConverter.convert(transformedVariants);
        AmbiguousArtifactTransformsFailure failure = new AmbiguousArtifactTransformsFailure(getOrCreateVariantSetComponentIdentifier(targetVariantSet), targetVariantSet.asDescribable().getDisplayName(), requestedAttributes, transformationChainDatas);
        return describeFailure(failure);
    }

    public AbstractResolutionFailureException noCompatibleArtifactFailure(
        AttributeMatcher matcher,
        ResolvedVariantSet targetVariantSet,
        ImmutableAttributes requestedAttributes
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(targetVariantSet.getCandidates());
        NoCompatibleArtifactFailure failure = new NoCompatibleArtifactFailure(getOrCreateVariantSetComponentIdentifier(targetVariantSet), targetVariantSet.asDescribable().getDisplayName(), requestedAttributes, assessedCandidates);
        return describeFailure(failure);
    }

    public AbstractResolutionFailureException ambiguousArtifactsFailure(
        AttributeMatcher matcher,
        ResolvedVariantSet targetVariantSet,
        ImmutableAttributes requestedAttributes,
        List<? extends ResolvedVariant> matchingVariants
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(matchingVariants);
        AmbiguousArtifactsFailure failure = new AmbiguousArtifactsFailure(getOrCreateVariantSetComponentIdentifier(targetVariantSet), targetVariantSet.asDescribable().getDisplayName(), requestedAttributes, assessedCandidates);
        return describeFailure(failure);
    }

    public AbstractResolutionFailureException unknownArtifactVariantSelectionFailure(
        ResolvedVariantSet targetVariantSet,
        ImmutableAttributes requestAttributes,
        Exception cause
    ) {
        UnknownArtifactSelectionFailure failure = new UnknownArtifactSelectionFailure(getOrCreateVariantSetComponentIdentifier(targetVariantSet), targetVariantSet.asDescribable().getDisplayName(), requestAttributes, cause);
        return describeFailure(failure);
    }

    private ComponentIdentifier getOrCreateVariantSetComponentIdentifier(ResolvedVariantSet resolvedVariantSet) {
        return resolvedVariantSet.getComponentIdentifier() != null ? resolvedVariantSet.getComponentIdentifier() : () -> resolvedVariantSet.asDescribable().getDisplayName();
    }
    // endregion Artifact Selection failures

    /**
     * Adds a {@link ResolutionFailureDescriber} for the given failure type to the custom describers
     * registered on this failure handler.
     *
     * If variant selection failures occur, these describers will be available to describe the failures.
     *
     * @param failureType The type of failure to describe
     * @param describerType A describer that can potentially describe failures of the given type
     *
     * @param <FAILURE> The type of failure to describe
     */
    public <FAILURE extends ResolutionFailure> void addFailureDescriber(Class<FAILURE> failureType, Class<? extends ResolutionFailureDescriber<FAILURE>> describerType) {
        customFailureDescribers.registerDescriber(failureType, describerType);
    }

    private <FAILURE extends ResolutionFailure> AbstractResolutionFailureException describeFailure(FAILURE failure) {
        @SuppressWarnings("unchecked")
        Class<FAILURE> failureType = (Class<FAILURE>) failure.getClass();
        return Stream.concat(
                customFailureDescribers.getDescribers(failureType).stream(),
                defaultFailureDescribers.getDescribers(failureType).stream()
            )
            .filter(describer -> describer.canDescribeFailure(failure))
            .findFirst()
            .map(describer -> describer.describeFailure(failure))
            .orElseThrow(() -> new IllegalStateException("No describer found for failure: " + failure)); // TODO: a default describer at the end of the list that catches everything instead?
    }
}
