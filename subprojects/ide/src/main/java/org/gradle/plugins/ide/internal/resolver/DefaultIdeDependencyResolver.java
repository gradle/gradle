/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.specs.Specs;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;

import java.io.File;
import java.util.*;

public class DefaultIdeDependencyResolver implements IdeDependencyResolver {
    /**
     * Gets IDE project dependencies.
     *
     * @param configuration Configuration
     * @param project Project
     * @return IDE project dependencies
     */
    public List<IdeProjectDependency> getIdeProjectDependencies(Configuration configuration, Project project) {
        ResolutionResult result = getIncomingResolutionResult(configuration);
        Set<ResolvedComponentResult> projectComponents = findAllResolvedProjectDependencyResultsAccessibleOnlyFromRoot(result.getRoot());

        List<IdeProjectDependency> ideProjectDependencies = new ArrayList<IdeProjectDependency>();

        for (ResolvedComponentResult projectComponent : projectComponents) {
            Project resolvedProject = project.project(((ProjectComponentIdentifier) projectComponent.getId()).getProjectPath());
            ideProjectDependencies.add(new IdeProjectDependency(configuration, resolvedProject));
        }

        return ideProjectDependencies;
    }

    /**
     * Finds all project dependencies that are inaccessible from any other project dependencies of the component, only from root.
     * Direct and transitive dependencies are both considered accessible here.
     *
     * @param root root component
     * @return Resolved dependency results
     */
    private Set<ResolvedComponentResult> findAllResolvedProjectDependencyResultsAccessibleOnlyFromRoot(ResolvedComponentResult root) {
        Multimap<ResolvedComponentResult, ResolvedComponentResult> parents = LinkedHashMultimap.create();
        findAllResolvedDependencyResultsAndTheirDependenciesAndRecordTheirParents(parents, root);

        Set<ResolvedComponentResult> matches = new LinkedHashSet<ResolvedComponentResult>();

        for (ResolvedComponentResult component : parents.keySet()) {
            if (component.getId() instanceof ProjectComponentIdentifier) {
                boolean hasNoParentOtherThanRoot = true;
                for (ResolvedComponentResult parent : parents.get(component)) {
                    if (!parent.getId().equals(root.getId()) && parent.getId() instanceof ProjectComponentIdentifier) {
                        hasNoParentOtherThanRoot = false;
                        break;
                    }
                }
                if (hasNoParentOtherThanRoot) {
                    matches.add(component);
                }
            }
        }

        return matches;
    }

    private void findAllResolvedDependencyResultsAndTheirDependenciesAndRecordTheirParents(Multimap<ResolvedComponentResult, ResolvedComponentResult> parents, ResolvedComponentResult parent) {
        for (DependencyResult dependencyResult : parent.getDependencies()) {
            if (dependencyResult instanceof ResolvedDependencyResult) {
                ResolvedComponentResult child = ((ResolvedDependencyResult) dependencyResult).getSelected();
                // avoid circular dependencies by checking whether component result is already visited
                if (parents.containsKey(child)) {
                    continue;
                }
                recordParents(parents, parent, child);
                findAllResolvedDependencyResultsAndTheirDependenciesAndRecordTheirParents(parents, child);
            }
        }
    }

    private void recordParents(Multimap<ResolvedComponentResult, ResolvedComponentResult> parents, ResolvedComponentResult parent, ResolvedComponentResult child) {
        parents.put(child, parent);
        for (ResolvedComponentResult grandParent : parents.get(parent)) {
            recordParents(parents, grandParent, child);
        }
    }

    /**
     * Gets unresolved IDE repository file dependencies.
     *
     * @param configuration Configuration
     * @return Unresolved IDE repositoru file dependencies
     */
    public List<UnresolvedIdeRepoFileDependency> getUnresolvedIdeRepoFileDependencies(Configuration configuration) {
        List<ComponentSelector> componentSelectors = getUnresolvedComponentSelectors(configuration);
        List<UnresolvedIdeRepoFileDependency> unresolvedIdeRepoFileDependencies = new ArrayList<UnresolvedIdeRepoFileDependency>();

        for (ComponentSelector componentSelector : componentSelectors) {
            UnresolvedIdeRepoFileDependency unresolvedIdeRepoFileDependency = new UnresolvedIdeRepoFileDependency(configuration, new File(unresolvedFileName(componentSelector)));
            unresolvedIdeRepoFileDependencies.add(unresolvedIdeRepoFileDependency);
        }

        return unresolvedIdeRepoFileDependencies;
    }

    /**
     * Creates unresolved file name.
     *
     * @param componentSelector Component selector
     * @return Unresolved file name
     */
    private String unresolvedFileName(ComponentSelector componentSelector) {
        return "unresolved dependency - " + componentSelector.getDisplayName().replaceAll(":", " ");
    }

    /**
     * Gets IDE local file dependencies.
     *
     * @param configuration Configuration
     * @return IDE local file dependencies
     */
    public List<IdeExtendedRepoFileDependency> getIdeRepoFileDependencies(Configuration configuration) {
        ResolutionResult result = getIncomingResolutionResult(configuration);
        List<ResolvedComponentResult> resolvedDependencies = new ArrayList<ResolvedComponentResult>();
        findAllResolvedDependencyResultsAndTheirDependencies(resolvedDependencies, result.getRoot().getDependencies(), ModuleComponentIdentifier.class);
        Set<ModuleVersionIdentifier> mappedResolvedDependencies = mapResolvedDependencies(resolvedDependencies);
        Set<ResolvedArtifact> artifacts = getExternalArtifacts(configuration);

        List<IdeExtendedRepoFileDependency> externalDependencies = new ArrayList<IdeExtendedRepoFileDependency>();

        for (ResolvedArtifact artifact : artifacts) {
            if (mappedResolvedDependencies.contains(artifact.getModuleVersion().getId())) {
                IdeExtendedRepoFileDependency ideRepoFileDependency = new IdeExtendedRepoFileDependency(configuration, artifact.getFile());
                ideRepoFileDependency.setId(artifact.getModuleVersion().getId());
                externalDependencies.add(ideRepoFileDependency);
            }
        }

        return externalDependencies;
    }

