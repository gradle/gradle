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
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
            ProjectComponentIdentifierInternal replacement = getReplacementFor(selector);
            if (replacement != null) {
                ProjectComponentSelector targetProject = new DefaultProjectComponentSelector(
                    replacement.getProjectIdentity(),
                    ((AttributeContainerInternal)requested.getAttributes()).asImmutable(),
                    requested.getRequestedCapabilities()
                );
                dependencySubstitution.useTarget(
                    targetProject,
                    ComponentSelectionReasons.COMPOSITE_BUILD);
            }
        }
    }

    @Nullable
    private ProjectComponentIdentifierInternal getReplacementFor(ModuleComponentSelector selector) {
        ModuleIdentifier candidateId = selector.getModuleIdentifier();
        Collection<ProjectComponentIdentifier> providingProjects = replacementMap.get(candidateId);
        if (providingProjects.isEmpty()) {
            LOGGER.debug("Found no composite build substitute for module '{}'.", candidateId);
            return null;
        }
        if (providingProjects.size() == 1) {
            ProjectComponentIdentifier match = providingProjects.iterator().next();
            LOGGER.info("Found project '{}' as substitute for module '{}'.", match, candidateId);
            return (ProjectComponentIdentifierInternal) match;
        }
        throw new ModuleVersionResolveException(selector, () -> {
            SortedSet<String> sortedProjects =
                providingProjects.stream()
                .map(ComponentIdentifier::getDisplayName)
                .collect(Collectors.toCollection(TreeSet::new));
            return String.format("Module version '%s' is not unique in composite: can be provided by %s.", selector.getDisplayName(), sortedProjects);
        });
    }
}
