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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.transform.AttributeMatchingArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.problems.Problems;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.describer.AmbiguousArtifactTransformFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.AmbiguousArtifactVariantsFailureDescriber2;
import org.gradle.internal.component.resolution.failure.describer.AmbiguousGraphVariantsFailureDescriber2;
import org.gradle.internal.component.resolution.failure.describer.CapabilitiesDescriber;
import org.gradle.internal.component.resolution.failure.describer.FailureDescriberRegistry2;
import org.gradle.internal.component.resolution.failure.describer.IncompatibleArtifactVariantsFailureDescriber2;
import org.gradle.internal.component.resolution.failure.describer.IncompatibleGraphVariantsFailureDescriber2;
import org.gradle.internal.component.resolution.failure.describer.IncompatibleRequestedConfigurationFailureDescriber2;
import org.gradle.internal.component.resolution.failure.describer.InvalidMultipleVariantsFailureDescriber2;
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber2;
import org.gradle.internal.component.resolution.failure.failures.AmbiguousArtifactTransformFailure2;
import org.gradle.internal.component.resolution.failure.failures.AmbiguousResolutionFailure2;
import org.gradle.internal.component.resolution.failure.failures.IncompatibleGraphVariantFailure2;
import org.gradle.internal.component.resolution.failure.failures.IncompatibleRequestedConfigurationFailure2;
import org.gradle.internal.component.resolution.failure.failures.IncompatibleResolutionFailure2;
import org.gradle.internal.component.resolution.failure.failures.InvalidMultipleVariantsSelectionFailure2;
import org.gradle.internal.component.resolution.failure.failures.ResolutionFailure2;
import org.gradle.internal.component.resolution.failure.failures.VariantAwareAmbiguousResolutionFailure2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// TODO: Register all failures with the Problems API

/**
 * Provides a central location for logging and reporting failures appearing during
 * each stage of resolution, including during the variant selection process.
 *
 * All variant selection failures encountered during selection by the {@link GraphVariantSelector} or
 * {@link AttributeMatchingArtifactVariantSelector}
 * should be routed through this class.
 *
 * This class reports failures to the {@link Problems} service.  It is a
 * Gradle managed type, and so it can serve as an injection point for any types wishing to be notified or respond
 * to the variant selection process.
 */
public class ResolutionFailureHandler {
    public static final String DEFAULT_MESSAGE_PREFIX = "Review the variant matching algorithm at ";

    private final FailureDescriberRegistry2 defaultFailureDescribers2;

    private final DocumentationRegistry documentationRegistry;

    public ResolutionFailureHandler(FailureDescriberRegistry2 failureDescriberRegistry, DocumentationRegistry documentationRegistry) {
        this.defaultFailureDescribers2 = failureDescriberRegistry;
        this.documentationRegistry = documentationRegistry;

        defaultFailureDescribers2.registerDescriber(AmbiguousGraphVariantsFailureDescriber2.class);
        defaultFailureDescribers2.registerDescriber(IncompatibleGraphVariantsFailureDescriber2.class);

        defaultFailureDescribers2.registerDescriber(AmbiguousArtifactVariantsFailureDescriber2.class);
        defaultFailureDescribers2.registerDescriber(IncompatibleArtifactVariantsFailureDescriber2.class);
        defaultFailureDescribers2.registerDescriber(InvalidMultipleVariantsFailureDescriber2.class);
        defaultFailureDescribers2.registerDescriber(AmbiguousArtifactTransformFailureDescriber.class);

        defaultFailureDescribers2.registerDescriber(IncompatibleRequestedConfigurationFailureDescriber2.class);
    }

    private void suggestSpecificDocumentation(AbstractVariantSelectionException exception, String prefix, String section) {
        exception.addResolution(prefix + documentationRegistry.getDocumentationFor("variant_model", section) + ".");
    }

    private void suggestReviewAlgorithm(AbstractVariantSelectionException exception) {
        exception.addResolution(DEFAULT_MESSAGE_PREFIX + documentationRegistry.getDocumentationFor("variant_attributes", "sec:abm_algorithm") + ".");
    }

