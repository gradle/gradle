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
package org.gradle.plugins.ide.internal;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.component.Artifact;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.gradle.plugins.ide.internal.resolver.DefaultIdeDependencyResolver;
import org.gradle.plugins.ide.internal.resolver.IdeDependencyResolver;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;
import org.gradle.jvm.JvmLibrary;

import java.io.File;
import java.util.*;

public class IdeDependenciesExtractor {

    private final IdeDependencyResolver ideDependencyResolver = new DefaultIdeDependencyResolver();

    public Collection<IdeProjectDependency> extractProjectDependencies(Project project, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<Project, IdeProjectDependency> deps = new LinkedHashMap<Project, IdeProjectDependency>();

        for (Configuration plusConfiguration : plusConfigurations) {
            for (IdeProjectDependency dep : ideDependencyResolver.getIdeProjectDependencies(plusConfiguration, project)) {
                deps.put(dep.getProject(), dep);
            }
        }

        for (Configuration minusConfiguration : minusConfigurations) {
            for (IdeProjectDependency dep : ideDependencyResolver.getIdeProjectDependencies(minusConfiguration, project)) {
                deps.remove(dep.getProject());
            }
        }

        return deps.values();
    }

    public Collection<IdeExtendedRepoFileDependency> extractRepoFileDependencies(DependencyHandler dependencyHandler, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations, boolean downloadSources, boolean downloadJavadoc) {
        // can have multiple IDE dependencies with same component identifier (see GRADLE-1622)
        Multimap<ComponentIdentifier, IdeExtendedRepoFileDependency> resolvedDependenciesComponentMap = LinkedHashMultimap.create();
        for (IdeExtendedRepoFileDependency dep : resolvedExternalDependencies(plusConfigurations, minusConfigurations)) {
            resolvedDependenciesComponentMap.put(toComponentIdentifier(dep.getId()), dep);
        }

        List<Class<? extends Artifact>> artifactTypes = new ArrayList<Class<? extends Artifact>>(2);
        if (downloadSources) {
            artifactTypes.add(SourcesArtifact.class);
        }

        if (downloadJavadoc) {
            artifactTypes.add(JavadocArtifact.class);
        }

        downloadAuxiliaryArtifacts(dependencyHandler, resolvedDependenciesComponentMap, artifactTypes);

        Collection<UnresolvedIdeRepoFileDependency> unresolvedDependencies = unresolvedExternalDependencies(plusConfigurations, minusConfigurations);
        Collection<IdeExtendedRepoFileDependency> resolvedDependencies = resolvedDependenciesComponentMap.values();

        Collection<IdeExtendedRepoFileDependency> resolvedAndUnresolved = new ArrayList<IdeExtendedRepoFileDependency>(unresolvedDependencies.size() + resolvedDependencies.size());
        resolvedAndUnresolved.addAll(resolvedDependencies);
        resolvedAndUnresolved.addAll(unresolvedDependencies);
        return resolvedAndUnresolved;
    }

    private ModuleComponentIdentifier toComponentIdentifier(ModuleVersionIdentifier id) {
        return new DefaultModuleComponentIdentifier(id.getGroup(), id.getName(), id.getVersion());
    }

    private static void downloadAuxiliaryArtifacts(DependencyHandler dependencyHandler, Multimap<ComponentIdentifier, IdeExtendedRepoFileDependency> dependencies, List<Class<? extends Artifact>> artifactTypes) {
        if (artifactTypes.isEmpty()) {
            return;
        }

        ArtifactResolutionQuery query = dependencyHandler.createArtifactResolutionQuery();
        query.forComponents(dependencies.keySet());

        @SuppressWarnings("unchecked") Class<? extends Artifact>[] artifactTypesArray = (Class<? extends Artifact>[]) new Class<?>[artifactTypes.size()];
        query.withArtifacts(JvmLibrary.class, artifactTypes.toArray(artifactTypesArray));
        Set<ComponentArtifactsResult> componentResults = query.execute().getResolvedComponents();
        for (ComponentArtifactsResult componentResult : componentResults) {
            for (IdeExtendedRepoFileDependency dependency : dependencies.get(componentResult.getId())) {
                for (ArtifactResult sourcesResult : componentResult.getArtifacts(SourcesArtifact.class)) {
                    if (sourcesResult instanceof ResolvedArtifactResult) {
                        dependency.setSourceFile(((ResolvedArtifactResult) sourcesResult).getFile());
                    }
                }

                for (ArtifactResult javadocResult : componentResult.getArtifacts(JavadocArtifact.class)) {
                    if (javadocResult instanceof ResolvedArtifactResult) {
                        dependency.setJavadocFile(((ResolvedArtifactResult) javadocResult).getFile());
                    }
                }
            }
        }
    }

    private Collection<UnresolvedIdeRepoFileDependency> unresolvedExternalDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        final LinkedHashMap<File, UnresolvedIdeRepoFileDependency> unresolved = new LinkedHashMap<File, UnresolvedIdeRepoFileDependency>();

        for (Configuration c : plusConfigurations) {
            List<UnresolvedIdeRepoFileDependency> deps = ideDependencyResolver.getUnresolvedIdeRepoFileDependencies(c);
            for (UnresolvedIdeRepoFileDependency dep : deps) {
                unresolved.put(dep.getFile(), dep);
            }
        }

        for (Configuration c : minusConfigurations) {
            List<UnresolvedIdeRepoFileDependency> deps = ideDependencyResolver.getUnresolvedIdeRepoFileDependencies(c);
            for (UnresolvedIdeRepoFileDependency dep : deps) {
                unresolved.remove(dep.getFile());
            }
        }

        return unresolved.values();
    }

    public Collection<IdeLocalFileDependency> extractLocalFileDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<File, IdeLocalFileDependency> fileToConf = new LinkedHashMap<File, IdeLocalFileDependency>();

        if (plusConfigurations != null) {
            for (Configuration plusConfiguration : plusConfigurations) {
                for (IdeLocalFileDependency localFileDependency : ideDependencyResolver.getIdeLocalFileDependencies(plusConfiguration)) {
                    fileToConf.put(localFileDependency.getFile(), localFileDependency);
                }
            }
        }

        if (minusConfigurations != null) {
            for (Configuration minusConfiguration : minusConfigurations) {
                for (IdeLocalFileDependency localFileDependency : ideDependencyResolver.getIdeLocalFileDependencies(minusConfiguration)) {
                    fileToConf.remove(localFileDependency.getFile());
                }
            }
        }

        return fileToConf.values();
    }

    public Collection<IdeExtendedRepoFileDependency> resolvedExternalDependencies(Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        LinkedHashMap<File, IdeExtendedRepoFileDependency> out = new LinkedHashMap<File, IdeExtendedRepoFileDependency>();

        if (plusConfigurations != null) {
            for (Configuration plusConfiguration : plusConfigurations) {
                for (IdeExtendedRepoFileDependency artifact : ideDependencyResolver.getIdeRepoFileDependencies(plusConfiguration)) {
                    out.put(artifact.getFile(), artifact);
                }
            }
        }

        if (minusConfigurations != null) {
            for (Configuration minusConfiguration : minusConfigurations) {
                for (IdeExtendedRepoFileDependency artifact : ideDependencyResolver.getIdeRepoFileDependencies(minusConfiguration)) {
                    out.remove(artifact.getFile());
                }
            }
        }

        return out.values();
    }
}
