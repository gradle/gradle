/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.ComponentSelectionInternal;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.DefaultComponentSelection;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.resolve.RejectedByAttributesVersion;
import org.gradle.internal.resolve.RejectedByRuleVersion;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.ComponentSelectionContext;
import org.gradle.internal.rules.SpecRuleAction;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class DefaultVersionedComponentChooser implements VersionedComponentChooser {
    private final ComponentSelectionRulesProcessor rulesProcessor = new ComponentSelectionRulesProcessor();
    private final VersionComparator versionComparator;
    private final ComponentSelectionRulesInternal componentSelectionRules;
    private final VersionParser versionParser;
    private final AttributesSchemaInternal attributesSchema;

    DefaultVersionedComponentChooser(VersionComparator versionComparator, VersionParser versionParser, ComponentSelectionRulesInternal componentSelectionRules, AttributesSchema attributesSchema) {
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
        this.componentSelectionRules = componentSelectionRules;
        this.attributesSchema = (AttributesSchemaInternal) attributesSchema;
    }

    @Override
    public ComponentGraphResolveMetadata selectNewestComponent(@Nullable ModuleComponentGraphResolveMetadata one, @Nullable ModuleComponentGraphResolveMetadata two) {
        if (one == null || two == null) {
            return two == null ? one : two;
        }

        int comparison = versionComparator.compare(new VersionInfo(versionParser.transform(one.getModuleVersionId().getVersion())), new VersionInfo(versionParser.transform(two.getModuleVersionId().getVersion())));

        if (comparison == 0) {
            if (isMissingModuleDescriptor(one) && !isMissingModuleDescriptor(two)) {
                return two;
            }
            return one;
        }

        return comparison < 0 ? two : one;
    }

    private boolean isMissingModuleDescriptor(ModuleComponentGraphResolveMetadata metadata) {
        return metadata.isMissing();
    }

    @Override
    public void selectNewestMatchingComponent(Collection<? extends ModuleComponentResolveState> versions, ComponentSelectionContext result, VersionSelector requestedVersionMatcher, VersionSelector rejectedVersionSelector, ImmutableAttributes consumerAttributes) {
        Collection<SpecRuleAction<? super ComponentSelection>> rules = componentSelectionRules.getRules();

        // Loop over all listed versions, sorted by LATEST first
        List<ModuleComponentResolveState> resolveStates = sortLatestFirst(versions);
        Action<? super ArtifactResolutionDetails> contentFilter = result.getContentFilter();
        for (ModuleComponentResolveState candidate : resolveStates) {
            if (contentFilter != null) {
                DynamicArtifactResolutionDetails details = new DynamicArtifactResolutionDetails(candidate);
                contentFilter.execute(details);
                if (!details.found) {
                    continue;
                }
            }

            DefaultMetadataProvider metadataProvider = createMetadataProvider(candidate);
            boolean versionMatches = versionMatches(requestedVersionMatcher, candidate, metadataProvider);
            if (metadataIsNotUsable(result, metadataProvider)) {
                return;
            }

            ModuleComponentIdentifier candidateId = candidate.getId();
            if (!versionMatches) {
                result.notMatched(candidateId, requestedVersionMatcher);
                continue;
            }

            RejectedByAttributesVersion maybeRejectByAttributes = tryRejectByAttributes(candidateId, metadataProvider, consumerAttributes);
            if (maybeRejectByAttributes != null) {
                result.doesNotMatchConsumerAttributes(maybeRejectByAttributes);
            } else if (isRejectedBySelector(candidateId, rejectedVersionSelector)) {
                // Mark this version as rejected
                result.rejectedBySelector(candidateId, rejectedVersionSelector);
            } else {
                RejectedByRuleVersion rejectedByRules = isRejectedByRule(candidateId, rules, metadataProvider);
                if (rejectedByRules != null) {
                    // Mark this version as rejected
                    result.rejectedByRule(rejectedByRules);

                    if (requestedVersionMatcher.matchesUniqueVersion()) {
                        // Only consider one candidate, because matchesUniqueVersion means that there's no ambiguity on the version number
                        break;
                    }
                } else {
                    result.matches(candidateId);
                    return;
                }
            }
        }

        // if we reach this point, no match was found, either because there are no versions matching the selector
        // or all of them were rejected
        result.noMatchFound();
    }

    @Nullable
    private RejectedByAttributesVersion tryRejectByAttributes(ModuleComponentIdentifier id, MetadataProvider provider, ImmutableAttributes consumerAttributes) {
        if (consumerAttributes.isEmpty()) {
            return null;
        }

        // At this point, we need the component metadata, because it may declare attributes that are needed for matching
        // Component metadata may not necessarily hit the network if there is a custom component metadata supplier
        ComponentMetadata componentMetadata = provider.getComponentMetadata();
        if (componentMetadata != null) {
            AttributeContainerInternal attributes = (AttributeContainerInternal) componentMetadata.getAttributes();
            boolean matching = attributesSchema.matcher().isMatching(attributes, consumerAttributes);
            if (!matching) {
                return new RejectedByAttributesVersion(id, attributesSchema.matcher().describeMatching(attributes, consumerAttributes));
            }
        }
        return null;
    }

    /**
     * This method checks if the metadata provider already knows that metadata for this version is not usable.
     * If that's the case it means it's not necessary to perform more checks for this version, because we already
     * know it's broken in some way.
     *
     * @param result where to notify that metadata is broken, if broken
     * @param metadataProvider the metadata provider
     * @return true if metadata is not usable
     */
    private boolean metadataIsNotUsable(ComponentSelectionContext result, DefaultMetadataProvider metadataProvider) {
        if (!metadataProvider.isUsable()) {
            applyTo(metadataProvider, result);
            return true;
        }
        return false;
    }

    private static DefaultMetadataProvider createMetadataProvider(ModuleComponentResolveState candidate) {
        return new DefaultMetadataProvider(candidate);
    }

    private static void applyTo(DefaultMetadataProvider provider, ComponentSelectionContext result) {
        BuildableModuleComponentMetaDataResolveResult<?> metaDataResult = provider.getResult();
        switch (metaDataResult.getState()) {
            case Unknown:
            case Missing:
                result.noMatchFound();
                break;
            case Failed:
                result.failed(metaDataResult.getFailure());
                break;
            default:
                throw new IllegalStateException("Unexpected meta-data resolution result.");
        }
    }

    private static boolean versionMatches(VersionSelector selector, ModuleComponentResolveState component, MetadataProvider metadataProvider) {
        if (selector.requiresMetadata()) {
            ComponentMetadata componentMetadata = metadataProvider.getComponentMetadata();
            return componentMetadata != null && selector.accept(componentMetadata);
        } else {
            return selector.accept(component.getVersion());
        }
    }

    @Override
    public RejectedByRuleVersion isRejectedComponent(ModuleComponentIdentifier candidateIdentifier, MetadataProvider metadataProvider) {
        return isRejectedByRule(candidateIdentifier, componentSelectionRules.getRules(), metadataProvider);
    }

    @Nullable
    private RejectedByRuleVersion isRejectedByRule(ModuleComponentIdentifier candidateIdentifier, Collection<SpecRuleAction<? super ComponentSelection>> rules, MetadataProvider metadataProvider) {
        ComponentSelectionInternal selection = new DefaultComponentSelection(candidateIdentifier, metadataProvider);
        rulesProcessor.apply(selection, rules, metadataProvider);
        if (selection.isRejected()) {
            return new RejectedByRuleVersion(candidateIdentifier, selection.getRejectionReason());
        }
        return null;
    }

    private boolean isRejectedBySelector(ModuleComponentIdentifier candidateIdentifier, VersionSelector rejectedVersionSelector) {
        return rejectedVersionSelector != null && rejectedVersionSelector.accept(candidateIdentifier.getVersion());
    }

    private List<ModuleComponentResolveState> sortLatestFirst(Collection<? extends ModuleComponentResolveState> listing) {
        return CollectionUtils.sort(listing, Collections.reverseOrder(versionComparator));
    }

    private static class DynamicArtifactResolutionDetails implements ArtifactResolutionDetails {
        private final ModuleComponentResolveState resolveState;
        boolean found = true;

        public DynamicArtifactResolutionDetails(ModuleComponentResolveState resolveState) {
            this.resolveState = resolveState;
        }

        @Override
        public ModuleIdentifier getModuleId() {
            return resolveState.getId().getModuleIdentifier();
        }

        @Override
        @Nullable
        public ModuleComponentIdentifier getComponentId() {
            return resolveState.getId();
        }

        @Override
        public boolean isVersionListing() {
            return false;
        }

        @Override
        public void notFound() {
            found = false;
        }
    }
}