    private <FAILURE extends ResolutionFailure2> AbstractVariantSelectionException describeFailure2(AttributesSchemaInternal schema, FAILURE failure) {
        List<ResolutionFailureDescriber2<?, FAILURE>> describers = new ArrayList<>();
        describers.addAll(schema.getFailureDescribers2(failure));
        describers.addAll(defaultFailureDescribers2.getDescribers(failure));

        return describers.stream()
            .filter(describer -> describer.canDescribeFailure(failure))
            .findFirst()
            .map(describer -> describer.describeFailure(failure))
            .orElseThrow(() -> new IllegalStateException("No describer found for failure: " + failure)); // TODO: a default describer at the end of the list that catches everything instead?
    }

    // region Artifact Variant Selection Failures
    public AbstractVariantSelectionException noMatchingArtifactVariantFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, String variantSetName, ImmutableAttributes requestedAttributes, List<? extends ResolvedVariant> candidateVariants) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(candidateVariants);
        IncompatibleResolutionFailure2 failure = new IncompatibleResolutionFailure2(schema, variantSetName, requestedAttributes, assessedCandidates);
        return describeFailure2(schema, failure);
    }

    public AbstractVariantSelectionException ambiguousArtifactVariantsFailure(AttributesSchemaInternal schema, AttributeMatcher matcher, String selectedComponentName, ImmutableAttributes requestedAttributes, List<? extends ResolvedVariant> matchingVariants, Set<ResolvedVariant> discardedVariants) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(matchingVariants);
        AmbiguousResolutionFailure2 failure = new AmbiguousResolutionFailure2(schema, selectedComponentName, requestedAttributes, assessedCandidates);
        return describeFailure2(schema, failure);
    }

    public AbstractVariantSelectionException ambiguousArtifactTransformationFailure(AttributesSchemaInternal schema, @SuppressWarnings("unused") AttributeMatcher matcher, String selectedComponentName, ImmutableAttributes requestedAttributes, List<TransformedVariant> transformedVariants) {
        AmbiguousArtifactTransformFailure2 failure = new AmbiguousArtifactTransformFailure2(schema, selectedComponentName, requestedAttributes, transformedVariants);
        return describeFailure2(schema, failure);
    }

    // TODO: Unify this failure in the exception hierarchy with the others
    public BrokenResolvedArtifactSet unknownArtifactVariantSelectionFailure(@SuppressWarnings("unused") AttributesSchema schema, ArtifactVariantSelectionException t) {
        return new BrokenResolvedArtifactSet(t);
        // TODO: perhaps unify this with the other failures in the hierarchy so standard suggestions can be added?  Or maybe it's its own hierachy?
    }

    public BrokenResolvedArtifactSet unknownArtifactVariantSelectionFailure(AttributesSchema schema, ResolvedVariantSet producer, Exception cause) {
        String message = buildUnknownArtifactVariantFailureMsg(producer);
        ArtifactVariantSelectionException e = new ArtifactVariantSelectionException(message, cause);
        // This is the catch-all error type and there's nothing more specific to add here
        suggestReviewAlgorithm(e);
        return unknownArtifactVariantSelectionFailure(schema, e);
    }

    public AbstractVariantSelectionException incompatibleArtifactVariantsFailure(AttributesSchemaInternal schema, ComponentState selectedComponent, Set<NodeState> incompatibleNodes) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(ImmutableAttributes.EMPTY, schema.matcher());
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessCandidates(extractVariants(incompatibleNodes));
        InvalidMultipleVariantsSelectionFailure2 failure = new InvalidMultipleVariantsSelectionFailure2(schema, selectedComponent.toString(), assessedCandidates);
        return describeFailure2(schema, failure);
    }

    private String buildUnknownArtifactVariantFailureMsg(ResolvedVariantSet producer) {
        return String.format("Could not select a variant of %s that matches the consumer attributes.", producer.asDescribable().getDisplayName());
    }
    // endregion Artifact Variant Selection Failures

    // region Graph Variant Selection Failures
    public AbstractVariantSelectionException ambiguousGraphVariantsFailure(
        AttributesSchemaInternal schema, AttributeMatcher matcher,
        AttributeContainerInternal requestedAttributes,
        List<? extends VariantGraphResolveState> matchingVariants,
        ComponentGraphResolveMetadata targetComponent
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessCandidates(extractVariants(matchingVariants));
        VariantAwareAmbiguousResolutionFailure2 failure = new VariantAwareAmbiguousResolutionFailure2(schema, targetComponent.getId().getDisplayName(), requestedAttributes, assessedCandidates, targetComponent);
        return describeFailure2(schema, failure);
    }

    // TODO: rename to Incompatible Selected Configuration? Yes, do this, match the classes
    public AbstractVariantSelectionException incompatibleGraphVariantFailure(
        AttributesSchemaInternal schema,
        AttributeMatcher matcher,
        AttributeContainerInternal requestedAttributes,
        ComponentGraphResolveMetadata targetComponent,
        ConfigurationGraphResolveState targetConfiguration
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = Collections.singletonList(resolutionCandidateAssessor.assessCandidate(targetConfiguration.getName(), targetConfiguration.asVariant().getCapabilities(), targetConfiguration.asVariant().getMetadata().getAttributes()));
        IncompatibleRequestedConfigurationFailure2 failure = new IncompatibleRequestedConfigurationFailure2(schema, targetComponent.getId().getDisplayName(), requestedAttributes, assessedCandidates);
        return describeFailure2(schema, failure);
    }

    public AbstractVariantSelectionException noMatchingGraphVariantFailure(
        AttributesSchemaInternal schema,
        AttributeMatcher matcher,
        AttributeContainerInternal requestedAttributes,
        ComponentGraphResolveMetadata targetComponent,
        GraphSelectionCandidates candidates
    ) {
        ResolutionCandidateAssessor resolutionCandidateAssessor = new ResolutionCandidateAssessor(requestedAttributes, matcher);
        List<AssessedCandidate> assessedCandidates = resolutionCandidateAssessor.assessCandidates(extractVariants(candidates));
        IncompatibleGraphVariantFailure2 failure = new IncompatibleGraphVariantFailure2(schema, targetComponent.getId().getDisplayName(), requestedAttributes, assessedCandidates, targetComponent);
        return describeFailure2(schema, failure);
    }

    public NoMatchingCapabilitiesException noMatchingCapabilitiesFailure(@SuppressWarnings("unused") AttributesSchemaInternal schema, @SuppressWarnings("unused") AttributeMatcher matcher, ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> requestedCapabilities, List<? extends VariantGraphResolveState> candidates) {
        String message = buildNoMatchingCapabilitiesFailureMsg(targetComponent, requestedCapabilities, candidates);
        NoMatchingCapabilitiesException e = new NoMatchingCapabilitiesException(message);
        suggestReviewAlgorithm(e);
        return e;
    }

    public ConfigurationNotFoundException configurationNotFoundFailure(@SuppressWarnings("unused") AttributesSchemaInternal schema, String targetConfigurationName, ComponentIdentifier targetComponentId) {
        String message = buildConfigurationNotFoundFailureMsg(targetConfigurationName, targetComponentId);
        ConfigurationNotFoundException e = new ConfigurationNotFoundException(message);
        suggestReviewAlgorithm(e);
        return e;
    }

    /**
     * This is a special case where a configuration is requested by name in a target component, so there is no relevant schema to provide to this handler method.
     */
    public ExternalConfigurationNotFoundException externalConfigurationNotFoundFailure(ComponentIdentifier fromComponent, String fromConfigurationName, ComponentIdentifier toComponent, String toConfigurationName) {
        String message = buildExternalConfigurationNotFoundFailureMsg(fromComponent, fromConfigurationName, toConfigurationName, toComponent);
        ExternalConfigurationNotFoundException e = new ExternalConfigurationNotFoundException(message);
        suggestReviewAlgorithm(e);
        return e;
    }

    public ConfigurationNotConsumableException configurationNotConsumableFailure(@SuppressWarnings("unused") AttributesSchemaInternal schema, String targetComponentName, String targetConfigurationName) {
        String message = buildConfigurationNotConsumableFailureMsg(targetComponentName, targetConfigurationName);
        ConfigurationNotConsumableException e = new ConfigurationNotConsumableException(message);
        suggestReviewAlgorithm(e);
        return e;
    }

    private String buildConfigurationNotFoundFailureMsg(String targetConfigurationName, ComponentIdentifier targetComponentId) {
        return String.format("A dependency was declared on configuration '%s' which is not declared in the descriptor for %s.", targetConfigurationName, targetComponentId.getDisplayName());
    }

    private String buildExternalConfigurationNotFoundFailureMsg(ComponentIdentifier fromComponent, String fromConfiguration, String toConfiguration, ComponentIdentifier toComponent) {
        return String.format("%s declares a dependency from configuration '%s' to configuration '%s' which is not declared in the descriptor for %s.", StringUtils.capitalize(fromComponent.getDisplayName()), fromConfiguration, toConfiguration, toComponent.getDisplayName());
    }

    private String buildConfigurationNotConsumableFailureMsg(String targetComponentName, String targetConfigurationName) {
        return String.format("Selected configuration '" + targetConfigurationName + "' on '" + targetComponentName + "' but it can't be used as a project dependency because it isn't intended for consumption by other components.");
    }

    private String buildNoMatchingCapabilitiesFailureMsg(ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> requestedCapabilities, List<? extends VariantGraphResolveState> candidates) {
        StringBuilder sb = new StringBuilder("Unable to find a variant of ");
        sb.append(targetComponent.getId()).append(" providing the requested ");
        sb.append(CapabilitiesDescriber.describeCapabilitiesWithTitle(targetComponent, requestedCapabilities));
        sb.append(":\n");
        for (VariantGraphResolveState candidate : candidates) {
            sb.append("   - Variant ").append(candidate.getName()).append(" provides ");
            sb.append(CapabilitiesDescriber.describeCapabilities(targetComponent, candidate.getCapabilities().asSet())).append("\n");
        }
        return sb.toString();
    }

    /**
     * Extracts variant metadata from the given {@link GraphSelectionCandidates}.
     *
     * @param candidates the source from which to extract variants
     * @return the extracted variants, sorted by name
     */
    private List<? extends VariantGraphResolveMetadata> extractVariants(GraphSelectionCandidates candidates) {
        final List<? extends VariantGraphResolveMetadata> variants;
        if (candidates.isUseVariants()) {
            variants = candidates.getVariants().stream()
                .map(VariantGraphResolveState::getMetadata)
                .collect(Collectors.toList());
        } else {
            variants = new ArrayList<>(candidates.getCandidateConfigurations()); // Need non-immutable copy to sort
        }

        variants.sort(Comparator.comparing(VariantGraphResolveMetadata::getName));
        return variants;
    }

    /**
     * Extracts variant metadata from the given {@link Set} of {@link NodeState}s.
     *
     * @param nodes the source from which to extract variants
     * @return the extracted variants, sorted by name
     */
    private List<? extends VariantGraphResolveMetadata> extractVariants(Set<NodeState> nodes) {
        return nodes.stream()
            .map(NodeState::getMetadata)
            .sorted(Comparator.comparing(VariantGraphResolveMetadata::getName))
            .collect(Collectors.toList());
    }

    /**
     * Extracts variant metadata from the given {@link List} of {@link VariantGraphResolveState}s.
     *
     * @param variantStates the source from which to extract variants
     * @return the extracted variants, sorted by name
     */
    private List<? extends VariantGraphResolveMetadata> extractVariants(List<? extends VariantGraphResolveState> variantStates) {
        return variantStates.stream()
            .map(VariantGraphResolveState::getMetadata)
            .sorted(Comparator.comparing(VariantGraphResolveMetadata::getName))
            .collect(Collectors.toList());
    }
    // endregion Graph Variant Selection Failures
}
