/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.component.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.capabilities.CapabilitiesMetadataInternal;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.internal.Cast;
import org.gradle.internal.component.SelectionFailureHandler;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Uses attribute matching to select a list one or more variants for a component in a graph.
 *
 * This class is intentionally named similarly to {@link ArtifactVariantSelector}, as it has a
 * similar purpose.  An instance of {@link SelectionFailureHandler} is injected in the constructor
 * to allow the caller to handle failures in a consistent way - all matching failures should be reported via
 * calls to that instance.
 */
public class GraphVariantSelector {
    private final SelectionFailureHandler failureProcessor;

    public GraphVariantSelector(SelectionFailureHandler failureProcessor) {
        this.failureProcessor = failureProcessor;
    }

    /**
     * Returns the failure processor which must be used to report failures during variant selection.
     *
     * @return the failure processor
     */
    public SelectionFailureHandler getFailureProcessor() {
        return failureProcessor;
    }

    public GraphVariantSelectionResult selectVariants(ImmutableAttributes consumerAttributes, Collection<? extends Capability> explicitRequestedCapabilities, ComponentGraphResolveState targetComponentState, AttributesSchemaInternal consumerSchema, List<IvyArtifactName> requestedArtifacts) {
        return selectVariants(consumerAttributes, explicitRequestedCapabilities, targetComponentState, consumerSchema, requestedArtifacts, AttributeMatchingExplanationBuilder.logging());
    }

    private GraphVariantSelectionResult selectVariants(ImmutableAttributes consumerAttributes, Collection<? extends Capability> explicitRequestedCapabilities, ComponentGraphResolveState targetComponentState, AttributesSchemaInternal consumerSchema, List<IvyArtifactName> requestedArtifacts, AttributeMatchingExplanationBuilder explanationBuilder) {
        ComponentGraphResolveMetadata targetComponent = targetComponentState.getMetadata();
        GraphSelectionCandidates candidates = targetComponentState.getCandidatesForGraphVariantSelection();
        AttributeMatcher attributeMatcher = consumerSchema.withProducer(targetComponent.getAttributesSchema());

        boolean variantAware = candidates.isUseVariants();

        // Fallback to the default configuration if there are no variants or if variant aware resolution is not supported.
        if (!variantAware) {
            return selectDefaultConfiguration(consumerAttributes, consumerSchema, targetComponent, attributeMatcher, candidates);
        }

        List<? extends VariantGraphResolveState> allConsumableVariants = candidates.getVariants();
        ImmutableList<VariantGraphResolveState> variantsProvidingRequestedCapabilities = filterVariantsByRequestedCapabilities(targetComponent, explicitRequestedCapabilities, allConsumableVariants, true);
        if (variantsProvidingRequestedCapabilities.isEmpty()) {
            throw failureProcessor.noMatchingCapabilitiesFailure(targetComponent, explicitRequestedCapabilities, allConsumableVariants);
        }

        List<VariantGraphResolveState> matches = attributeMatcher.matches(variantsProvidingRequestedCapabilities, consumerAttributes, explanationBuilder);
        if (matches.size() > 1) {
            // there's an ambiguity, but we may have several variants matching the requested capabilities.
            // Here we're going to check if in the candidates, there's a single one _strictly_ matching the requested capabilities.
            List<VariantGraphResolveState> strictlyMatchingCapabilities = filterVariantsByRequestedCapabilities(targetComponent, explicitRequestedCapabilities, matches, false);
            if (strictlyMatchingCapabilities.size() == 1) {
                return singleVariant(true, strictlyMatchingCapabilities);
            } else if (strictlyMatchingCapabilities.size() > 1) {
                // there are still more than one candidate, but this time we know only a subset strictly matches the required attributes
                // so we perform another round of selection on the remaining candidates
                strictlyMatchingCapabilities = attributeMatcher.matches(strictlyMatchingCapabilities, consumerAttributes, explanationBuilder);
                if (strictlyMatchingCapabilities.size() == 1) {
                    return singleVariant(true, strictlyMatchingCapabilities);
                }
            }

            if (requestedArtifacts.size() == 1) {
                // Here, we know that the user requested a specific classifier. There may be multiple
                // candidate variants left, but maybe only one of them provides the classified artifact
                // we're looking for.
                String classifier = requestedArtifacts.get(0).getClassifier();
                if (classifier != null) {
                    List<VariantGraphResolveState> sameClassifier = findVariantsProvidingExactlySameClassifier(matches, classifier);
                    if (sameClassifier != null && sameClassifier.size() == 1) {
                        return singleVariant(true, sameClassifier);
                    }
                }
            }
        }

        if (matches.size() == 1) {
            return singleVariant(true, matches);
        } else if (!matches.isEmpty()) {
            AttributeDescriber describer = DescriberSelector.selectDescriber(consumerAttributes, consumerSchema);
            if (explanationBuilder instanceof TraceDiscardedConfigurations) {
                Set<VariantGraphResolveState> discarded = Cast.uncheckedCast(((TraceDiscardedConfigurations) explanationBuilder).discarded);
                throw failureProcessor.ambiguousConfigurationSelectionFailure(describer, consumerAttributes, attributeMatcher, matches, targetComponent, true, discarded);
            } else {
                // Perform a second resolution with tracing
                return selectVariants(consumerAttributes, explicitRequestedCapabilities, targetComponentState, consumerSchema, requestedArtifacts, new TraceDiscardedConfigurations());
            }
        } else {
            AttributeDescriber describer = DescriberSelector.selectDescriber(consumerAttributes, consumerSchema);
            throw failureProcessor.noMatchingConfigurationSelectionFailure(describer, consumerAttributes, attributeMatcher, targetComponent, candidates);
        }
    }

