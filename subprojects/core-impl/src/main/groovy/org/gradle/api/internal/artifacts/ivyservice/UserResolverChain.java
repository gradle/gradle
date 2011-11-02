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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.cache.CacheMetadataOptions;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.latest.ArtifactInfo;
import org.apache.ivy.plugins.latest.ComparatorLatestStrategy;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.DynamicVersionCachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DynamicVersionCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ForceChangeDependencyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.*;

public class UserResolverChain extends ChainResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResolverChain.class);

    private final Map<ModuleRevisionId, DependencyResolver> artifactResolvers = new HashMap<ModuleRevisionId, DependencyResolver>();
    private final DynamicRevisionDependencyConverter dynamicRevisions;

    public UserResolverChain(DynamicVersionCache dynamicVersionCache) {
        dynamicRevisions = new DynamicRevisionDependencyConverter(dynamicVersionCache);
    }

    public void setDynamicRevisionCachePolicy(DynamicVersionCachePolicy dynamicVersionCachePolicy) {
        dynamicRevisions.setDynamicRevisionCachePolicy(dynamicVersionCachePolicy);
    }

    @Override
    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) {

        List<ModuleResolution> resolutionList = createResolutionList(dd, data);

        ModuleResolution latestCached = lookupAllInCacheAndGetLatest(resolutionList);
        if (latestCached != null) {
            LOGGER.debug("Found module {} in resolver cache {}", latestCached.getModule(), latestCached.resolver.getName());
            rememberResolverToUseForArtifactDownload(latestCached.resolver, latestCached.getModule());
            return latestCached.getModule();
        }

        // Otherwise delegate to each resolver in turn
        ModuleResolution latestResolved = resolveLatestModule(resolutionList);
        if (latestResolved != null) {
            ResolvedModuleRevision downloadedModule = latestResolved.getModule();
            LOGGER.debug("Found module {} using resolver {}", downloadedModule, downloadedModule.getArtifactResolver());
            rememberResolverToUseForArtifactDownload(downloadedModule.getArtifactResolver(), downloadedModule);
            return downloadedModule;
        }
        return null;
    }

    private List<ModuleResolution> createResolutionList(DependencyDescriptor dd, ResolveData data) {
        boolean staticVersion = !getSettings().getVersionMatcher().isDynamic(dd.getDependencyRevisionId());
        List<ModuleResolution> resolutionList = new ArrayList<ModuleResolution>();
        for (DependencyResolver resolver : getResolvers()) {
            resolutionList.add(new ModuleResolution(resolver, dd, data, staticVersion));
        }
        return resolutionList;
    }

    private ModuleResolution lookupAllInCacheAndGetLatest(List<ModuleResolution> resolutionList) {
        for (ModuleResolution moduleResolution : resolutionList) {
            moduleResolution.lookupModuleInCache();
        }

        return chooseBestResult(resolutionList);
    }

    private ModuleResolution resolveLatestModule(List<ModuleResolution> resolutionList) {

        List<RuntimeException> errors = new ArrayList<RuntimeException>();
        for (ModuleResolution moduleResolution : resolutionList) {
            try {
                moduleResolution.resolveModule();
                if (moduleResolution.getModule() != null && moduleResolution.isStaticVersion() && !moduleResolution.isGeneratedModuleDescriptor())
                {
                    return moduleResolution;
                }
            } catch (RuntimeException e) {
                errors.add(e);
            }
        }

        ModuleResolution mr = chooseBestResult(resolutionList);
        if (mr == null && !errors.isEmpty()) {
            throwResolutionFailure(errors);
        }
        return mr;
    }

    private ModuleResolution chooseBestResult(List<ModuleResolution> resolutionList) {
        ModuleResolution best = null;
        for (ModuleResolution moduleResolution : resolutionList) {
            best = chooseBest(best, moduleResolution);
        }
        if (best == null || best.getModule() == null) {
            return null;
        }
        return best;
    }
    
    private ModuleResolution chooseBest(ModuleResolution one, ModuleResolution two) {
        if (one == null || two == null) {
            return two == null ? one : two;
        }
        if (one.getModule() == null || two.getModule() == null) {
            return two.getModule() == null ? one : two;
        }

        ComparatorLatestStrategy latestStrategy = (ComparatorLatestStrategy) getLatestStrategy();
        Comparator<ArtifactInfo> comparator = latestStrategy.getComparator();
        int comparison = comparator.compare(one, two);

        if (comparison == 0) {
            if (one.isGeneratedModuleDescriptor() && !two.isGeneratedModuleDescriptor()) {
                return two;
            }
            return one;
        }

        return comparison < 0 ? one : two;
    }

    private void rememberResolverToUseForArtifactDownload(DependencyResolver resolver, ResolvedModuleRevision cachedModule) {
        artifactResolvers.put(cachedModule.getId(), resolver);
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

    @Override
    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        DownloadReport overallReport = new DownloadReport();
        for (Artifact artifact : artifacts) {
            DependencyResolver artifactResolver = artifactResolvers.get(artifact.getModuleRevisionId());
            ArtifactDownloadReport artifactDownloadReport;
            if (artifactResolver != null && artifactResolver != this) {
                artifactDownloadReport = downloadFromSingleRepository(artifactResolver, artifact, options);
            } else {
                artifactDownloadReport = downloadFromAnyRepository(artifact, options);
            }
            overallReport.addArtifactReport(artifactDownloadReport);
        }
        return overallReport;
     }

    private ArtifactDownloadReport downloadFromSingleRepository(DependencyResolver artifactResolver, Artifact artifact, DownloadOptions options) {
        LOGGER.debug("Attempting to download artifact {} using resolver {}", artifact, artifactResolver);
        DownloadReport downloadReport = artifactResolver.download(new Artifact[]{artifact}, options);
        return downloadReport.getArtifactReport(artifact);
    }

    private ArtifactDownloadReport downloadFromAnyRepository(Artifact artifact, DownloadOptions options) {
        // Check all of the resolvers in turn, stopping at the first successful match
        Artifact[] singleArtifact = {artifact};
        // TODO Try all repositories for cached artifact first
        LOGGER.debug("Attempting to download {} using all resolvers", artifact);
        for (DependencyResolver resolver : getResolvers()) {
            DownloadReport downloadReport = resolver.download(singleArtifact, options);
            ArtifactDownloadReport artifactDownload = downloadReport.getArtifactReport(artifact);
            if (artifactDownload.getDownloadStatus() != DownloadStatus.FAILED) {
                return artifactDownload;
            }
        }

        ArtifactDownloadReport failedDownload = new ArtifactDownloadReport(artifact);
        failedDownload.setDownloadStatus(DownloadStatus.FAILED);
        return failedDownload;
    }

    @Override
    public List<DependencyResolver> getResolvers() {
        return super.getResolvers();
    }

    private static class DynamicRevisionDependencyConverter {
        private final DynamicVersionCache dynamicVersionCache;
        private DynamicVersionCachePolicy dynamicVersionCachePolicy;

        private DynamicRevisionDependencyConverter(DynamicVersionCache dynamicVersionCache) {
            this.dynamicVersionCache = dynamicVersionCache;
        }

        public void setDynamicRevisionCachePolicy(DynamicVersionCachePolicy dynamicVersionCachePolicy) {
            this.dynamicVersionCachePolicy = dynamicVersionCachePolicy;
        }

        public void maybeSaveDynamicRevision(DependencyDescriptor original, ResolvedModuleRevision downloadedModule) {
            if (downloadedModule == null) {
                return;
            }

            ModuleRevisionId originalId = original.getDependencyRevisionId();
            ModuleRevisionId resolvedId = downloadedModule.getId();
            if (originalId.equals(resolvedId) && !isChanging(original)) {
                return;
            }

            LOGGER.debug("Caching resolved revision in dynamic revision cache: Will use '{}' for '{}'", resolvedId, originalId);
            dynamicVersionCache.saveResolvedDynamicVersion(downloadedModule.getResolver(), originalId, resolvedId);
        }

        public DependencyDescriptor maybeResolveDynamicRevision(DependencyResolver resolver, DependencyDescriptor original) {
            assert dynamicVersionCachePolicy != null : "dynamicRevisionExpiryPolicy was not configured";

            ModuleRevisionId originalId = original.getDependencyRevisionId();
            DynamicVersionCache.ResolvedDynamicVersion resolvedRevision = dynamicVersionCache.getResolvedDynamicVersion(resolver, originalId);
            if (resolvedRevision == null) {
                return tweakSnapshot(original);
            }
            if (dynamicVersionCachePolicy.mustCheckForUpdates(resolvedRevision.getModule(), resolvedRevision.getAgeMillis())) {
                LOGGER.debug("Resolved revision in dynamic revision cache is expired: will perform fresh resolve of '{}'", originalId);
                return tweakSnapshot(original);
            }
            
            if (originalId.equals(resolvedRevision.getRevision())) {
                LOGGER.debug("Found cached version of changing module: Using cached metadata for '{}'", originalId);
                return ForceChangeDependencyDescriptor.forceChangingFlag(original, false);
            }

            LOGGER.debug("Found resolved revision in dynamic revision cache: Using '{}' for '{}'", resolvedRevision.getRevision(), originalId);
            return original.clone(resolvedRevision.getRevision());
        }

        // TODO:DAZ Check for SNAPSHOT should be done inside MavenRepositoryResolver, which can set the changing flag on descriptor if required
        private boolean isChanging(DependencyDescriptor descriptor) {
            return descriptor.isChanging() || descriptor.getDependencyRevisionId().getRevision().endsWith("SNAPSHOT");
        }

        // TODO:DAZ Setting changing flag for SNAPSHOT should be done inside MavenRepositoryResolver
        private DependencyDescriptor tweakSnapshot(DependencyDescriptor original) {
            if (original.getDependencyRevisionId().getRevision().endsWith("SNAPSHOT")) {
                return ForceChangeDependencyDescriptor.forceChangingFlag(original, true);
            }
            return original;
        }
    }

    private class ModuleResolution implements ArtifactInfo {
        private final DependencyResolver resolver;
        private final DependencyDescriptor descriptor;
        private final ResolveData resolveData;
        private final boolean staticVersion;
        private ResolvedModuleRevision resolvedModule;
        private DependencyDescriptor resolvedDescriptor;

        public ModuleResolution(DependencyResolver resolver, DependencyDescriptor moduleDescriptor, ResolveData resolveData, boolean staticVersion) {
            this.resolver = resolver;
            this.descriptor = moduleDescriptor;
            this.resolveData = resolveData;
            this.staticVersion = staticVersion;
        }

        public boolean isStaticVersion() {
            return staticVersion;
        }
        
        public boolean isGeneratedModuleDescriptor() {
            if (getModule() == null) {
                throw new IllegalStateException();
            }
            return getModule().getDescriptor().isDefault();
        }

        public void lookupModuleInCache() {
            resolvedDescriptor = dynamicRevisions.maybeResolveDynamicRevision(resolver, descriptor);
            resolvedModule = findModuleInCache(resolver, resolvedDescriptor, resolveData);
        }
        
        public void resolveModule() {
            try {
                resolvedModule = resolver.getDependency(resolvedDescriptor, resolveData);
                dynamicRevisions.maybeSaveDynamicRevision(descriptor, resolvedModule);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        public ResolvedModuleRevision getModule() {
            return resolvedModule;
        }

        private ResolvedModuleRevision findModuleInCache(DependencyResolver resolver, DependencyDescriptor dd, ResolveData resolveData) {
            CacheMetadataOptions cacheOptions = getCacheMetadataOptions(resolver, resolveData);
            return resolver.getRepositoryCacheManager().findModuleInCache(dd, dd.getDependencyRevisionId(), cacheOptions, resolver.getName());
        }

        private CacheMetadataOptions getCacheMetadataOptions(DependencyResolver resolver, ResolveData resolveData) {
            if (resolver instanceof AbstractResolver) {
                try {
                    Method method = AbstractResolver.class.getDeclaredMethod("getCacheOptions", ResolveData.class);
                    method.setAccessible(true);
                    return (CacheMetadataOptions) method.invoke(resolver, resolveData);
                } catch (Exception e) {
                    throw new GradleException("Could not get cache options from AbstractResolver", e);
                }
            }
            return new CacheMetadataOptions();
        }

        public long getLastModified() {
            return resolvedModule.getPublicationDate().getTime();
        }

        public String getRevision() {
            return resolvedModule.getId().getRevision();
        }
    }
}
