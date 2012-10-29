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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.latest.ArtifactInfo;
import org.apache.ivy.plugins.latest.ComparatorLatestStrategy;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class UserResolverChain implements DependencyToModuleResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResolverChain.class);

    private final List<ModuleVersionRepository> moduleVersionRepositories = new ArrayList<ModuleVersionRepository>();
    private final List<String> moduleVersionRepositoryNames = new ArrayList<String>();
    private ResolverSettings settings;

    public void setSettings(ResolverSettings settings) {
        this.settings = settings;
    }

    public void add(ModuleVersionRepository repository) {
        moduleVersionRepositories.add(repository);
        moduleVersionRepositoryNames.add(repository.getName());
    }

    public void resolve(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionResolveResult result) {
        LOGGER.debug("Attempting to resolve module '{}' using repositories {}", dependencyDescriptor.getDependencyRevisionId(), moduleVersionRepositoryNames);
        List<Throwable> errors = new ArrayList<Throwable>();
        final ModuleResolution latestResolved = findLatestModule(dependencyDescriptor, errors);
        if (latestResolved != null) {
            final ModuleVersionDescriptor downloadedModule = latestResolved.module;
            LOGGER.debug("Using module '{}' from repository '{}'", downloadedModule.getId(), latestResolved.repository.getName());
            result.resolved(latestResolved.getId(), latestResolved.getDescriptor(), latestResolved.getArtifactResolver());
            return;
        }
        if (!errors.isEmpty()) {
            result.failed(new ModuleVersionResolveException(dependencyDescriptor.getDependencyRevisionId(), errors));
        } else {
            result.notFound(dependencyDescriptor.getDependencyRevisionId());
        }
    }

    private ModuleResolution findLatestModule(DependencyDescriptor dependencyDescriptor, Collection<Throwable> failures) {
        boolean isStaticVersion = !settings.getVersionMatcher().isDynamic(dependencyDescriptor.getDependencyRevisionId());
        
        ModuleResolution best = null;
        for (ModuleVersionRepository repository : moduleVersionRepositories) {
            try {
                ModuleVersionDescriptor module = repository.getDependency(dependencyDescriptor);
                if (module != null) {
                    ModuleResolution moduleResolution = new ModuleResolution(repository, module);
                    if (isStaticVersion && !moduleResolution.isGeneratedModuleDescriptor()) {
                        return moduleResolution;
                    }
                    best = chooseBest(best, moduleResolution);
                }
            } catch (Throwable e) {
                failures.add(e);
            }
        }

        return best;
    }

    private ModuleResolution chooseBest(ModuleResolution one, ModuleResolution two) {
        if (one == null || two == null) {
            return two == null ? one : two;
        }
        if (one.module == null || two.module == null) {
            return two.module == null ? one : two;
        }

        ComparatorLatestStrategy latestStrategy = (ComparatorLatestStrategy) settings.getDefaultLatestStrategy();
        Comparator<ArtifactInfo> comparator = latestStrategy.getComparator();
        int comparison = comparator.compare(one, two);

        if (comparison == 0) {
            if (one.isGeneratedModuleDescriptor() && !two.isGeneratedModuleDescriptor()) {
                return two;
            }
            return one;
        }

        return comparison < 0 ? two : one;
    }

    private static class ModuleResolution implements ArtifactInfo {
        public final ModuleVersionRepository repository;
        public final ModuleVersionDescriptor module;

        public ModuleResolution(ModuleVersionRepository repository, ModuleVersionDescriptor module) {
            this.repository = repository;
            this.module = module;
        }

        public ModuleRevisionId getId() throws ModuleVersionResolveException {
            return module.getId();
        }

        public ModuleDescriptor getDescriptor() throws ModuleVersionResolveException {
            return module.getDescriptor();
        }

        public ArtifactResolver getArtifactResolver() throws ModuleVersionResolveException {
            return new ModuleVersionRepositoryBackedArtifactResolver(repository);
        }

        public boolean isGeneratedModuleDescriptor() {
            return module.getDescriptor().isDefault();
        }

        public long getLastModified() {
            return module.getDescriptor().getResolvedPublicationDate().getTime();
        }

        public String getRevision() {
            return module.getId().getRevision();
        }
    }

    private static final class ModuleVersionRepositoryBackedArtifactResolver implements ArtifactResolver {
        private final ModuleVersionRepository repository;

        private ModuleVersionRepositoryBackedArtifactResolver(ModuleVersionRepository repository) {
            this.repository = repository;
        }

        public ArtifactResolveResult resolve(Artifact artifact) {
            LOGGER.debug("Attempting to download {} using repository '{}'", artifact, repository.getName());
            ArtifactResolveResult result;
            try {
                result = repository.download(artifact);
            } catch (ArtifactResolveException e) {
                return new BrokenArtifactResolveResult(e);
            }
            if (result == null) {
                return new BrokenArtifactResolveResult(new ArtifactNotFoundException(artifact));
            }
            return result;
        }
    }
}
