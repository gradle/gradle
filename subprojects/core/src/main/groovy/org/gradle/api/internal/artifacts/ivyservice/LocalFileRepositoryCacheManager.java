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

import org.apache.ivy.core.cache.*;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.repository.ArtifactResourceResolver;
import org.apache.ivy.plugins.repository.ResourceDownloader;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;

import java.io.File;
import java.text.ParseException;

/**
 * A cache manager for local repositories. Doesn't cache anything, and uses artifacts from their origin.
 */
class LocalFileRepositoryCacheManager implements RepositoryCacheManager {
    private final String name;

    public LocalFileRepositoryCacheManager(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void saveResolvers(ModuleDescriptor descriptor, String metadataResolverName, String artifactResolverName) {
    }

    public ArtifactOrigin getSavedArtifactOrigin(Artifact artifact) {
        return null;
    }

    public ResolvedModuleRevision findModuleInCache(DependencyDescriptor dd, ModuleRevisionId requestedRevisionId, CacheMetadataOptions options, String expectedResolver) {
        return null;
    }

    public ArtifactDownloadReport download(Artifact artifact, ArtifactResourceResolver resourceResolver, ResourceDownloader resourceDownloader, CacheDownloadOptions options) {
        long start = System.currentTimeMillis();
        ArtifactDownloadReport report = new ArtifactDownloadReport(artifact);
        ResolvedResource resolvedResource = resourceResolver.resolve(artifact);
        if (resolvedResource == null) {
            report.setDownloadStatus(DownloadStatus.FAILED);
            report.setDownloadDetails(ArtifactDownloadReport.MISSING_ARTIFACT);
            report.setDownloadTimeMillis(System.currentTimeMillis() - start);
            return report;
        }
        assert resolvedResource.getResource().isLocal();
        File file = new File(resolvedResource.getResource().getName());
        assert file.isFile();

        ArtifactOrigin origin = new ArtifactOrigin(artifact, true, file.getAbsolutePath());
        report.setDownloadStatus(DownloadStatus.NO);
        report.setArtifactOrigin(origin);
        report.setSize(file.length());
        report.setLocalFile(file);
        return report;
    }

    public ResolvedModuleRevision cacheModuleDescriptor(DependencyResolver resolver, ResolvedResource orginalMetadataRef, DependencyDescriptor dd, Artifact requestedMetadataArtifact, ResourceDownloader downloader, CacheMetadataOptions options) throws ParseException {
        return null;
    }

    public void originalToCachedModuleDescriptor(DependencyResolver resolver, ResolvedResource orginalMetadataRef, Artifact requestedMetadataArtifact, ResolvedModuleRevision rmr, ModuleDescriptorWriter writer) {
    }

    public void clean() {
    }

    public void saveResolvedRevision(ModuleRevisionId dynamicMrid, String revision) {
    }
}
