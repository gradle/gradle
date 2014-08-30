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

import com.google.common.collect.Lists;
import org.gradle.api.RuleAction;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ComponentSelectionInternal;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.DefaultComponentSelection;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.ExternalComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class NewestVersionComponentChooser implements ComponentChooser {
    private final ComponentSelectionRulesProcessor rulesProcessor = new ComponentSelectionRulesProcessor();
    private final VersionMatcher versionMatcher;
    private final LatestStrategy latestStrategy;
    private final ComponentSelectionRulesInternal componentSelectionRules;

    NewestVersionComponentChooser(LatestStrategy latestStrategy, VersionMatcher versionMatcher, ComponentSelectionRulesInternal componentSelectionRules) {
        this.latestStrategy = latestStrategy;
        this.versionMatcher = versionMatcher;
        this.componentSelectionRules = componentSelectionRules;
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
        ModuleVersionSelector requested = dependency.getRequested();
        List<RuleAction<? super ComponentSelection>> rules = buildRulesForSelector(requested);

        for (Versioned candidate : sortLatestFirst(versions)) {
            ModuleComponentIdentifier candidateIdentifier = DefaultModuleComponentIdentifier.newId(requested.getGroup(), requested.getName(), candidate.getVersion());

            if (isRejectedByRules(candidateIdentifier, rules, dependency, moduleAccess)) {
                continue;
            }

            return candidateIdentifier;
        }
        return null;
    }

    private List<RuleAction<? super ComponentSelection>> buildRulesForSelector(ModuleVersionSelector requested) {
        List<RuleAction<? super ComponentSelection>> rules = Lists.newArrayList();
        if (versionMatcher.needModuleMetadata(requested.getVersion())) {
            rules.add(new MetadataVersionMatchingRule());
        } else {
            rules.add(new SimpleVersionMatchingRule());
        }
        rules.addAll(componentSelectionRules.getRules());
        return rules;
    }

    public boolean isRejectedByRules(ModuleComponentIdentifier candidateIdentifier, DependencyMetaData dependency, ModuleComponentRepositoryAccess moduleAccess) {
        return isRejectedByRules(candidateIdentifier, componentSelectionRules.getRules(), dependency, moduleAccess);
    }

    private boolean isRejectedByRules(ModuleComponentIdentifier candidateIdentifier, Collection<RuleAction<? super ComponentSelection>> rules, DependencyMetaData dependency, ModuleComponentRepositoryAccess moduleAccess) {
        ComponentSelectionInternal selection = new DefaultComponentSelection(dependency, candidateIdentifier);
        rulesProcessor.apply(selection, rules, moduleAccess);
        return selection.isRejected();
    }

    private List<Versioned> sortLatestFirst(ModuleVersionListing listing) {
        List<Versioned> sorted = latestStrategy.sort(listing.getVersions());
        Collections.reverse(sorted);
        return sorted;
    }

    private final class SimpleVersionMatchingRule implements RuleAction<ComponentSelection> {
        public List<Class<?>> getInputTypes() {
            return Collections.emptyList();
        }

        public void execute(ComponentSelection selection, List<?> inputs) {
            if (!versionMatcher.accept(selection.getRequested().getVersion(), selection.getCandidate().getVersion())) {
                selection.reject("Incorrect version");
            }
        }
    }

    private final class MetadataVersionMatchingRule implements RuleAction<ComponentSelection> {
        public List<Class<?>> getInputTypes() {
            return Collections.<Class<?>>singletonList(ModuleVersionMetaData.class);
        }

        public void execute(ComponentSelection selection, List<?> inputs) {
            ModuleVersionMetaData metadata = (ModuleVersionMetaData) inputs.get(0);
            if (!versionMatcher.accept(selection.getRequested().getVersion(), metadata)) {
                selection.reject("Does not match requested version or status");
            }
        }
    }
}
