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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.problems.internal.Problem;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.AttributeMatcher;
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
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactTransformsFailure;
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactsFailure;
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleMultipleNodesValidationFailure;
import org.gradle.internal.component.resolution.failure.type.ConfigurationNotCompatibleFailure;
import org.gradle.internal.component.resolution.failure.type.NoCompatibleArtifactFailure;
import org.gradle.internal.component.resolution.failure.type.NoVariantsWithMatchingCapabilitiesFailure;
import org.gradle.internal.component.resolution.failure.type.ConfigurationDoesNotExistFailure;
import org.gradle.internal.component.resolution.failure.type.UnknownArtifactSelectionFailure;
import org.gradle.internal.component.resolution.failure.type.AmbiguousVariantsFailure;
import org.gradle.util.internal.TextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.gradle.api.problems.Severity.ERROR;
import static org.gradle.internal.deprecation.Documentation.userManual;

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

    private final ResolutionFailureDescriberRegistry defaultFailureDescribers;
    private final InternalProblems problemsService;

    public ResolutionFailureHandler(ResolutionFailureDescriberRegistry failureDescriberRegistry, InternalProblems problemsService) {
        this.defaultFailureDescribers = failureDescriberRegistry;
        this.problemsService = problemsService;
    }

    // region Component Selection failures
    // TODO: Route these failures through this handler in order to standardize their description logic, supply consistent failure reporting
    //  via the Problems API, and allow for the possible custom descriptions in specific scenarios
    // endregion Component Selection failures

    // region Variant Selection failures
    public AbstractResolutionFailureException configurationNotCompatibleFailure(
        AttributesSchemaInternal schema,
        AttributeMatcher matcher,
        ComponentGraphResolveState targetComponent,
        VariantGraphResolveState targetConfiguration,
        AttributeContainerInternal requestedAttributes,
        ImmutableCapabilities targetConfigurationCapabilities
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = Collections.singletonList(resolutionCandidateAssessor.assessCandidate(targetConfiguration.getName(), targetConfigurationCapabilities, targetConfiguration.getAttributes()));
        ConfigurationNotCompatibleFailure failure = new ConfigurationNotCompatibleFailure(targetComponent.getId(), targetConfiguration.getName(), requestedAttributes, assessedCandidates);
        return describeFailure(schema, failure);
    }
    public AbstractResolutionFailureException configurationDoesNotExistFailure(ComponentGraphResolveState targetComponent, String targetConfigurationName) {
        ConfigurationDoesNotExistFailure failure = new ConfigurationDoesNotExistFailure(targetComponent.getId(), targetConfigurationName);
        return describeFailure(failure);
    }

    public AbstractResolutionFailureException ambiguousVariantsFailure(
        AttributesSchemaInternal schema, AttributeMatcher matcher,
        ComponentGraphResolveState targetComponent,
        AttributeContainerInternal requestedAttributes,
        ImmutableCapabilities requestedCapabilities,
        List<? extends VariantGraphResolveState> matchingVariants
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariantStates(matchingVariants, targetComponent.getDefaultCapability());
        AmbiguousVariantsFailure failure = new AmbiguousVariantsFailure(targetComponent.getId(), requestedAttributes, requestedCapabilities, assessedCandidates);
        return describeFailure(schema, failure);
    }

    // TODO: This is the same logic as the noCompatibleVariantsFailure case for now.  We want to split the NoCompatibleVariantsFailureDescriber into
    // separate describers, with separate failures, for these different types.
    public AbstractResolutionFailureException noVariantsFailure(
        AttributesSchemaInternal schema, AttributeMatcher matcher,
        ComponentGraphResolveState targetComponent,
        AttributeContainerInternal requestedAttributes,
        ImmutableCapabilities requestedCapabilities,
        GraphSelectionCandidates candidates
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessGraphSelectionCandidates(candidates);
        NoCompatibleVariantsFailure failure = new NoCompatibleVariantsFailure(targetComponent.getId(), requestedAttributes, requestedCapabilities, assessedCandidates);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException noCompatibleVariantsFailure(
        AttributesSchemaInternal schema, AttributeMatcher matcher,
        ComponentGraphResolveState targetComponent,
        AttributeContainerInternal requestedAttributes,
        ImmutableCapabilities requestedCapabilities,
        GraphSelectionCandidates candidates
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessGraphSelectionCandidates(candidates);
        NoCompatibleVariantsFailure failure = new NoCompatibleVariantsFailure(targetComponent.getId(), requestedAttributes, requestedCapabilities, assessedCandidates);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException noVariantsWithMatchingCapabilitiesFailure(
        AttributesSchemaInternal schema, AttributeMatcher matcher,
        ComponentGraphResolveState targetComponent,
        ImmutableAttributes requestedAttributes,
        ImmutableCapabilities requestedCapabilities,
        List<? extends VariantGraphResolveState> candidates
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariantStates(candidates, targetComponent.getDefaultCapability());
        NoVariantsWithMatchingCapabilitiesFailure failure = new NoVariantsWithMatchingCapabilitiesFailure(targetComponent.getId(), requestedAttributes, requestedCapabilities, assessedCandidates);
        return describeFailure(schema, failure);
    }
    // endregion Variant Selection failures

    // region Graph Validation failures
    public AbstractResolutionFailureException incompatibleMultipleNodesValidationFailure(AttributesSchemaInternal schema, ComponentGraphResolveMetadata selectedComponent, Set<VariantGraphResolveMetadata> incompatibleNodes) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(ImmutableAttributes.EMPTY, schema.matcher());
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessNodeMetadatas(incompatibleNodes);
        IncompatibleMultipleNodesValidationFailure failure = new IncompatibleMultipleNodesValidationFailure(selectedComponent, incompatibleNodes, assessedCandidates);
        return describeFailure(schema, failure);
    }
    // endregion Graph Validation failures

    // region Artifact Selection failures
    public AbstractResolutionFailureException ambiguousArtifactTransformsFailure(AttributesSchemaInternal schema, ResolvedVariantSet targetVariantSet, ImmutableAttributes requestedAttributes, List<TransformedVariant> transformedVariants) {
        AmbiguousArtifactTransformsFailure failure = new AmbiguousArtifactTransformsFailure(getOrCreateVariantSetComponentIdentifier(targetVariantSet), targetVariantSet.asDescribable().getDisplayName(), requestedAttributes, transformedVariants);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException noCompatibleArtifactFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, ResolvedVariantSet targetVariantSet, ImmutableAttributes requestedAttributes, List<? extends ResolvedVariant> candidateVariants) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(candidateVariants);
        NoCompatibleArtifactFailure failure = new NoCompatibleArtifactFailure(getOrCreateVariantSetComponentIdentifier(targetVariantSet), targetVariantSet.asDescribable().getDisplayName(), requestedAttributes, assessedCandidates);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException ambiguousArtifactsFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, ResolvedVariantSet targetVariantSet, ImmutableAttributes requestedAttributes, List<? extends ResolvedVariant> matchingVariants) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(matchingVariants);
        AmbiguousArtifactsFailure failure = new AmbiguousArtifactsFailure(getOrCreateVariantSetComponentIdentifier(targetVariantSet), targetVariantSet.asDescribable().getDisplayName(), requestedAttributes, assessedCandidates);
        return describeFailure(schema, failure);
    }

    public AbstractResolutionFailureException unknownArtifactVariantSelectionFailure(AttributesSchemaInternal schema, ResolvedVariantSet targetVariantSet, ImmutableAttributes requestAttributes, Exception cause) {
        UnknownArtifactSelectionFailure failure = new UnknownArtifactSelectionFailure(getOrCreateVariantSetComponentIdentifier(targetVariantSet), targetVariantSet.asDescribable().getDisplayName(), requestAttributes, cause);
        return describeFailure(schema, failure);
    }

    private ComponentIdentifier getOrCreateVariantSetComponentIdentifier(ResolvedVariantSet resolvedVariantSet) {
        return resolvedVariantSet.getComponentIdentifier() != null ? resolvedVariantSet.getComponentIdentifier() : () -> resolvedVariantSet.asDescribable().getDisplayName();
    }
    // endregion Artifact Selection failures

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
            .map(this::reportExceptionAsProblem)
            .orElseThrow(() -> new IllegalStateException("No describer found for failure: " + failure)); // TODO: a default describer at the end of the list that catches everything instead?
    }

    private AbstractResolutionFailureException reportExceptionAsProblem(AbstractResolutionFailureException exception) {
        Problem problem = problemsService.getInternalReporter().create(builder -> {
            ResolutionFailureProblemId problemId = exception.getFailure().getProblemId();
            builder.id(TextUtil.screamingSnakeToKebabCase(problemId.name()), problemId.getDisplayName(), GradleCoreProblemGroup.variantResolution())
                .contextualLabel(exception.getMessage())
                .documentedAt(userManual("variant_model", "sec:variant-select-errors"))
                .severity(ERROR);
        });
        problemsService.getInternalReporter().report(problem);

        return exception;
    }
}
