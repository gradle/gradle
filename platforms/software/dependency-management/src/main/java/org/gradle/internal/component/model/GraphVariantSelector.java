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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Uses attribute matching to select a list of one or more variants for a component in a graph
 * (in practice, this should be only contain single variant).
 *
 * This class is intentionally named similarly to {@link ArtifactVariantSelector}, as it has a
 * similar purpose.  An instance of {@link ResolutionFailureHandler} is injected in the constructor
 * to allow the caller to handle failures in a consistent way - all matching failures should be reported via
 * calls to that instance.
 */
public class GraphVariantSelector {
    private final ResolutionFailureHandler failureHandler;

    public GraphVariantSelector(ResolutionFailureHandler failureHandler) {
        this.failureHandler = failureHandler;
    }

    /**
     * Returns the failure processor which must be used to report failures during variant selection.
     *
     * @return the failure processor
     */
    public ResolutionFailureHandler getFailureHandler() {
        return failureHandler;
    }

    public VariantGraphResolveState selectByAttributeMatching(
        ImmutableAttributes consumerAttributes,
        Collection<? extends Capability> explicitRequestedCapabilities,
        ComponentGraphResolveState targetComponentState,
        AttributesSchemaInternal consumerSchema,
        List<IvyArtifactName> requestedArtifacts
    ) {
        VariantGraphResolveState result = selectByAttributeMatchingLenient(
            consumerAttributes,
            explicitRequestedCapabilities, targetComponentState,
            consumerSchema,
            requestedArtifacts
        );

        if (result == null) {
            ComponentGraphResolveMetadata targetComponent = targetComponentState.getMetadata();
            AttributeMatcher attributeMatcher = consumerSchema.withProducer(targetComponent.getAttributesSchema());
            GraphSelectionCandidates candidates = targetComponentState.getCandidatesForGraphVariantSelection();
            throw failureHandler.noCompatibleVariantsFailure(attributeMatcher, targetComponentState, consumerAttributes, ImmutableCapabilities.of(explicitRequestedCapabilities), candidates);
        }

        return result;
    }

    @Nullable
    public VariantGraphResolveState selectByAttributeMatchingLenient(
        ImmutableAttributes consumerAttributes,
        Collection<? extends Capability> explicitRequestedCapabilities,
        ComponentGraphResolveState targetComponentState,
        AttributesSchemaInternal consumerSchema,
        List<IvyArtifactName> requestedArtifacts
    ) {
        GraphSelectionCandidates candidates = targetComponentState.getCandidatesForGraphVariantSelection();
        assert !candidates.getVariantsForAttributeMatching().isEmpty();

        ComponentGraphResolveMetadata targetComponent = targetComponentState.getMetadata();
        AttributeMatcher attributeMatcher = consumerSchema.withProducer(targetComponent.getAttributesSchema());

        List<? extends VariantGraphResolveState> allVariants = candidates.getVariantsForAttributeMatching();
        ImmutableList<VariantGraphResolveState> variantsProvidingRequestedCapabilities = filterVariantsByRequestedCapabilities(targetComponent, explicitRequestedCapabilities, allVariants, true);
        if (variantsProvidingRequestedCapabilities.isEmpty()) {
            throw failureHandler.noVariantsWithMatchingCapabilitiesFailure(attributeMatcher, targetComponentState, consumerAttributes, ImmutableCapabilities.of(explicitRequestedCapabilities), allVariants);
        }

        List<VariantGraphResolveState> matches = attributeMatcher.matchMultipleCandidates(variantsProvidingRequestedCapabilities, consumerAttributes, AttributeMatchingExplanationBuilder.logging());
        if (matches.size() > 1) {
            // there's an ambiguity, but we may have several variants matching the requested capabilities.
            // Here we're going to check if in the candidates, there's a single one _strictly_ matching the requested capabilities.
            List<VariantGraphResolveState> strictlyMatchingCapabilities = filterVariantsByRequestedCapabilities(targetComponent, explicitRequestedCapabilities, matches, false);
            if (strictlyMatchingCapabilities.size() == 1) {
                return singleVariant(strictlyMatchingCapabilities);
            } else if (strictlyMatchingCapabilities.size() > 1) {
                // there are still more than one candidate, but this time we know only a subset strictly matches the required attributes
                // so we perform another round of selection on the remaining candidates
                strictlyMatchingCapabilities = attributeMatcher.matchMultipleCandidates(strictlyMatchingCapabilities, consumerAttributes, AttributeMatchingExplanationBuilder.logging());
                if (strictlyMatchingCapabilities.size() == 1) {
                    return singleVariant(strictlyMatchingCapabilities);
                }
            }

            // TODO: Deprecate this.
            // Variant matching should not depend on requested artifacts, which are not part of the variant model.
            if (requestedArtifacts.size() == 1) {
                // Here, we know that the user requested a specific classifier. There may be multiple
                // candidate variants left, but maybe only one of them provides the classified artifact
                // we're looking for.
                String classifier = requestedArtifacts.get(0).getClassifier();
                if (classifier != null) {
                    List<VariantGraphResolveState> sameClassifier = findVariantsProvidingExactlySameClassifier(matches, classifier);
                    if (sameClassifier != null && sameClassifier.size() == 1) {
                        return singleVariant(sameClassifier);
                    }
                }
            }
        }

        if (matches.size() == 1) {
            return singleVariant(matches);
        }

        if (!matches.isEmpty()) {
            throw failureHandler.ambiguousVariantsFailure(attributeMatcher, targetComponentState, consumerAttributes, ImmutableCapabilities.of(explicitRequestedCapabilities), matches);
        }

        return null;
    }

