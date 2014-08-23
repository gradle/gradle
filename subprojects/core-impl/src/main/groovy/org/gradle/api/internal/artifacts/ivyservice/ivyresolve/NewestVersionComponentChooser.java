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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ComponentSelectionInternal;
import org.gradle.api.internal.artifacts.DefaultComponentSelection;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.metadata.ExternalComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData;

import java.util.Collections;
import java.util.List;

class NewestVersionComponentChooser implements ComponentChooser {
    private final VersionMatcher versionMatcher;
    private final LatestStrategy latestStrategy;
    private final ComponentSelectionRulesInternal versionSelectionRules;

    NewestVersionComponentChooser(LatestStrategy latestStrategy, VersionMatcher versionMatcher, ComponentSelectionRulesInternal versionSelectionRules) {
        this.latestStrategy = latestStrategy;
        this.versionMatcher = versionMatcher;
        this.versionSelectionRules = versionSelectionRules;
    }

    public boolean canSelectMultipleComponents(ModuleVersionSelector selector) {
        return versionMatcher.isDynamic(selector.getVersion());
    }

    public ExternalComponentMetaData choose(ExternalComponentMetaData one, ExternalComponentMetaData two) {
        if (one == null || two == null) {
            return two == null ? one : two;
        }

        int comparison = latestStrategy.compare(new VersionInfo(one.getId().getVersion()), new VersionInfo(two.getId().getVersion()));

        if (comparison == 0) {
            if (isGeneratedModuleDescriptor(one) && !isGeneratedModuleDescriptor(two)) {
                return two;
            }
            return one;
        }

        return comparison < 0 ? two : one;
    }

    private boolean isGeneratedModuleDescriptor(ExternalComponentMetaData externalComponentMetaData) {
        return externalComponentMetaData.isGenerated();
    }

    public ModuleComponentIdentifier choose(ModuleVersionListing versions, DependencyMetaData dependency, ModuleComponentRepositoryAccess moduleAccess) {
        boolean versionMatcherRequiresMetadata = versionMatcher.needModuleMetadata(dependency.getRequested().getVersion());
        ModuleVersionSelector requested = dependency.getRequested();
        for (Versioned candidate : sortLatestFirst(versions)) {
            ModuleComponentIdentifier candidateIdentifier = DefaultModuleComponentIdentifier.newId(requested.getGroup(), requested.getName(), candidate.getVersion());

            if (versionMatcherRequiresMetadata) {
                MutableModuleVersionMetaData metaData = resolveComponentMetaData(dependency, candidate, moduleAccess);
                if (!versionMatcher.accept(requested.getVersion(), metaData)) {
                    continue;
                }
            } else {
                if (!versionMatcher.accept(requested.getVersion(), candidate.getVersion())) {
                    continue;
                }
            }

            if (isRejectedByRules(candidateIdentifier, dependency, moduleAccess)) {
                continue;
            }

            return candidateIdentifier;
        }
        return null;
    }

    public boolean isRejectedByRules(ModuleComponentIdentifier candidateIdentifier, DependencyMetaData dependency, ModuleComponentRepositoryAccess moduleAccess) {
        ComponentSelectionInternal selection = new DefaultComponentSelection(dependency, candidateIdentifier);
        versionSelectionRules.apply(selection, moduleAccess);
        return selection.isRejected();
    }

    private MutableModuleVersionMetaData resolveComponentMetaData(DependencyMetaData dependency, Versioned candidate, ModuleComponentRepositoryAccess moduleAccess) {
        ModuleVersionSelector selector = dependency.getRequested();
        ModuleComponentIdentifier candidateId = DefaultModuleComponentIdentifier.newId(selector.getGroup(), selector.getName(), candidate.getVersion());
        DependencyMetaData moduleVersionDependency = dependency.withRequestedVersion(candidate.getVersion());
        BuildableModuleVersionMetaDataResolveResult descriptorResult = new DefaultBuildableModuleVersionMetaDataResolveResult();
        moduleAccess.resolveComponentMetaData(moduleVersionDependency, candidateId, descriptorResult);
        return descriptorResult.getMetaData();
    }

    private List<Versioned> sortLatestFirst(ModuleVersionListing listing) {
        List<Versioned> sorted = latestStrategy.sort(listing.getVersions());
        Collections.reverse(sorted);
        return sorted;
    }
}
