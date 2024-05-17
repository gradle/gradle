/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.ResolutionFailureDescriberRegistry;
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactTransformFailure;
import org.gradle.internal.component.resolution.failure.type.AmbiguousResolutionFailure;
import org.gradle.internal.component.resolution.failure.type.ExternalRequestedConfigurationNotFoundFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleGraphVariantFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleMultipleNodeSelectionFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleRequestedConfigurationFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleResolutionFailure;
import org.gradle.internal.component.resolution.failure.type.NoMatchingCapabilitiesFailure;
import org.gradle.internal.component.resolution.failure.type.RequestedConfigurationNotFoundFailure;
import org.gradle.internal.component.resolution.failure.type.ResolutionFailure;
import org.gradle.internal.component.resolution.failure.type.UnknownArtifactSelectionFailure;
import org.gradle.internal.component.resolution.failure.type.VariantAwareAmbiguousResolutionFailure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Provides a central location for handling failures encountered during
 * each stage of the variant selection process during dependency resolution.
 *
 * All variant selection failures encountered during selection by the {@link GraphVariantSelector} or
 * {@link AttributeMatchingArtifactVariantSelector}
 * should be routed through this class.
 *
 * This class is responsible for packaging failure data into the appropriate {@link ResolutionFailure} type,
 * selecting the appropriate {@link ResolutionFailureDescriber} to describe that failure,
 * returning the result of running the describer.  It maintains a registry of <strong>default</strong>
 * describers for each failure type; but will first consult the {@link AttributesSchemaInternal} for
 * any custom describers registered on that schema for a given failure type.
 *
 * Class is non-{@code final} for testing via stubbing.
 */
public class ResolutionFailureHandler {
    public static final String DEFAULT_MESSAGE_PREFIX = "Review the variant matching algorithm at ";

    private final ResolutionFailureDescriberRegistry defaultFailureDescribers;

    public ResolutionFailureHandler(ResolutionFailureDescriberRegistry failureDescriberRegistry) {
        this.defaultFailureDescribers = failureDescriberRegistry;
    }

