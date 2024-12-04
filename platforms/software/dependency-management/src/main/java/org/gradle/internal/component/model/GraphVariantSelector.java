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
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorInternal;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

    private final AttributeSchemaServices attributeSchemaServices;
    private final ResolutionFailureHandler failureHandler;

    public GraphVariantSelector(
        AttributeSchemaServices attributeSchemaServices,
        ResolutionFailureHandler failureHandler
    ) {
        this.attributeSchemaServices = attributeSchemaServices;
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
        Set<CapabilitySelector> capabilitySelectors,
        ComponentGraphResolveState targetComponentState,
        ImmutableAttributesSchema consumerSchema,
        List<IvyArtifactName> requestedArtifacts
    ) {
        VariantGraphResolveState result = selectByAttributeMatchingLenient(
            consumerAttributes,
            capabilitySelectors,
            targetComponentState,
            consumerSchema,
            requestedArtifacts
        );

        if (result == null) {
            ComponentGraphResolveMetadata targetComponent = targetComponentState.getMetadata();
            AttributeMatcher attributeMatcher = attributeSchemaServices.getMatcher(consumerSchema, targetComponent.getAttributesSchema());
            GraphSelectionCandidates candidates = targetComponentState.getCandidatesForGraphVariantSelection();
            throw failureHandler.noCompatibleVariantsFailure(attributeMatcher, targetComponentState, consumerAttributes, capabilitySelectors, candidates);
        }

        return result;
    }

    @Nullable
    public VariantGraphResolveState selectByAttributeMatchingLenient(
        ImmutableAttributes consumerAttributes,
        Set<CapabilitySelector> capabilitySelectors,
        ComponentGraphResolveState targetComponentState,
        ImmutableAttributesSchema consumerSchema,
        List<IvyArtifactName> requestedArtifacts
    ) {
        List<? extends VariantGraphResolveState> candidates = targetComponentState.getCandidatesForGraphVariantSelection().getVariantsForAttributeMatching();
        assert !candidates.isEmpty();

        ImmutableAttributesSchema producerSchema = targetComponentState.getMetadata().getAttributesSchema();
        AttributeMatcher attributeMatcher = attributeSchemaServices.getMatcher(consumerSchema, producerSchema);

        // Find all variants that match the requested capabilities
        ImmutableList<VariantGraphResolveState> variantsProvidingRequestedCapabilities = filterVariantsByRequestedCapabilities(targetComponentState, capabilitySelectors, candidates, true);
        if (variantsProvidingRequestedCapabilities.isEmpty()) {
            throw failureHandler.noVariantsWithMatchingCapabilitiesFailure(attributeMatcher, targetComponentState, consumerAttributes, capabilitySelectors, candidates);
        }

        // Perform attribute matching on the candidates satisfying our capability selectors
        List<VariantGraphResolveState> matches = attributeMatcher.matchMultipleCandidates(variantsProvidingRequestedCapabilities, consumerAttributes);
        if (matches.size() < 2) {
            return zeroOrSingleVariant(matches);
        }

        // There's an ambiguity, but we may have several variants matching the requested capabilities.
        // Try to find a set of candidates that _strictly_ match the capability selectors.
        matches = filterVariantsByRequestedCapabilities(targetComponentState, capabilitySelectors, matches, false);
        if (matches.size() < 2) {
            return zeroOrSingleVariant(matches);
        }

        // there are still more than one candidate, but this time we know only a subset strictly matches the required attributes
        // so we perform another round of selection on the remaining candidates
        matches = attributeMatcher.matchMultipleCandidates(matches, consumerAttributes);
        if (matches.size() < 2) {
            return zeroOrSingleVariant(matches);
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
                if (sameClassifier.size() < 2) {
                    return zeroOrSingleVariant(sameClassifier);
                }
            }
        }

        throw failureHandler.ambiguousVariantsFailure(attributeMatcher, targetComponentState, consumerAttributes, capabilitySelectors, matches);
    }

    /**
     * Select the legacy variant from the target component.
     */
    public VariantGraphResolveState selectLegacyVariant(ImmutableAttributes consumerAttributes, ComponentGraphResolveState targetComponentState, ImmutableAttributesSchema consumerSchema, ResolutionFailureHandler failureHandler) {
        VariantGraphResolveState conf = targetComponentState.getCandidatesForGraphVariantSelection().getLegacyVariant();
        if (conf == null) {
            // We wanted to do variant matching, but there were no variants in the target component.
            // So, we fell back to looking for the legacy (`default`) configuration, but it didn't exist.
            // So, there are no variants to select from, and selection fails here.
            throw failureHandler.noVariantsFailure(targetComponentState, consumerAttributes);
        }

        validateVariantAttributes(conf, consumerAttributes, targetComponentState, consumerSchema);
        maybeEmitConsumptionDeprecation(conf);
        return conf;
    }

    /**
     * Select the variant that is identified by the given configuration name.
     */
    public VariantGraphResolveState selectVariantByConfigurationName(String name, ImmutableAttributes consumerAttributes, ComponentGraphResolveState targetComponentState, ImmutableAttributesSchema consumerSchema) {
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
        ImmutableAttributesSchema consumerSchema
    ) {
        ComponentGraphResolveMetadata targetComponent = targetComponentState.getMetadata();
        AttributeMatcher attributeMatcher = attributeSchemaServices.getMatcher(consumerSchema, targetComponent.getAttributesSchema());

        if (!consumerAttributes.isEmpty() && !conf.getAttributes().isEmpty()) {
            // Need to validate that the selected configuration still matches the consumer attributes
            if (!attributeMatcher.isMatchingCandidate(conf.getAttributes(), consumerAttributes)) {
                throw failureHandler.configurationNotCompatibleFailure(attributeMatcher, targetComponentState, conf, consumerAttributes, conf.getCapabilities());
            }
        }
    }

    private static List<VariantGraphResolveState> findVariantsProvidingExactlySameClassifier(List<VariantGraphResolveState> matches, String classifier) {
        List<VariantGraphResolveState> sameClassifier = Collections.emptyList();
        // let's see if we can find a single variant which has exactly the requested artifacts
        for (VariantGraphResolveState match : matches) {
            if (variantProvidesClassifier(match, classifier)) {
                if (sameClassifier == Collections.EMPTY_LIST) {
                    sameClassifier = Collections.singletonList(match);
                } else {
                    sameClassifier = Lists.newArrayList(sameClassifier);
                    sameClassifier.add(match);
                }
            }
        }
        return sameClassifier;
    }

    private static boolean variantProvidesClassifier(VariantGraphResolveState variant, String classifier) {
        Set<? extends VariantResolveMetadata> artifactSets = variant.prepareForArtifactResolution().getArtifactVariants();
        for (VariantResolveMetadata artifactSet : artifactSets) {
            if (artifactSetStrictlyProvidesClassifier(artifactSet, classifier)) {
                return true;
            }
        }

        return false;
    }

    private static boolean artifactSetStrictlyProvidesClassifier(VariantResolveMetadata artifactSet, String classifier) {
        List<? extends ComponentArtifactMetadata> artifacts = artifactSet.getArtifacts();
        if (artifacts.size() != 1) {
            return false;
        }

        ComponentArtifactMetadata componentArtifactMetadata = artifacts.get(0);
        if (!(componentArtifactMetadata instanceof ModuleComponentArtifactMetadata)) {
            return false;
        }

        return classifier.equals(componentArtifactMetadata.getName().getClassifier());
    }

    @Nullable
    private static VariantGraphResolveState zeroOrSingleVariant(List<VariantGraphResolveState> matches) {
        if (matches.isEmpty()) {
            return null;
        }

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

    private static ImmutableList<VariantGraphResolveState> filterVariantsByRequestedCapabilities(
        ComponentGraphResolveState targetComponent,
        Set<CapabilitySelector> capabilitySelectors,
        Collection<? extends VariantGraphResolveState> consumableVariants,
        boolean lenient
    ) {
        ImmutableCapability defaultCapability = targetComponent.getDefaultCapability();
        boolean explicitlyRequested = !capabilitySelectors.isEmpty();
        ImmutableList.Builder<VariantGraphResolveState> builder = ImmutableList.builderWithExpectedSize(consumableVariants.size());

        for (VariantGraphResolveState variant : consumableVariants) {
            ImmutableCapabilities capabilities = variant.getCapabilities();
            if (explicitlyRequested) {
                // Capabilities were explicitly requested.
                // Require the variants capabilities match all requested selectors.
                if (matchesCapabilitySelectors(capabilitySelectors, capabilities, defaultCapability, lenient)) {
                    builder.add(variant);
                }
            } else {
                // No capabilities were explicitly requested.
                // Default to requiring the implicit capability as specified by the component.
                if (containsImplicitCapability(capabilities, defaultCapability, lenient)) {
                    builder.add(variant);
                }
            }
        }

        return builder.build();
    }

    /**
     * Determines if the provided capabilities contains the implicit capability of the component.
     *
     * @param capabilities The capabilities to check
     * @param implicitCapability The implicit capability of the component
     * @param lenient If false, the method will return fail if the component has more capabilities than the implicit capability.
     *
     * @return true if the capabilities contain the implicit capability
     */
    private static boolean containsImplicitCapability(
        ImmutableCapabilities capabilities,
        ImmutableCapability implicitCapability,
        boolean lenient
    ) {
        // If the variant declares no capabilities, it inherits the implicit capability of the component.
        if (capabilities.isEmpty()) {
            return true;
        }

        // If the variant contains only the shadowed capability, it's an implicit capability.
        // TODO: Why do we not check the content of the shadowed capability?
        ImmutableSet<ImmutableCapability> capabilitiesSet = capabilities.asSet();
        if (capabilitiesSet.size() == 1 && capabilitiesSet.iterator().next() instanceof ShadowedCapability) {
            return true;
        }

        // Otherwise, check the declared capabilities.
        for (Capability capability : capabilities) {
            if (capability instanceof ShadowedCapability) {
                capability = ((ShadowedCapability) capability).getShadowedCapability();
            }
            if (implicitCapability.getGroup().equals(capability.getGroup()) && implicitCapability.getName().equals(capability.getName())) {
                return lenient || capabilities.asSet().size() == 1;
            }
        }

        return false;
    }

    /**
     * Determines if the provided capabilities matches all the provided selectors.
     *
     * @param capabilitySelectors The selectors to check against
     * @param capabilities The capabilities to check
     * @param implicitCapability The implicit capability of the component
     * @param lenient If false, the method will return fail if there are extra capabilities not explicitly requested.
     *
     * @return true if the capabilities match the selectors
     */
    private static boolean matchesCapabilitySelectors(
        Set<CapabilitySelector> capabilitySelectors,
        ImmutableCapabilities capabilities,
        ImmutableCapability implicitCapability,
        boolean lenient
    ) {
        if (capabilities.isEmpty()) {
            // The variant does not declare any capabilities.
            // Use the component's implicit capability by default.
            capabilities = ImmutableCapabilities.of(implicitCapability);
        }

        // Check that every selector matches at least one capability
        for (CapabilitySelector selector : capabilitySelectors) {
            if (noMatchingCapability(selector, capabilities, implicitCapability)) {
                return false;
            }
        }

        // If lenient, we allow extra capabilities not explicitly requested
        if (lenient) {
            return true;
        }

        // Check that every capability matches at least one selector
        for (CapabilityInternal capability : capabilities) {
            if (noMatchingSelector(capability, capabilitySelectors, implicitCapability)) {
                return false;
            }
        }

        return true;
    }

    private static boolean noMatchingCapability(
        CapabilitySelector selector,
        ImmutableCapabilities capabilities,
        ImmutableCapability implicitCapability
    ) {
        for (CapabilityInternal capability : capabilities) {
            if (matches(selector, capability, implicitCapability)) {
                return false;
            }
        }

        return true;
    }

    private static boolean noMatchingSelector(
        CapabilityInternal capability,
        Set<CapabilitySelector> selectors,
        ImmutableCapability implicitCapability
    ) {
        for (CapabilitySelector selector : selectors) {
            if (matches(selector, capability, implicitCapability)) {
                return false;
            }
        }

        return true;
    }

    private static boolean matches(
        CapabilitySelector selector,
        CapabilityInternal capability,
        ImmutableCapability implicitCapability
    ) {
        CapabilitySelectorInternal internalSelector = (CapabilitySelectorInternal) selector;
        return internalSelector.matches(capability.getGroup(), capability.getName(), implicitCapability);
    }

}
