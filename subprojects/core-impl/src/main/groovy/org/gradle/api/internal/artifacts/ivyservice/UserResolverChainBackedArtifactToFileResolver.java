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
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * An {@link ArtifactToFileResolver} implementation that uses Ivy {@link DependencyResolver} instances from the {@link UserResolverChain} to download the artifact.
 */
public class UserResolverChainBackedArtifactToFileResolver implements ArtifactToFileResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserResolverChainBackedArtifactToFileResolver.class);

    private final ArtifactResolutionCache artifactResolutionCache;
    private final UserResolverChain userResolverChain;

    public UserResolverChainBackedArtifactToFileResolver(UserResolverChain userResolverChain, ArtifactResolutionCache artifactResolutionCache) {
        this.userResolverChain = userResolverChain;
        this.artifactResolutionCache = artifactResolutionCache;
    }

    public File resolve(Artifact artifact) {
        DependencyResolver artifactResolver = userResolverChain.getResolver(artifact.getModuleRevisionId());
        if (artifactResolver != null && artifactResolver != this) {
            return downloadFromSingleRepository(artifactResolver, artifact);
        } else {
            return downloadFromAnyRepository(artifact);
        }
    }

    private File downloadFromSingleRepository(DependencyResolver artifactResolver, Artifact artifact) {
        LOGGER.debug("Attempting to download artifact {} using resolver {}", artifact, artifactResolver);
        return downloadWithCache(artifactResolver, artifact, new DownloadOptions());
    }

    private File downloadFromAnyRepository(Artifact artifact) {
        // Check all of the resolvers in turn, stopping at the first successful match: this is used for Client Modules where the module was never resolved
        // TODO Try all repositories for cached artifact first
        DownloadOptions downloadOptions = new DownloadOptions();
        LOGGER.debug("Attempting to download {} using all resolvers", artifact);
        for (DependencyResolver resolver : userResolverChain.getResolvers()) {
            File artifactDownload = downloadWithCache(resolver, artifact, downloadOptions);
            if (artifactDownload != null) {
                return artifactDownload;
            }
        }

        return null;
    }

    private File downloadWithCache(DependencyResolver artifactResolver, Artifact artifact, DownloadOptions options) {
        if (artifactResolver.getRepositoryCacheManager() instanceof LocalFileRepositoryCacheManager) {
            return downloadWithoutCache(artifactResolver, artifact, options);
        }

        // Look in the cache for this resolver
        ArtifactResolutionCache.CachedArtifactResolution cachedArtifactResolution = artifactResolutionCache.getCachedArtifactResolution(artifactResolver, artifact.getId());
        if (cachedArtifactResolution != null) {
            // TODO:DAZ Expire these entries (artifact was missing from resolver)
            if (cachedArtifactResolution.getArtifactFile() == null) {
                LOGGER.debug("Detected non-existence of artifact '{}' in resolver cache", artifact.getId());
                return null;
            }

            // For changing modules, the underlying cached artifact file will have been removed
            if (cachedArtifactResolution.getArtifactFile().exists()) {
                LOGGER.debug("Found artifact '{}' in resolver cache: {}", artifact.getId(), cachedArtifactResolution.getArtifactFile());
                return cachedArtifactResolution.getArtifactFile();
            }
        }

        // Otherwise, do the actual download
        File artifactFile = downloadWithoutCache(artifactResolver, artifact, options);
        LOGGER.debug("Downloaded artifact '{}' from resolver: {}", artifact.getId(), artifactFile);
        artifactResolutionCache.recordArtifactResolution(artifactResolver, artifact.getId(), artifactFile);
        return artifactFile;
    }

    private File downloadWithoutCache(DependencyResolver artifactResolver, Artifact artifact, DownloadOptions options) {
        DownloadReport downloadReport = artifactResolver.download(new Artifact[]{artifact}, options);
        return downloadReport.getArtifactReport(artifact).getLocalFile();
    }
}
