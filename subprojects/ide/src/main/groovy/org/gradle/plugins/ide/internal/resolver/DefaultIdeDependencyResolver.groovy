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

package org.gradle.plugins.ide.internal.resolver

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.specs.Spec
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeRepoFileDependency
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency

class DefaultIdeDependencyResolver implements IdeDependencyResolver {
    List<IdeProjectDependency> getIdeProjectDependencies(Configuration configuration, Project project) {
        ResolutionResult result = getIncomingResolutionResult(configuration)
        Collection<ResolvedDependencyResult> resolvedDependencies = result.root.dependencies.findAll { it instanceof ResolvedDependencyResult }
        Collection<ResolvedComponentResult> projectComponents = resolvedDependencies.selected.findAll { it.id instanceof ProjectComponentIdentifier }
        projectComponents.collect { new IdeProjectDependency(project: project.project(it.id.projectPath), declaredConfiguration: configuration) }
    }

    List<UnresolvedIdeRepoFileDependency> getUnresolvedIdeRepoFileDependencies(Configuration configuration) {
        Collection<ModuleComponentSelector> moduleComponentSelectors = getUnresolvedModuleComponentSelectors(configuration)
        moduleComponentSelectors.collect { new UnresolvedIdeRepoFileDependency(file: new File(unresolvedFileName(it)), declaredConfiguration: configuration)}
    }

    private String unresolvedFileName(ModuleComponentSelector dep) {
        "unresolved dependency - $dep.group $dep.module $dep.version"
    }

    List<IdeRepoFileDependency> getIdeRepoFileDependencies(Configuration configuration) {
        Set<ResolvedArtifact> artifacts = getExternalArtifacts(configuration)
        List<IdeRepoFileDependency> externalDependencies = new ArrayList<IdeRepoFileDependency>()

        artifacts.each { artifact ->
            externalDependencies << new IdeRepoFileDependency( file: artifact.file, declaredConfiguration: configuration, id: artifact.moduleVersion.id)
        }

        externalDependencies
    }

    List<IdeLocalFileDependency> getIdeLocalFileDependencies(Configuration configuration) {
        def filter = { it instanceof SelfResolvingDependency && !(it instanceof org.gradle.api.artifacts.ProjectDependency)}
        def deps = configuration.allDependencies.findAll(filter)
        def files = deps.collect { it.resolve() }.flatten()
        files.collect { new IdeLocalFileDependency(file: it, declaredConfiguration: configuration) }
    }

    /**
     * Gets incoming resolution result for a given configuration.
     *
     * @param configuration Configuration
     * @return Incoming resolution result
     */
    private ResolutionResult getIncomingResolutionResult(Configuration configuration) {
        configuration.incoming.resolutionResult
    }

    /**
     * Gets unresolved module component selectors for a given configuration.
     *
     * @param configuration Configuration
     * @return List of unresolved module component selectors
     */
    private Collection<ModuleComponentSelector> getUnresolvedModuleComponentSelectors(Configuration configuration) {
        ResolutionResult result = getIncomingResolutionResult(configuration)
        Collection<UnresolvedDependencyResult> unresolvedDependencies = result.root.dependencies.findAll { it instanceof UnresolvedDependencyResult }
        unresolvedDependencies.requested.findAll { it instanceof ModuleComponentSelector }
    }

    private Set<ResolvedArtifact> getExternalArtifacts(Configuration configuration) {
        return configuration.resolvedConfiguration.lenientConfiguration.getArtifacts({ it instanceof ExternalDependency } as Spec)
    }
}
