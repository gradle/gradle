/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.composite.internal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.SortedSet;

/**
 * Provides a dependency substitution rule for composite build,
 * that substitutes a project within the composite with any dependency with a matching ModuleIdentifier.
 */
public class CompositeBuildDependencySubstitutions implements Action<DependencySubstitution> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeBuildDependencySubstitutions.class);

    private final Multimap<ModuleIdentifier, ProjectComponentIdentifier> replacementMap = ArrayListMultimap.create();

    public CompositeBuildDependencySubstitutions(Collection<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> replacements) {
        for (Pair<ModuleVersionIdentifier, ProjectComponentIdentifier> replacement : replacements) {
            replacementMap.put(replacement.getLeft().getModule(), replacement.getRight());
        }
    }

    @Override
    public void execute(DependencySubstitution sub) {
        DependencySubstitutionInternal dependencySubstitution = (DependencySubstitutionInternal) sub;
        // Use the result of previous rules as the input for dependency substitution
        ComponentSelector requested = dependencySubstitution.getTarget();
        if (requested instanceof ModuleComponentSelector) {
            ModuleComponentSelector selector = (ModuleComponentSelector) requested;
            ProjectComponentIdentifier replacement = getReplacementFor(selector);
            if (replacement != null) {
                dependencySubstitution.useTarget(
                    DefaultProjectComponentSelector.newSelector(replacement),
                    VersionSelectionReasons.COMPOSITE_BUILD);
            }
        }
    }

    private ProjectComponentIdentifier getReplacementFor(ModuleComponentSelector selector) {
        ModuleIdentifier candidateId = DefaultModuleIdentifier.newId(selector.getGroup(), selector.getModule());
        Collection<ProjectComponentIdentifier> providingProjects = replacementMap.get(candidateId);
        if (providingProjects.isEmpty()) {
            LOGGER.info("Found no composite build substitute for module '" + candidateId + "'.");
            return null;
        }
        if (providingProjects.size() == 1) {
            ProjectComponentIdentifier match = providingProjects.iterator().next();
            LOGGER.info("Found project '" + match + "' as substitute for module '" + candidateId + "'.");
            return match;
        }
        SortedSet<String> sortedProjects = Sets.newTreeSet(CollectionUtils.collect(providingProjects, new Transformer<String, ProjectComponentIdentifier>() {
            @Override
            public String transform(ProjectComponentIdentifier projectComponentIdentifier) {
                return projectComponentIdentifier.getDisplayName();
            }
        }));
        String failureMessage = String.format("Module version '%s' is not unique in composite: can be provided by %s.", selector.getDisplayName(), sortedProjects);
        throw new ModuleVersionResolveException(selector, failureMessage);
    }
}