    /**
     * Finds all resolved components of the given type from the given set of dependency edges. If resolved component has dependencies itself, recursively resolve them as well
     * and add them to the results. This method can handle circular dependencies.
     *
     * @param matches Resolved dependency results
     * @param dependencies Dependencies
     */
    private void findAllResolvedDependencyResultsAndTheirDependencies(List<ResolvedComponentResult> matches, Set<? extends DependencyResult> dependencies, Class<? extends ComponentIdentifier> type) {
        for (DependencyResult dependencyResult : dependencies) {
            if (dependencyResult instanceof ResolvedDependencyResult) {
                ResolvedDependencyResult resolvedResult = (ResolvedDependencyResult) dependencyResult;
                if (type.isInstance(resolvedResult.getSelected().getId())) {
                    // avoid circular dependencies by checking whether component result is already added
                    if (!matches.contains(resolvedResult.getSelected())) {
                        matches.add(resolvedResult.getSelected());
                        findAllResolvedDependencyResultsAndTheirDependencies(matches, resolvedResult.getSelected().getDependencies(), type);
                    }
                }
            }
        }
    }

    /**
     * Maps resolved dependencies by module version identifier.
     *
     * @param components Resolved dependencies
     * @return Mapped, resolved dependencies
     */
    private Set<ModuleVersionIdentifier> mapResolvedDependencies(List<ResolvedComponentResult> components) {
        Set<ModuleVersionIdentifier> mappedResolvedDependencies = new LinkedHashSet<ModuleVersionIdentifier>();
        for (ResolvedComponentResult component : components) {
            mappedResolvedDependencies.add(component.getModuleVersion());
        }
        return mappedResolvedDependencies;
    }

    /**
     * Gets IDE local file dependencies.
     *
     * @param configuration Configuration
     * @return IDE local file dependencies
     */
    public List<IdeLocalFileDependency> getIdeLocalFileDependencies(Configuration configuration) {
        List<SelfResolvingDependency> externalDependencies = findAllExternalDependencies(configuration);
        List<IdeLocalFileDependency> ideLocalFileDependencies = new ArrayList<IdeLocalFileDependency>();

        for (SelfResolvingDependency externalDependency : externalDependencies) {
            Set<File> resolvedFiles = externalDependency.resolve();

            for (File resolvedFile : resolvedFiles) {
                IdeLocalFileDependency ideLocalFileDependency = new IdeLocalFileDependency(configuration, resolvedFile);
                ideLocalFileDependencies.add(ideLocalFileDependency);
            }
        }

        return ideLocalFileDependencies;
    }

    /**
     * Finds all external dependencies.
     *
     * @param configuration Configuration
     * @return External dependencies
     */
    private List<SelfResolvingDependency> findAllExternalDependencies(Configuration configuration) {
        List<SelfResolvingDependency> externalDependencies = new ArrayList<SelfResolvingDependency>();

        for (Dependency dependency : configuration.getAllDependencies()) {
            if (dependency instanceof SelfResolvingDependency) {
                externalDependencies.add((SelfResolvingDependency) dependency);
            }
        }

        return externalDependencies;
    }

    /**
     * Gets incoming resolution result for a given configuration.
     *
     * @param configuration Configuration
     * @return Incoming resolution result
     */
    private ResolutionResult getIncomingResolutionResult(Configuration configuration) {
        return configuration.getIncoming().getResolutionResult();
    }

    /**
     * Gets unresolved component selectors for a given configuration.
     *
     * @param configuration Configuration
     * @return List of unresolved component selectors
     */
    private List<ComponentSelector> getUnresolvedComponentSelectors(Configuration configuration) {
        ResolutionResult result = getIncomingResolutionResult(configuration);
        List<UnresolvedDependencyResult> unresolvedDependencies = findAllUnresolvedDependencyResults(result.getRoot().getDependencies());
        List<ComponentSelector> componentSelectors = new ArrayList<ComponentSelector>();

        for (UnresolvedDependencyResult unresolvedDependencyResult : unresolvedDependencies) {
            componentSelectors.add(unresolvedDependencyResult.getAttempted());
        }

        return componentSelectors;
    }

    /**
     * Finds all unresolved dependency results.
     *
     * @param dependencies Unfiltered dependencies
     * @return Unresolved dependency results.
     */
    private List<UnresolvedDependencyResult> findAllUnresolvedDependencyResults(Set<? extends DependencyResult> dependencies) {
        List<UnresolvedDependencyResult> unresolvedDependencyResults = new ArrayList<UnresolvedDependencyResult>();

        for (DependencyResult dependencyResult : dependencies) {
            if (dependencyResult instanceof UnresolvedDependencyResult) {
                unresolvedDependencyResults.add((UnresolvedDependencyResult) dependencyResult);
            }
        }

        return unresolvedDependencyResults;
    }

    /**
     * Gets all external artifacts.
     *
     * @param configuration Configuration
     * @return External artifacts
     */
    private Set<ResolvedArtifact> getExternalArtifacts(Configuration configuration) {
        return configuration.getResolvedConfiguration().getLenientConfiguration().getArtifacts(Specs.SATISFIES_ALL);
    }
}