    // region Artifact Variant Selection Failures
    public AbstractResolutionFailureException noMatchingArtifactVariantFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, String variantSetName, ImmutableAttributes requestedAttributes, List<? extends ResolvedVariant> candidateVariants) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(candidateVariants);
        IncompatibleResolutionFailure failure = new IncompatibleResolutionFailure(variantSetName, requestedAttributes, assessedCandidates);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException ambiguousArtifactVariantsFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, String selectedComponentName, ImmutableAttributes requestedAttributes, List<? extends ResolvedVariant> matchingVariants) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(matchingVariants);
        AmbiguousResolutionFailure failure = new AmbiguousResolutionFailure(selectedComponentName, requestedAttributes, assessedCandidates);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException ambiguousArtifactTransformationFailure(AttributesSchemaInternal schema, String selectedComponentName, ImmutableAttributes requestedAttributes, List<TransformedVariant> transformedVariants) {
        AmbiguousArtifactTransformFailure failure = new AmbiguousArtifactTransformFailure(selectedComponentName, requestedAttributes, transformedVariants);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException unknownArtifactVariantSelectionFailure(AttributesSchemaInternal schema, ResolvedVariantSet producer, Exception cause) {
        UnknownArtifactSelectionFailure failure = new UnknownArtifactSelectionFailure(producer.asDescribable().getDisplayName(), cause);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException incompatibleArtifactVariantsFailure(AttributesSchemaInternal schema, ComponentState selectedComponent, Set<NodeState> incompatibleNodes) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(ImmutableAttributes.EMPTY, schema.matcher());
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessNodeStates(incompatibleNodes);
        IncompatibleMultipleNodeSelectionFailure failure = new IncompatibleMultipleNodeSelectionFailure(selectedComponent.toString(), assessedCandidates);
        return describeFailure(schema, failure);
    }
    // endregion Artifact Variant Selection Failures

    // region Graph Variant Selection Failures
    public AbstractResolutionFailureException ambiguousGraphVariantsFailure(
        AttributesSchemaInternal schema, AttributeMatcher matcher,
        AttributeContainerInternal requestedAttributes,
        List<? extends VariantGraphResolveState> matchingVariants,
        ComponentGraphResolveMetadata targetComponent
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariantStates(matchingVariants);
        VariantAwareAmbiguousResolutionFailure failure = new VariantAwareAmbiguousResolutionFailure(targetComponent.getId().getDisplayName(), requestedAttributes, assessedCandidates, targetComponent.getModuleVersionId());
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException noMatchingGraphVariantFailure(
        AttributesSchemaInternal schema,
        AttributeMatcher matcher,
        AttributeContainerInternal requestedAttributes,
        ComponentGraphResolveMetadata targetComponent,
        GraphSelectionCandidates candidates
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessGraphSelectionCandidates(candidates);
        IncompatibleGraphVariantFailure failure = new IncompatibleGraphVariantFailure(targetComponent.getId().getDisplayName(), requestedAttributes, assessedCandidates);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException noMatchingCapabilitiesFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, ImmutableAttributes requestedAttributes, ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> requestedCapabilities, List<? extends VariantGraphResolveState> candidates) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariantStates(candidates);
        NoMatchingCapabilitiesFailure failure = new NoMatchingCapabilitiesFailure(targetComponent.getId().getDisplayName(), requestedCapabilities, assessedCandidates, targetComponent.getModuleVersionId());
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException noVariantsExistFailure(AttributesSchemaInternal schema, AttributeContainerInternal requestedAttributes, ComponentIdentifier targetComponent) {
        IncompatibleGraphVariantFailure failure = new IncompatibleGraphVariantFailure(targetComponent.getDisplayName(), requestedAttributes, Collections.emptyList());
        return describeFailure(schema, failure);
    }

    // region Configuration by name
    // These are cases where a configuration is requested by name in a target component, so there is sometimes no relevant schema to provide to this handler method.

    public AbstractResolutionFailureException incompatibleRequestedConfigurationFailure(
        AttributesSchemaInternal schema,
        AttributeMatcher matcher,
        AttributeContainerInternal requestedAttributes,
        ComponentGraphResolveMetadata targetComponent,
        VariantGraphResolveState targetConfiguration
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = Collections.singletonList(resolutionCandidateAssessor.assessCandidate(targetConfiguration.getName(), targetConfiguration.getCapabilities(), targetConfiguration.getMetadata().getAttributes()));
        IncompatibleRequestedConfigurationFailure failure = new IncompatibleRequestedConfigurationFailure(targetComponent.getId().getDisplayName(), requestedAttributes, assessedCandidates);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException configurationNotFoundFailure(ComponentIdentifier toComponent, String toConfigurationName) {
        RequestedConfigurationNotFoundFailure failure = new RequestedConfigurationNotFoundFailure(toConfigurationName, toComponent);
        return describeFailure(failure);
    }
    public AbstractResolutionFailureException externalConfigurationNotFoundFailure(String fromConfigurationName, ComponentIdentifier toComponent, String toConfigurationName) {
        ExternalRequestedConfigurationNotFoundFailure failure = new ExternalRequestedConfigurationNotFoundFailure(toConfigurationName, toComponent, fromConfigurationName);
        return describeFailure(failure);
    }

    // endregion Configuration by name
    // endregion Graph Variant Selection Failures

    private <FAILURE extends ResolutionFailure> AbstractResolutionFailureException describeFailure(FAILURE failure) {
        @SuppressWarnings("unchecked")
        Class<FAILURE> failureType = (Class<FAILURE>) failure.getClass();
        List<ResolutionFailureDescriber<FAILURE>> describers = defaultFailureDescribers.getDescribers(failureType);
        return describeFailure(describers, failure, Optional.empty());
    }

    private <FAILURE extends ResolutionFailure> AbstractResolutionFailureException describeFailure(AttributesSchemaInternal schema, FAILURE failure) {
        @SuppressWarnings("unchecked")
        Class<FAILURE> failureType = (Class<FAILURE>) failure.getClass();
        List<ResolutionFailureDescriber<FAILURE>> describers = new ArrayList<>();
        describers.addAll(schema.getFailureDescribers(failureType));
        describers.addAll(defaultFailureDescribers.getDescribers(failureType));
        return describeFailure(describers, failure, Optional.of(schema));
    }

    private <FAILURE extends ResolutionFailure> AbstractResolutionFailureException describeFailure(List<ResolutionFailureDescriber<FAILURE>> describers, FAILURE failure, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<AttributesSchemaInternal> schema) {
        return describers.stream()
            .filter(describer -> describer.canDescribeFailure(failure))
            .findFirst()
            .map(describer -> describer.describeFailure(failure, schema))
            .orElseThrow(() -> new IllegalStateException("No describer found for failure: " + failure)); // TODO: a default describer at the end of the list that catches everything instead?
    }
}