    /**
     * Select the legacy variant from the target component.
     */
    public VariantGraphResolveState selectLegacyVariant(ImmutableAttributes consumerAttributes, ComponentGraphResolveState targetComponentState, AttributesSchemaInternal consumerSchema, ResolutionFailureHandler failureHandler) {
        VariantGraphResolveState conf = targetComponentState.getCandidatesForGraphVariantSelection().getLegacyVariant();
        if (conf == null) {
            // We wanted to do variant matching, but there were no variants in the target component.
            // So, we fell back to looking for the legacy (`default`) configuration, but it didn't exist.
            // So, there are no variants to select from, and selection fails here.
            throw failureHandler.noVariantsFailure(targetComponentState, consumerAttributes, ImmutableCapabilities.EMPTY);
        }

        validateVariantAttributes(conf, consumerAttributes, targetComponentState, consumerSchema);
        maybeEmitConsumptionDeprecation(conf);
        return conf;
    }

    /**
     * Select the variant that is identified by the given configuration name.
     */
    public VariantGraphResolveState selectVariantByConfigurationName(String name, ImmutableAttributes consumerAttributes, ComponentGraphResolveState targetComponentState, AttributesSchemaInternal consumerSchema) {
        VariantGraphResolveState conf = targetComponentState.getCandidatesForGraphVariantSelection().getVariantByConfigurationName(name);
        if (conf == null) {
            throw failureHandler.configurationDoesNotExistFailure(targetComponentState, name);
        }

        validateVariantAttributes(conf, consumerAttributes, targetComponentState, consumerSchema);
        maybeEmitConsumptionDeprecation(conf);
        return conf;
    }

