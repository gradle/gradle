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
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.GradleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CacheFirstChainResolver extends ChainResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheFirstChainResolver.class);
    
    private final Map<ModuleRevisionId, DependencyResolver> artifactResolvers = new HashMap<ModuleRevisionId, DependencyResolver>();

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data)
            throws ParseException {

        // First attempt to locate the module in a resolver cache
        for (DependencyResolver resolver : getResolvers()) {
            ResolvedModuleRevision cachedModule = findModuleInCache(resolver, dd, data);
            if (cachedModule != null) {
                LOGGER.debug("Found module {} in resolver cache {}", cachedModule, resolver.getName());
                artifactResolvers.put(cachedModule.getId(), resolver);
                return cachedModule;
            }
        }

        // Otherwise delegate to the regular chain (each resolver will re-check cache)
        ResolvedModuleRevision downloadedModule = super.getDependency(dd, data);
        if (downloadedModule != null) {
            LOGGER.debug("Found module {} using resolver {}", downloadedModule, downloadedModule.getArtifactResolver());
            artifactResolvers.put(downloadedModule.getId(), downloadedModule.getArtifactResolver());
        }
        return downloadedModule;
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

    @Override
    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        DownloadReport overallReport = new DownloadReport();
        for (Artifact artifact : artifacts) {
            DependencyResolver artifactResolver = artifactResolvers.get(artifact.getModuleRevisionId());
            ArtifactDownloadReport artifactDownloadReport;
            // If possible, download from same artifactResolver that provided meta-data
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
}
