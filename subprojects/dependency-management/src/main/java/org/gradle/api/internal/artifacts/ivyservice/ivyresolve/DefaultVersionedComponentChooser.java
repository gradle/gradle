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

import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ComponentSelectionInternal;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.DefaultComponentSelection;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.ComponentSelectionContext;
import org.gradle.internal.rules.SpecRuleAction;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class DefaultVersionedComponentChooser implements VersionedComponentChooser {
    private final ComponentSelectionRulesProcessor rulesProcessor = new ComponentSelectionRulesProcessor();
    private final VersionComparator versionComparator;
    private final ComponentSelectionRulesInternal componentSelectionRules;

    DefaultVersionedComponentChooser(VersionComparator versionComparator, ComponentSelectionRulesInternal componentSelectionRules) {
        this.versionComparator = versionComparator;
        this.componentSelectionRules = componentSelectionRules;
    }

    public ComponentResolveMetadata selectNewestComponent(ComponentResolveMetadata one, ComponentResolveMetadata two) {
        if (one == null || two == null) {
            return two == null ? one : two;
        }

        int comparison = versionComparator.compare(new VersionInfo(one.getId().getVersion()), new VersionInfo(two.getId().getVersion()));

        if (comparison == 0) {
            if (isMissingModuleDescriptor(one) && !isMissingModuleDescriptor(two)) {
                return two;
            }
            return one;
        }

        return comparison < 0 ? two : one;
    }

    private boolean isMissingModuleDescriptor(ComponentResolveMetadata componentResolveMetadata) {
        return componentResolveMetadata.isMissing();
    }

    public void selectNewestMatchingComponent(Collection<? extends ModuleComponentResolveState> versions, ComponentSelectionContext result, VersionSelector requestedVersionMatcher) {
        Collection<SpecRuleAction<? super ComponentSelection>> rules = componentSelectionRules.getRules();

        // Loop over all listed versions, sorted by LATEST first
        for (ModuleComponentResolveState candidate : sortLatestFirst(versions)) {
            MetadataProvider metadataProvider = createMetadataProvider(candidate);

            boolean versionMatches = versionMatches(requestedVersionMatcher, candidate, metadataProvider);
            if (metadataIsNotUsable(result, metadataProvider)) {
                return;
            }

            String version = candidate.getVersion().getSource();
            if (!versionMatches) {
                result.notMatched(version);
                continue;
            }

            ModuleComponentIdentifier candidateIdentifier = candidate.getId();
            if (!isRejectedByRules(candidateIdentifier, rules, metadataProvider)) {
                result.matches(candidateIdentifier);
                return;
            }

            // Mark this version as rejected
            result.rejected(version);
            if (requestedVersionMatcher.matchesUniqueVersion()) {
                // Only consider one candidate, because matchesUniqueVersion means that there's no ambiguity on the version number
                break;
            }
        }
        // if we reach this point, no match was found, either because there are no versions matching the selector
        // or all of them were rejected
        result.noMatchFound();
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
    private boolean metadataIsNotUsable(ComponentSelectionContext result, MetadataProvider metadataProvider) {
        if (!metadataProvider.isUsable()) {
            applyTo(metadataProvider, result);
            return true;
        }
        return false;
    }

    private static MetadataProvider createMetadataProvider(ModuleComponentResolveState candidate) {
        return new MetadataProvider(candidate);
    }

    private static void applyTo(MetadataProvider provider, ComponentSelectionContext result) {
        BuildableModuleComponentMetaDataResolveResult metaDataResult = provider.getResult();
        switch (metaDataResult.getState()) {
            case Unknown:
                // For example, when using a local access to resolve something remote
                result.noMatchFound();
                break;
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

    public boolean isRejectedComponent(ModuleComponentIdentifier candidateIdentifier, MetadataProvider metadataProvider) {
        return isRejectedByRules(candidateIdentifier, componentSelectionRules.getRules(), metadataProvider);
    }

    private boolean isRejectedByRules(ModuleComponentIdentifier candidateIdentifier, Collection<SpecRuleAction<? super ComponentSelection>> rules, MetadataProvider metadataProvider) {
        ComponentSelectionInternal selection = new DefaultComponentSelection(candidateIdentifier);
        rulesProcessor.apply(selection, rules, metadataProvider);
        return selection.isRejected();
    }

    private List<ModuleComponentResolveState> sortLatestFirst(Collection<? extends ModuleComponentResolveState> listing) {
        return CollectionUtils.sort(listing, Collections.reverseOrder(versionComparator));
    }
}
