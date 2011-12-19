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
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.IvySettingsAware;
import org.apache.ivy.plugins.latest.ArtifactInfo;
import org.apache.ivy.plugins.latest.ComparatorLatestStrategy;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.StringUtils;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactToFileResolver;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactFileStore;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class UserResolverChain implements ArtifactToFileResolver, IvySettingsAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResolverChain.class);

    private final Map<ModuleRevisionId, ModuleVersionRepository> artifactRepositories = new HashMap<ModuleRevisionId, ModuleVersionRepository>();
    private final ModuleResolutionCache moduleResolutionCache;
    private final ModuleDescriptorCache moduleDescriptorCache;
    private final ArtifactResolutionCache artifactResolutionCache;
    private final ArtifactFileStore artifactFileStore;
    private final List<DependencyResolver> resolvers = new ArrayList<DependencyResolver>();
    private final List<ModuleVersionRepository> moduleVersionRepositories = new ArrayList<ModuleVersionRepository>();
    private IvySettings settings;
    private CachePolicy cachePolicy;

    public UserResolverChain(ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache, ArtifactResolutionCache artifactResolutionCache, ArtifactFileStore artifactFileStore) {
        this.moduleDescriptorCache = moduleDescriptorCache;
        this.moduleResolutionCache = moduleResolutionCache;
        this.artifactResolutionCache = artifactResolutionCache;
        this.artifactFileStore = artifactFileStore;
    }

    public void setSettings(IvySettings settings) {
        this.settings = settings;
    }

    public void setCachePolicy(CachePolicy cachePolicy) {
        this.cachePolicy = cachePolicy;
    }

    public void add(DependencyResolver resolver) {
        if (!(resolver instanceof ModuleVersionRepository)) {
            throw new IllegalArgumentException("Can only add ModuleVersionRepository instances.");
        }
        resolvers.add(resolver);
        ModuleVersionRepository cachingRepository = new CachingModuleVersionRepository((ModuleVersionRepository) resolver,
                moduleResolutionCache, moduleDescriptorCache, artifactResolutionCache, artifactFileStore, cachePolicy);
        moduleVersionRepositories.add(cachingRepository);
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) {
        ModuleResolution latestResolved = findLatestModule(dd, data);
        if (latestResolved != null) {
            ResolvedModuleRevision downloadedModule = latestResolved.module;
            LOGGER.debug("Found module '{}' using resolver '{}'", downloadedModule, latestResolved.repository);
            rememberResolverToUseForArtifactDownload(latestResolved.repository, downloadedModule);
            return downloadedModule;
        }
        return null;
    }

    private ModuleResolution findLatestModule(DependencyDescriptor dependencyDescriptor, ResolveData resolveData) {
        List<RuntimeException> errors = new ArrayList<RuntimeException>();
        boolean isStaticVersion = !settings.getVersionMatcher().isDynamic(dependencyDescriptor.getDependencyRevisionId());
        
        ModuleResolution best = null;
        for (ModuleVersionRepository repository : moduleVersionRepositories) {
            try {
                ResolvedModuleRevision module = repository.getDependency(dependencyDescriptor, resolveData);
                if (module != null) {
                    ModuleResolution moduleResolution = new ModuleResolution(repository, module);
                    if (isStaticVersion && !moduleResolution.isGeneratedModuleDescriptor()) {
                        return moduleResolution;
                    }
                    best = chooseBest(best, moduleResolution);
                }
            } catch (RuntimeException e) {
                errors.add(e);
            }
        }

        if (best == null && !errors.isEmpty()) {
            throwResolutionFailure(errors);
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

    private void rememberResolverToUseForArtifactDownload(ModuleVersionRepository repository, ResolvedModuleRevision cachedModule) {
        artifactRepositories.put(cachedModule.getId(), repository);
    }

    private void throwResolutionFailure(List<RuntimeException> errors) {
        if (errors.size() == 1) {
            throw errors.get(0);
        } else {
            StringBuilder err = new StringBuilder();
            for (Exception ex : errors) {
                err.append("\t").append(StringUtils.getErrorMessage(ex)).append("\n");
            }
            err.setLength(err.length() - 1);
            throw new RuntimeException("several problems occurred while resolving :\n" + err);
        }
    }

    private List<ModuleVersionRepository> getArtifactResolversForModule(ModuleRevisionId moduleRevisionId) {
        ModuleVersionRepository moduleDescriptorRepository = artifactRepositories.get(moduleRevisionId);
        if (moduleDescriptorRepository != null && moduleDescriptorRepository != this) {
            return Collections.singletonList(moduleDescriptorRepository);
        }
        return moduleVersionRepositories;
    }

    public File resolve(Artifact artifact) {
        ArtifactResolutionExceptionBuilder exceptionBuilder = new ArtifactResolutionExceptionBuilder(artifact);

        List<ModuleVersionRepository> artifactRepositories = getArtifactResolversForModule(artifact.getModuleRevisionId());
        DownloadOptions downloadOptions = new DownloadOptions();
        LOGGER.debug("Attempting to download {} using resolvers {}", artifact, artifactRepositories);
        for (ModuleVersionRepository resolver : artifactRepositories) {
            try {
                File artifactDownload = resolver.download(artifact, downloadOptions);
                if (artifactDownload != null) {
                    return artifactDownload;
                }
            } catch (ArtifactResolveException e) {
                LOGGER.warn(e.getMessage());
                exceptionBuilder.addDownloadFailure(e);
            }
        }

        throw exceptionBuilder.buildException();
    }

    public void clearResolvers() {
        resolvers.clear();
        moduleVersionRepositories.clear();
    }
    
    public List<DependencyResolver> getResolvers() {
        return resolvers;
    }

    private class ModuleResolution implements ArtifactInfo {
        public final ModuleVersionRepository repository;
        public final ResolvedModuleRevision module;

        public ModuleResolution(ModuleVersionRepository repository, ResolvedModuleRevision module) {
            this.repository = repository;
            this.module = module;
        }

        public boolean isGeneratedModuleDescriptor() {
            if (module == null) {
                throw new IllegalStateException();
            }
            return module.getDescriptor().isDefault();
        }

        public long getLastModified() {
            return module.getPublicationDate().getTime();
        }

        public String getRevision() {
            return module.getId().getRevision();
        }
    }
}
