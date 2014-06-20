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
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData;

import java.util.Collections;
import java.util.List;

class NewestVersionComponentChooser implements ComponentChooser {
    private final VersionMatcher versionMatcher;
    private final LatestStrategy latestStrategy;

    NewestVersionComponentChooser(LatestStrategy latestStrategy, VersionMatcher versionMatcher) {
        this.latestStrategy = latestStrategy;
        this.versionMatcher = versionMatcher;
    }

    public boolean canSelectMultipleComponents(ModuleVersionSelector selector) {
        return versionMatcher.isDynamic(selector.getVersion());
    }

    public ComponentMetaData choose(ComponentMetaData one, ComponentMetaData two) {
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

    private boolean isGeneratedModuleDescriptor(ComponentMetaData componentMetaData) {
        return componentMetaData.isGenerated();
    }

    public ModuleComponentIdentifier choose(ModuleVersionListing versions, DependencyMetaData dependency, ModuleComponentRepositoryAccess moduleAccess) {
        if (versionMatcher.needModuleMetadata(dependency.getRequested().getVersion())) {
            return chooseBestMatchingDependencyWithMetaData(versions, dependency, moduleAccess);
        } else {
            return chooseBestMatchingDependency(versions, dependency.getRequested());
        }
    }

    private ModuleComponentIdentifier chooseBestMatchingDependency(ModuleVersionListing versions, ModuleVersionSelector requested) {
        for (Versioned candidate : sortLatestFirst(versions)) {
            if (versionMatcher.accept(requested.getVersion(), candidate.getVersion())) {
                return DefaultModuleComponentIdentifier.newId(requested.getGroup(), requested.getName(), candidate.getVersion());
            }
        }
        return null;
    }

    private ModuleComponentIdentifier chooseBestMatchingDependencyWithMetaData(ModuleVersionListing versions, DependencyMetaData dependency, ModuleComponentRepositoryAccess moduleAccess) {
        for (Versioned candidate : sortLatestFirst(versions)) {
            MutableModuleVersionMetaData metaData = resolveComponentMetaData(dependency, candidate, moduleAccess);
            if (versionMatcher.accept(dependency.getRequested().getVersion(), metaData)) {
                // We already resolved the correct module.
                return metaData.getComponentId();
            }
        }
        return null;
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
