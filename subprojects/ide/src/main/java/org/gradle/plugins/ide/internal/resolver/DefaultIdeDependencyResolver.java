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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        final Set<ResolvedComponentResult> projectComponents = CollectionUtils.filter(result.getAllComponents(), new Spec<ResolvedComponentResult>() {
            @Override
            public boolean isSatisfiedBy(ResolvedComponentResult element) {
                return element.getId() instanceof ProjectComponentIdentifier;
            }
        });
        List<IdeProjectDependency> ideProjectDependencies = new ArrayList<IdeProjectDependency>();

        ProjectComponentIdentifier thisProjectId = DefaultProjectComponentIdentifier.newProjectId(project);
        for (ResolvedComponentResult projectComponent : projectComponents) {
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) projectComponent.getId();
            if (thisProjectId.equals(projectId)) {
                continue;
            }
            if (!projectId.getBuild().isCurrentBuild()) {
                // Don't have access to the ProjectInstance: we can't use it to determine the name.
                ideProjectDependencies.add(new IdeProjectDependency(projectId));
            } else {
                Project resolvedProject = project.project(projectId.getProjectPath());
                ideProjectDependencies.add(new IdeProjectDependency(projectId, resolvedProject.getName()));
            }
        }
        return ideProjectDependencies;
    }

    /**
     * Gets unresolved IDE repository file dependencies.
     *
     * @param configuration Configuration
     * @return Unresolved IDE repositoru file dependencies
     */
    public List<UnresolvedIdeRepoFileDependency> getUnresolvedIdeRepoFileDependencies(Configuration configuration) {
        ResolutionResult result = getIncomingResolutionResult(configuration);
        List<UnresolvedDependencyResult> unresolvedDependencies = findAllUnresolvedDependencyResults(result.getRoot().getDependencies());
        List<UnresolvedIdeRepoFileDependency> unresolvedIdeRepoFileDependencies = new ArrayList<UnresolvedIdeRepoFileDependency>();

        for (UnresolvedDependencyResult unresolvedDependencyResult : unresolvedDependencies) {
            Throwable failure = unresolvedDependencyResult.getFailure();
            ComponentSelector componentSelector = unresolvedDependencyResult.getAttempted();

            String displayName = componentSelector.getDisplayName();
            File file = new File(unresolvedFileName(componentSelector));
            unresolvedIdeRepoFileDependencies.add(new UnresolvedIdeRepoFileDependency(file, failure, displayName));
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
     * Gets IDE repository file dependencies.
     *
     * @param configuration Configuration
     * @return IDE repository file dependencies
     */
    public List<IdeExtendedRepoFileDependency> getIdeRepoFileDependencies(Configuration configuration) {
        ResolutionResult result = getIncomingResolutionResult(configuration);
        final Set<ResolvedComponentResult> resolvedRepoFileComponents = CollectionUtils.filter(result.getAllComponents(), new Spec<ResolvedComponentResult>() {
            @Override
            public boolean isSatisfiedBy(ResolvedComponentResult element) {
                return element.getId() instanceof ModuleComponentIdentifier;
            }
        });
        Set<ModuleVersionIdentifier> mappedResolvedDependencies = mapResolvedDependencies(resolvedRepoFileComponents);
        Set<ResolvedArtifact> artifacts = getExternalArtifacts(configuration);
        List<IdeExtendedRepoFileDependency> externalDependencies = new ArrayList<IdeExtendedRepoFileDependency>();
        for (ResolvedArtifact artifact : artifacts) {
            if (mappedResolvedDependencies.contains(artifact.getModuleVersion().getId())) {
                IdeExtendedRepoFileDependency ideRepoFileDependency = new IdeExtendedRepoFileDependency(artifact.getFile());
                ideRepoFileDependency.setId(artifact.getModuleVersion().getId());
                externalDependencies.add(ideRepoFileDependency);
            }
        }

        return externalDependencies;
    }

    /**
     * Maps resolved dependencies by module version identifier.
     *
     * @param components Resolved dependencies
     * @return Mapped, resolved dependencies
     */
    private Set<ModuleVersionIdentifier> mapResolvedDependencies(Set<ResolvedComponentResult> components) {
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
        List<SelfResolvingDependency> externalDependencies = new ArrayList<SelfResolvingDependency>();
        findAllExternalDependencies(externalDependencies, new ArrayList<Dependency>(), configuration);
        List<IdeLocalFileDependency> ideLocalFileDependencies = new ArrayList<IdeLocalFileDependency>();

        for (SelfResolvingDependency externalDependency : externalDependencies) {
            Set<File> resolvedFiles = externalDependency.resolve(configuration.isTransitive());

            for (File resolvedFile : resolvedFiles) {
                IdeLocalFileDependency ideLocalFileDependency = new IdeLocalFileDependency(resolvedFile);
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
    private List<SelfResolvingDependency> findAllExternalDependencies(List<SelfResolvingDependency> externalDependencies, List<Dependency> visited, Configuration configuration) {
        for (Dependency dependency : configuration.getAllDependencies()) {
            if(!visited.contains(dependency)){
                visited.add(dependency);
                if(dependency instanceof ProjectDependency && configuration.isTransitive()) {
                    findAllExternalDependencies(externalDependencies, visited, getTargetConfiguration((ProjectDependency) dependency));
                } else if (dependency instanceof SelfResolvingDependency) {
                    externalDependencies.add((SelfResolvingDependency) dependency);
                }
            }
        }
        return externalDependencies;
    }

    private Configuration getTargetConfiguration(ProjectDependency dependency) {
        String targetConfiguration = dependency.getTargetConfiguration();
        if (targetConfiguration == null) {
            targetConfiguration = Dependency.DEFAULT_CONFIGURATION;
        }
        return dependency.getDependencyProject().getConfigurations().getByName(targetConfiguration);
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
