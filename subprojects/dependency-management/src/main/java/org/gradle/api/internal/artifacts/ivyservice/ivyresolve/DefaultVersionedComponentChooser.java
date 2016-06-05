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

import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ComponentSelectionInternal;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.DefaultComponentSelection;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.result.BuildableComponentSelectionResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.rules.SpecRuleAction;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class DefaultVersionedComponentChooser implements VersionedComponentChooser {
    private final ComponentSelectionRulesProcessor rulesProcessor = new ComponentSelectionRulesProcessor();
    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final ComponentSelectionRulesInternal componentSelectionRules;

    DefaultVersionedComponentChooser(VersionComparator versionComparator, VersionSelectorScheme versionSelectorScheme, ComponentSelectionRulesInternal componentSelectionRules) {
        this.versionComparator = versionComparator;
        this.versionSelectorScheme = versionSelectorScheme;
        this.componentSelectionRules = componentSelectionRules;
    }

    public ComponentResolveMetadata selectNewestComponent(ComponentResolveMetadata one, ComponentResolveMetadata two) {
        if (one == null || two == null) {
            return two == null ? one : two;
        }

        int comparison = versionComparator.compare(new VersionInfo(one.getId().getVersion()), new VersionInfo(two.getId().getVersion()));

        if (comparison == 0) {
            if (isGeneratedModuleDescriptor(one) && !isGeneratedModuleDescriptor(two)) {
                return two;
            }
            return one;
        }

        return comparison < 0 ? two : one;
    }

    private boolean isGeneratedModuleDescriptor(ComponentResolveMetadata componentResolveMetadata) {
        return componentResolveMetadata.isGenerated();
    }

    public void selectNewestMatchingComponent(Collection<? extends ModuleComponentResolveState> versions, BuildableComponentSelectionResult result, ModuleVersionSelector requested) {
        VersionSelector requestedVersion = versionSelectorScheme.parseSelector(requested.getVersion());
        Collection<SpecRuleAction<? super ComponentSelection>> rules = componentSelectionRules.getRules();

        for (ModuleComponentResolveState candidate : sortLatestFirst(versions)) {
            MetadataProvider metadataProvider = new MetadataProvider(candidate);

            boolean versionMatches = versionMatches(requestedVersion, candidate, metadataProvider);
            if (!metadataProvider.isUsable()) {
                applyTo(metadataProvider, result);
                return;
            }
            if (!versionMatches) {
                result.notMatched(candidate.getVersion());
                continue;
            }

            ModuleComponentIdentifier candidateIdentifier = candidate.getId();
            boolean accepted = !isRejectedByRules(candidateIdentifier, rules, metadataProvider);
            if (!metadataProvider.isUsable()) {
                applyTo(metadataProvider, result);
                return;
            }

            if (accepted) {
                result.matches(candidateIdentifier);
                return;
            }

            result.rejected(candidate.getVersion());
            if (requestedVersion.matchesUniqueVersion()) {
                // Only consider one candidate
                break;
            }
        }

        result.noMatchFound();
    }

    private void applyTo(MetadataProvider provider, BuildableComponentSelectionResult result) {
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

    private boolean versionMatches(VersionSelector selector, ModuleComponentResolveState component, MetadataProvider metadataProvider) {
        if (selector.requiresMetadata()) {
            if (!metadataProvider.resolve()) {
                return false;
            }
            return selector.accept(metadataProvider.getComponentMetadata());
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