    private GraphVariantSelectionResult selectDefaultConfiguration(
        ImmutableAttributes consumerAttributes, AttributesSchemaInternal consumerSchema,
        ComponentGraphResolveMetadata targetComponent, AttributeMatcher attributeMatcher, GraphSelectionCandidates candidates
    ) {
        ConfigurationGraphResolveState fallbackConfiguration = candidates.getLegacyConfiguration();
        if (fallbackConfiguration != null &&
            fallbackConfiguration.getMetadata().isCanBeConsumed() &&
            attributeMatcher.isMatching(fallbackConfiguration.getAttributes(), consumerAttributes)
        ) {
            return singleVariant(candidates.isUseVariants(), ImmutableList.of(fallbackConfiguration.asVariant()));
        }

        AttributeDescriber describer = DescriberSelector.selectDescriber(consumerAttributes, consumerSchema);
        throw failureProcessor.noMatchingConfigurationSelectionFailure(describer, consumerAttributes, attributeMatcher, targetComponent, candidates);
    }

    @Nullable
    private List<VariantGraphResolveState> findVariantsProvidingExactlySameClassifier(List<VariantGraphResolveState> matches, String classifier) {
        List<VariantGraphResolveState> sameClassifier = null;
        // let's see if we can find a single variant which has exactly the requested artifacts
        for (VariantGraphResolveState match : matches) {
            List<? extends ComponentArtifactMetadata> artifacts = match.resolveArtifacts().getArtifacts();
            if (artifacts.size() == 1) {
                ComponentArtifactMetadata componentArtifactMetadata = artifacts.get(0);
                if (componentArtifactMetadata instanceof ModuleComponentArtifactMetadata) {
                    ArtifactIdentifier artifactIdentifier = ((ModuleComponentArtifactMetadata) componentArtifactMetadata).toArtifactIdentifier();
                    if (classifier.equals(artifactIdentifier.getClassifier())) {
                        if (sameClassifier == null) {
                            sameClassifier = Collections.singletonList(match);
                        } else {
                            sameClassifier = Lists.newArrayList(sameClassifier);
                            sameClassifier.add(match);
                        }
                    }
                }
            }
        }
        return sameClassifier;
    }