    /**
     * Ensures the target variant matches the request attributes and is consumable. This needs to be called
     * for variants that are selected by means other than attribute matching.
     *
     * Note: This does not need to be called for variants selected via attribute matching, since
     * attribute matching ensures selected variants are compatible with the requested attributes.
     */
    private void validateVariantAttributes(
        VariantGraphResolveState conf,
        ImmutableAttributes consumerAttributes,
        ComponentGraphResolveState targetComponentState,
        AttributesSchemaInternal consumerSchema
    ) {
        ComponentGraphResolveMetadata targetComponent = targetComponentState.getMetadata();
        AttributeMatcher attributeMatcher = consumerSchema.withProducer(targetComponent.getAttributesSchema());

        if (!consumerAttributes.isEmpty() && !conf.getAttributes().isEmpty()) {
            // Need to validate that the selected configuration still matches the consumer attributes
            if (!attributeMatcher.isMatchingCandidate(conf.getAttributes(), consumerAttributes)) {
                throw failureHandler.configurationNotCompatibleFailure(attributeMatcher, targetComponentState, conf, consumerAttributes, conf.getCapabilities());
            }
        }
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
                    if (classifier.equals(componentArtifactMetadata.getName().getClassifier())) {
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

    private static VariantGraphResolveState singleVariant(List<VariantGraphResolveState> matches) {
        assert matches.size() == 1;
        VariantGraphResolveState match = matches.get(0);
        maybeEmitConsumptionDeprecation(match);
        return match;
    }

    private static void maybeEmitConsumptionDeprecation(VariantGraphResolveState targetVariant) {
        if (targetVariant.getMetadata().isDeprecated()) {
            DeprecationLogger.deprecateConfiguration(targetVariant.getName())
                .forConsumption()
                .willBecomeAnErrorInGradle9()
                .withUserManual("declaring_dependencies", "sec:deprecated-configurations")
                .nagUser();
        }
    }

    private ImmutableList<VariantGraphResolveState> filterVariantsByRequestedCapabilities(ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> explicitRequestedCapabilities, Collection<? extends VariantGraphResolveState> consumableVariants, boolean lenient) {
        if (consumableVariants.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<VariantGraphResolveState> builder = ImmutableList.builderWithExpectedSize(consumableVariants.size());
        boolean explicitlyRequested = !explicitRequestedCapabilities.isEmpty();
        ModuleIdentifier moduleId = targetComponent.getModuleVersionId().getModule();
        for (VariantGraphResolveState variant : consumableVariants) {
            ImmutableCapabilities capabilities = variant.getCapabilities();
            MatchResult result;
            if (explicitlyRequested) {
                // some capabilities are explicitly required (in other words, we're not _necessarily_ looking for the default capability
                // so we need to filter the variants
                result = providesAllCapabilities(targetComponent, explicitRequestedCapabilities, capabilities);
            } else {
                // we need to make sure the variants we consider provide the implicit capability
                result = containsImplicitCapability(capabilities, moduleId.getGroup(), moduleId.getName());
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
    private MatchResult providesAllCapabilities(ComponentGraphResolveMetadata targetComponent, Collection<? extends Capability> explicitRequestedCapabilities, ImmutableCapabilities providerCapabilities) {
        ImmutableSet<ImmutableCapability> providerCapabilitiesSet = providerCapabilities.asSet();
        if (providerCapabilitiesSet.isEmpty()) {
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
            for (Capability provided : providerCapabilitiesSet) {
                if (provided.getGroup().equals(requestedGroup) && provided.getName().equals(requestedName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return MatchResult.NO_MATCH;
            }
        }
        boolean exactMatch = explicitRequestedCapabilities.size() == providerCapabilitiesSet.size();
        return exactMatch ? MatchResult.EXACT_MATCH : MatchResult.MATCHES_ALL;
    }

    private MatchResult containsImplicitCapability(ImmutableCapabilities capabilities, String group, String name) {
        ImmutableSet<ImmutableCapability> capabilitiesSet = capabilities.asSet();
        if (fastContainsImplicitCapability(capabilitiesSet)) {
            // An empty capability list means that it's an implicit capability only
            return MatchResult.EXACT_MATCH;
        }
        for (Capability capability : capabilitiesSet) {
            capability = unwrap(capability);
            if (group.equals(capability.getGroup()) && name.equals(capability.getName())) {
                boolean exactMatch = capabilitiesSet.size() == 1;
                return exactMatch ? MatchResult.EXACT_MATCH : MatchResult.MATCHES_ALL;
            }
        }
        return MatchResult.NO_MATCH;
    }

    /**
     * A method that helps performance of selection by quickly checking if a
     * metadata container only contains a single, shadowed (the implicit) capability.
     *
     * @return {@code true} if the variant only contains the implicit capability
     */
    private static boolean fastContainsImplicitCapability(ImmutableSet<ImmutableCapability> capabilities) {
        if (capabilities.isEmpty()) {
            return true;
        }

        return capabilities.size() == 1 && capabilities.iterator().next() instanceof ShadowedCapability;
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
}
