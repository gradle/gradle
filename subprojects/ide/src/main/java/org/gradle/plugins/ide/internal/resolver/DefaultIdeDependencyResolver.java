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

import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.specs.Spec;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeRepoFileDependency;
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
        List<ResolvedDependencyResult> resolvedDependencies = findAllResolvedDependencyResults(result.getRoot().getDependencies());
        List<ResolvedComponentResult> projectComponents = findAllProjectComponents(resolvedDependencies);

        List<IdeProjectDependency> ideProjectDependencies = new ArrayList<IdeProjectDependency>();

        for(ResolvedComponentResult projectComponent : projectComponents) {
            Project resolvedProject = project.project(((ProjectComponentIdentifier)projectComponent.getId()).getProjectPath());
            ideProjectDependencies.add(new IdeProjectDependency(configuration, resolvedProject));
        }

        return ideProjectDependencies;
    }

    /**
     * Finds all resolved dependency results.
     *
     * @param dependencies Dependencies
     * @return Resolved dependency results
     */
    private List<ResolvedDependencyResult> findAllResolvedDependencyResults(Set<? extends DependencyResult> dependencies) {
        List<ResolvedDependencyResult> resolvedDependencyResults = new ArrayList<ResolvedDependencyResult>();

        for(DependencyResult dependencyResult : dependencies) {
            if(dependencyResult instanceof ResolvedDependencyResult) {
                resolvedDependencyResults.add((ResolvedDependencyResult)dependencyResult);
            }
        }

        return resolvedDependencyResults;
    }

    /**
     * Finds all resolved module dependency results.
     *
     * @param dependencies Dependencies
     * @return Resolved module dependency results
     */
    private List<ResolvedDependencyResult> findAllResolvedModuleDependencies(Set<? extends DependencyResult> dependencies) {
        List<ResolvedDependencyResult> resolvedDependencyResults = findAllResolvedDependencyResults(dependencies);
        List<ResolvedDependencyResult> resolvedModuleDependencyResults = new ArrayList<ResolvedDependencyResult>();

        for(ResolvedDependencyResult resolvedDependencyResult : resolvedDependencyResults) {
            if(resolvedDependencyResult.getSelected().getId() instanceof ModuleComponentIdentifier) {
                resolvedModuleDependencyResults.add(resolvedDependencyResult);
            }
        }

        return resolvedModuleDependencyResults;
    }

    /**
     * Finds all project components.
     *
     * @param resolvedDependencies Resolved dependencies
     * @return Project components
     */
    private List<ResolvedComponentResult> findAllProjectComponents(List<ResolvedDependencyResult> resolvedDependencies) {
        List<ResolvedComponentResult> projectComponents = new ArrayList<ResolvedComponentResult>();

        for(ResolvedDependencyResult resolvedDependencyResult : resolvedDependencies) {
            ResolvedComponentResult selected = resolvedDependencyResult.getSelected();

            if(selected.getId() instanceof ProjectComponentIdentifier) {
                projectComponents.add(selected);
            }
        }

        return projectComponents;
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

        for(ComponentSelector componentSelector : componentSelectors) {
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
    public List<IdeRepoFileDependency> getIdeRepoFileDependencies(Configuration configuration) {
        ResolutionResult result = getIncomingResolutionResult(configuration);
        List<ResolvedDependencyResult> resolvedDependencies = findAllResolvedModuleDependencies(result.getAllDependencies());
        Map<ModuleVersionIdentifier, ResolvedDependencyResult> mappedResolvedDependencies = mapResolvedDependencies(resolvedDependencies);
        Set<ResolvedArtifact> artifacts = getExternalArtifacts(configuration);

        List<IdeRepoFileDependency> externalDependencies = new ArrayList<IdeRepoFileDependency>();

        for(ResolvedArtifact artifact : artifacts) {
            if(mappedResolvedDependencies.containsKey(artifact.getModuleVersion().getId())) {
                IdeRepoFileDependency ideRepoFileDependency = new IdeRepoFileDependency(configuration, artifact.getFile());
                ideRepoFileDependency.setId(artifact.getModuleVersion().getId());
                externalDependencies.add(ideRepoFileDependency);
            }
        }

        return externalDependencies;
    }

    /**
     * Maps resolved dependencies by module version identifier.
     *
     * @param resolvedDependencies Resolved dependencies
     * @return Mapped, resolved dependencies
     */
    private Map<ModuleVersionIdentifier, ResolvedDependencyResult> mapResolvedDependencies(List<ResolvedDependencyResult> resolvedDependencies) {
        Map<ModuleVersionIdentifier, ResolvedDependencyResult> mappedResolvedDependencies = new LinkedHashMap<ModuleVersionIdentifier, ResolvedDependencyResult>();

        for(ResolvedDependencyResult resolvedDependency : resolvedDependencies) {
            mappedResolvedDependencies.put(resolvedDependency.getSelected().getModuleVersion(), resolvedDependency);
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

        for(SelfResolvingDependency externalDependency : externalDependencies) {
            Set<File> resolvedFiles = externalDependency.resolve();

            for(File resolvedFile : resolvedFiles) {
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

        for(Dependency dependency : configuration.getAllDependencies()) {
            if(dependency instanceof SelfResolvingDependency && !(dependency instanceof ProjectDependency)) {
                externalDependencies.add((SelfResolvingDependency)dependency);
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

        for(UnresolvedDependencyResult unresolvedDependencyResult : unresolvedDependencies) {
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

        for(DependencyResult dependencyResult : dependencies) {
            if(dependencyResult instanceof UnresolvedDependencyResult) {
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
        Spec<Dependency> externalDependencySpec = new Spec<Dependency>() {
            public boolean isSatisfiedBy(Dependency element) {
                return element instanceof ExternalDependency;
            }
        };

        return configuration.getResolvedConfiguration().getLenientConfiguration().getArtifacts(externalDependencySpec);
    }
}