    private GraphVariantSelectionResult singleVariant(boolean variantAware, List<VariantGraphResolveState> matches) {
        assert matches.size() == 1;
        VariantGraphResolveState match = matches.get(0);
        VariantGraphResolveMetadata matchMetadata = matches.get(0).getMetadata();

        if ((matchMetadata instanceof ConfigurationGraphResolveMetadata) &&
            ((ConfigurationGraphResolveMetadata) matchMetadata).isDeprecatedForConsumption()
        ) {
            DeprecationLogger.deprecateConfiguration(matchMetadata.getName())
                .forConsumption()
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(5, "dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
                .nagUser();
        }
        return new GraphVariantSelectionResult(ImmutableList.of(match), variantAware);
    }

    private ImmutableList<VariantGraphResolveState> filterVariantsByRequestedCapabilities(ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> explicitRequestedCapabilities, Collection<? extends VariantGraphResolveState> consumableVariants, boolean lenient) {
        if (consumableVariants.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<VariantGraphResolveState> builder = ImmutableList.builderWithExpectedSize(consumableVariants.size());
        boolean explicitlyRequested = !explicitRequestedCapabilities.isEmpty();
        ModuleIdentifier moduleId = targetComponent.getModuleVersionId().getModule();
        for (VariantGraphResolveState variant : consumableVariants) {
            CapabilitiesMetadata capabilitiesMetadata = variant.getCapabilities();
            List<? extends Capability> capabilities = capabilitiesMetadata.getCapabilities();
            MatchResult result;
            if (explicitlyRequested) {
                // some capabilities are explicitly required (in other words, we're not _necessarily_ looking for the default capability
                // so we need to filter the configurations
                result = providesAllCapabilities(targetComponent, explicitRequestedCapabilities, capabilities);
            } else {
                // we need to make sure the variants we consider provide the implicit capability
                result = containsImplicitCapability(capabilitiesMetadata, capabilities, moduleId.getGroup(), moduleId.getName());
            }
            if (result.matches) {
                if (lenient || result == MatchResult.EXACT_MATCH) {
                    builder.add(variant);
                }
            }
        }
        return builder.build();
    }

    /**
     * Determines if a producer variant provides all the requested capabilities. When doing so it does
     * NOT consider capability versions, as they will be used later in the engine during conflict resolution.
     */
    private MatchResult providesAllCapabilities(ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> explicitRequestedCapabilities, List<? extends Capability> providerCapabilities) {
        if (providerCapabilities.isEmpty()) {
            // producer doesn't declare anything, so we assume that it only provides the implicit capability
            if (explicitRequestedCapabilities.size() == 1) {
                Capability requested = explicitRequestedCapabilities.iterator().next();
                ModuleVersionIdentifier mvi = targetComponent.getModuleVersionId();
                if (requested.getGroup().equals(mvi.getGroup()) && requested.getName().equals(mvi.getName())) {
                    return MatchResult.EXACT_MATCH;
                }
            }
        }
        for (Capability requested : explicitRequestedCapabilities) {
            String requestedGroup = requested.getGroup();
            String requestedName = requested.getName();
            boolean found = false;
            for (Capability provided : providerCapabilities) {
                if (provided.getGroup().equals(requestedGroup) && provided.getName().equals(requestedName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return MatchResult.NO_MATCH;
            }
        }
        boolean exactMatch = explicitRequestedCapabilities.size() == providerCapabilities.size();
        return exactMatch ? MatchResult.EXACT_MATCH : MatchResult.MATCHES_ALL;
    }

    private MatchResult containsImplicitCapability(CapabilitiesMetadata capabilitiesMetadata, Collection<? extends Capability> capabilities, String group, String name) {
        if (fastContainsImplicitCapability((CapabilitiesMetadataInternal) capabilitiesMetadata, capabilities)) {
            // An empty capability list means that it's an implicit capability only
            return MatchResult.EXACT_MATCH;
        }
        for (Capability capability : capabilities) {
            capability = unwrap(capability);
            if (group.equals(capability.getGroup()) && name.equals(capability.getName())) {
                boolean exactMatch = capabilities.size() == 1;
                return exactMatch ? MatchResult.EXACT_MATCH : MatchResult.MATCHES_ALL;
            }
        }
        return MatchResult.NO_MATCH;
    }

    private boolean fastContainsImplicitCapability(CapabilitiesMetadataInternal capabilitiesMetadata, Collection<? extends Capability> capabilities) {
        return capabilities.isEmpty() || capabilitiesMetadata.isShadowedCapabilityOnly();
    }

    private Capability unwrap(Capability capability) {
        if (capability instanceof ShadowedCapability) {
            return ((ShadowedCapability) capability).getShadowedCapability();
        }
        return capability;
    }

    private enum MatchResult {
        NO_MATCH(false),
        MATCHES_ALL(true),
        EXACT_MATCH(true);

        private final boolean matches;

        MatchResult(boolean match) {
            this.matches = match;
        }
    }

    private static class TraceDiscardedConfigurations implements AttributeMatchingExplanationBuilder {
        private final Set<HasAttributes> discarded = Sets.newHashSet();

        @Override
        public boolean canSkipExplanation() {
            return false;
        }

        @Override
        public <T extends HasAttributes> void candidateDoesNotMatchAttributes(T candidate, AttributeContainerInternal requested) {
            recordDiscardedCandidate(candidate);
        }

        public <T extends HasAttributes> void recordDiscardedCandidate(T candidate) {
            discarded.add(candidate);
        }

        @Override
        public <T extends HasAttributes> void candidateAttributeDoesNotMatch(T candidate, Attribute<?> attribute, Object requestedValue, AttributeValue<?> candidateValue) {
            recordDiscardedCandidate(candidate);
        }
    }
}
