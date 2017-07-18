/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.idea.model.internal;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.FilePath;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public class IdeaDependenciesProvider {

    public static final String SCOPE_PLUS = "plus";
    public static final String SCOPE_MINUS = "minus";
    private final IdeDependenciesExtractor dependenciesExtractor;
    private final ModuleDependencyBuilder moduleDependencyBuilder;

    public IdeaDependenciesProvider(ServiceRegistry serviceRegistry) {
        this(new IdeDependenciesExtractor(), serviceRegistry);
    }

    IdeaDependenciesProvider(IdeDependenciesExtractor dependenciesExtractor, ServiceRegistry serviceRegistry) {
        this.dependenciesExtractor = dependenciesExtractor;
        moduleDependencyBuilder = new ModuleDependencyBuilder(serviceRegistry.get(LocalComponentRegistry.class));
    }

    public Set<Dependency> provide(final IdeaModule ideaModule) {
        Set<Dependency> result = Sets.newLinkedHashSet();
        result.addAll(getOutputLocations(ideaModule));
        result.addAll(getDependencies(ideaModule));
        return result;
    }

    private Set<SingleEntryModuleLibrary> getOutputLocations(IdeaModule ideaModule) {
        if (ideaModule.getSingleEntryLibraries() == null) {
            return Collections.emptySet();
        }
        Set<SingleEntryModuleLibrary> outputLocations = Sets.newLinkedHashSet();
        for (Map.Entry<String, Iterable<File>> outputLocation : ideaModule.getSingleEntryLibraries().entrySet()) {
            String scope = outputLocation.getKey();
            for (File file : outputLocation.getValue()) {
                if (file != null && file.isDirectory()) {
                    outputLocations.add(new SingleEntryModuleLibrary(toPath(ideaModule, file), scope));
                }
            }
        }
        return outputLocations;
    }

    private Set<Dependency> getDependencies(IdeaModule ideaModule) {
        Set<Dependency> dependencies = Sets.newLinkedHashSet();
        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            dependencies.addAll(getProjectDependencies(ideaModule, scope));
            dependencies.addAll(getExternalDependencies(ideaModule, scope));
            dependencies.addAll(getFileDependencies(ideaModule, scope));
        }
        return dependencies;
    }

    private Set<Dependency> getProjectDependencies(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        Collection<Configuration> plusConfigurations = getPlusConfigurations(ideaModule, scope);
        Collection<Configuration> minusConfigurations = getMinusConfigurations(ideaModule, scope);

        Collection<IdeProjectDependency> extractedDependencies = dependenciesExtractor.extractProjectDependencies(ideaModule.getProject(), plusConfigurations, minusConfigurations);
        Set<Dependency> dependencies = Sets.newLinkedHashSet();
        for (IdeProjectDependency ideProjectDependency : extractedDependencies) {
            dependencies.add(moduleDependencyBuilder.create(ideProjectDependency, scope.name()));
        }
        return dependencies;
    }

    private Set<Dependency> getExternalDependencies(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        if (ideaModule.isOffline()) {
            return Collections.emptySet();
        }

        Collection<Configuration> plusConfigurations = getPlusConfigurations(ideaModule, scope);
        Collection<Configuration> minusConfigurations = getMinusConfigurations(ideaModule, scope);
        Set<Dependency> dependencies = Sets.newLinkedHashSet();
        Collection<IdeExtendedRepoFileDependency> ideRepoFileDependencies = dependenciesExtractor.extractRepoFileDependencies(
            ideaModule.getProject().getDependencies(), plusConfigurations, minusConfigurations,
            ideaModule.isDownloadSources(), ideaModule.isDownloadJavadoc());

        for (IdeExtendedRepoFileDependency dependency : ideRepoFileDependencies) {
            dependencies.add(toLibraryDependency(dependency, ideaModule, scope));
        }
        return dependencies;
    }

    private SingleEntryModuleLibrary toLibraryDependency(IdeExtendedRepoFileDependency dependency, IdeaModule ideaModule, GeneratedIdeaScope scope) {
        Set<FilePath> javadoc = Sets.newLinkedHashSet();
        for (File javaDocFile : dependency.getJavadocFiles()) {
            javadoc.add(toPath(ideaModule, javaDocFile));
        }
        Set<FilePath> source = Sets.newLinkedHashSet();
        for (File sourceFile : dependency.getSourceFiles()) {
            source.add(toPath(ideaModule, sourceFile));
        }
        SingleEntryModuleLibrary library = new SingleEntryModuleLibrary(toPath(ideaModule, dependency.getFile()), javadoc, source, scope.name());
        library.setModuleVersion(dependency.getId());
        return library;
    }

    private Set<Dependency> getFileDependencies(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        Collection<Configuration> plusConfigurations = getPlusConfigurations(ideaModule, scope);
        Collection<Configuration> minusConfigurations = getMinusConfigurations(ideaModule, scope);
        Set<Dependency> dependencies = Sets.newLinkedHashSet();
        Collection<IdeLocalFileDependency> ideLocalFileDependencies = dependenciesExtractor.extractLocalFileDependencies(plusConfigurations, minusConfigurations);

        for (IdeLocalFileDependency fileDependency : ideLocalFileDependencies) {
            dependencies.add(toLibraryDependency(fileDependency, ideaModule, scope));
        }
        return dependencies;
    }

    private SingleEntryModuleLibrary toLibraryDependency(IdeLocalFileDependency fileDependency, IdeaModule ideaModule, GeneratedIdeaScope scope) {
        return new SingleEntryModuleLibrary(toPath(ideaModule, fileDependency.getFile()), scope.name());
    }

    public Collection<UnresolvedIdeRepoFileDependency> getUnresolvedDependencies(IdeaModule ideaModule) {
        Set<UnresolvedIdeRepoFileDependency> usedUnresolvedDependencies = Sets.newTreeSet(new Comparator<UnresolvedIdeRepoFileDependency>() {
            @Override
            public int compare(UnresolvedIdeRepoFileDependency left, UnresolvedIdeRepoFileDependency right) {
                return left.getDisplayName().compareTo(right.getDisplayName());
            }
        });

        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            Collection<Configuration> plusConfigurations = getPlusConfigurations(ideaModule, scope);
            Collection<Configuration> minusConfigurations = getMinusConfigurations(ideaModule, scope);
            usedUnresolvedDependencies.addAll(dependenciesExtractor.unresolvedExternalDependencies(plusConfigurations, minusConfigurations));
        }
        return usedUnresolvedDependencies;
    }

    private Collection<Configuration> getPlusConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        return getConfigurations(ideaModule, scope, SCOPE_PLUS);
    }

    private Collection<Configuration> getMinusConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        return getConfigurations(ideaModule, scope, SCOPE_MINUS);
    }

    private Collection<Configuration> getConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope, String plusMinus) {
        Map<String, Collection<Configuration>> plusMinusConfigurations = getPlusMinusConfigurations(ideaModule, scope);
        return plusMinusConfigurations.containsKey(plusMinus) ? plusMinusConfigurations.get(plusMinus) : Collections.<Configuration>emptyList();
    }

    private Map<String, Collection<Configuration>> getPlusMinusConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        Map<String, Collection<Configuration>> plusMinusConfigurations = ideaModule.getScopes().get(scope.name());
        return plusMinusConfigurations != null ? plusMinusConfigurations : Collections.<String, Collection<Configuration>>emptyMap();
    }

    private FilePath toPath(IdeaModule ideaModule, File file) {
        return file != null ? ideaModule.getPathFactory().path(file) : null;
    }
}
