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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * An {@link ArtifactToFileResolver} implementation that uses Ivy {@link DependencyResolver} instances from the {@link UserResolverChain} to download the artifact.
 */
public class IvyResolverBackedArtifactToFileResolver implements ArtifactToFileResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(IvyResolverBackedArtifactToFileResolver.class);

    private final ArtifactResolutionCache artifactResolutionCache;
    private final DependencyResolvers dependencyResolvers;
    private final CachePolicy cachePolicy;

    public IvyResolverBackedArtifactToFileResolver(DependencyResolvers dependencyResolvers, ArtifactResolutionCache artifactResolutionCache, CachePolicy cachePolicy) {
        this.dependencyResolvers = dependencyResolvers;
        this.artifactResolutionCache = artifactResolutionCache;
        this.cachePolicy = cachePolicy;
    }

    public File resolve(Artifact artifact) {
        List<DependencyResolver> artifactResolvers = dependencyResolvers.getArtifactResolversForModule(artifact.getModuleRevisionId());
        DownloadOptions downloadOptions = new DownloadOptions();
        LOGGER.debug("Attempting to download {} using resolvers {}", artifact, artifactResolvers);
        for (DependencyResolver resolver : artifactResolvers) {
            File artifactDownload = downloadWithCache(resolver, artifact, downloadOptions);
            if (artifactDownload != null) {
                return artifactDownload;
            }
        }

        return null;
    }

    private File downloadWithCache(DependencyResolver artifactResolver, Artifact artifact, DownloadOptions options) {
        if (dependencyResolvers.isLocalResolver(artifactResolver)) {
            return downloadFromResolver(artifactResolver, artifact, options).getLocalFile();
        }

        // Look in the cache for this resolver
        ArtifactResolutionCache.CachedArtifactResolution cachedArtifactResolution = artifactResolutionCache.getCachedArtifactResolution(artifactResolver, artifact.getId());
        if (cachedArtifactResolution != null) {
            if (cachedArtifactResolution.getArtifactFile() == null) {
                if (!cachePolicy.mustRefreshMissingArtifact(cachedArtifactResolution.getAgeMillis())) {
                    LOGGER.debug("Detected non-existence of artifact '{}' in resolver cache", artifact.getId());
                    return null;
                }
            // Need to check file existence since for changing modules the underlying cached artifact file will have been removed
            } else if (cachedArtifactResolution.getArtifactFile().exists()) {
                LOGGER.debug("Found artifact '{}' in resolver cache: {}", artifact.getId(), cachedArtifactResolution.getArtifactFile());
                return cachedArtifactResolution.getArtifactFile();
            }
        }

        return downloadArtifact(artifactResolver, artifact, options);
    }

    private File downloadArtifact(DependencyResolver artifactResolver, Artifact artifact, DownloadOptions options) {
        // Otherwise, do the actual download
        ArtifactDownloadReport artifactReport = downloadFromResolver(artifactResolver, artifact, options);
        if (downloadFailed(artifactReport)) {
            LOGGER.debug("Failed in attempt to resolve artifact '{}' using resolver: {}", artifact.getId(), artifactReport.getDownloadDetails());
            return null;
        }

        File artifactFile = artifactReport.getLocalFile();
        LOGGER.debug("Downloaded artifact '{}' from resolver: {}", artifact.getId(), artifactFile);
        return artifactResolutionCache.storeArtifactFile(artifactResolver, artifact.getId(), artifactFile);
    }

    private boolean downloadFailed(ArtifactDownloadReport artifactReport) {
        // Ivy reports FAILED with MISSING_ARTIFACT message when the artifact doesn't exist. We're happy to cache null in these cases.
        // For other failures (eg network failure) we want to try again - don't cache.
        return artifactReport.getDownloadStatus() == DownloadStatus.FAILED
                && !artifactReport.getDownloadDetails().equals(ArtifactDownloadReport.MISSING_ARTIFACT);
    }

    private ArtifactDownloadReport downloadFromResolver(DependencyResolver artifactResolver, Artifact artifact, DownloadOptions options) {
        DownloadReport downloadReport = artifactResolver.download(new Artifact[]{artifact}, options);
        return downloadReport.getArtifactReport(artifact);
    }
}
