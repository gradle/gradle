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
package org.gradle.api.internal.artifacts.repositories.legacy;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.cache.CacheDownloadOptions;
import org.apache.ivy.core.cache.CacheMetadataOptions;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.MetadataArtifactDownloadReport;
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
public class LocalFileRepositoryCacheManager extends AbstractRepositoryCacheManager {

    public LocalFileRepositoryCacheManager(String name) {
        super(name);
    }

    public EnhancedArtifactDownloadReport download(Artifact artifact, ArtifactResourceResolver resourceResolver, ResourceDownloader resourceDownloader, CacheDownloadOptions options) {
        ResolvedResource resolvedResource = resourceResolver.resolve(artifact);
        long start = System.currentTimeMillis();
        EnhancedArtifactDownloadReport report = new EnhancedArtifactDownloadReport(artifact);
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

    public ResolvedModuleRevision cacheModuleDescriptor(DependencyResolver resolver, ResolvedResource resolvedResource, DependencyDescriptor dd, Artifact moduleArtifact, ResourceDownloader downloader, CacheMetadataOptions options) throws ParseException {
        if (!moduleArtifact.isMetadata()) {
            return null;
        }

        assert resolvedResource.getResource().isLocal();
        File file = new File(resolvedResource.getResource().getName());
        assert file.isFile();

        ArtifactOrigin origin = new ArtifactOrigin(moduleArtifact, true, file.getAbsolutePath());
        MetadataArtifactDownloadReport report = new MetadataArtifactDownloadReport(moduleArtifact);
        report.setDownloadStatus(DownloadStatus.NO);
        report.setArtifactOrigin(origin);
        report.setSize(file.length());
        report.setLocalFile(file);
        report.setSearched(false);
        report.setOriginalLocalFile(file);

        ModuleDescriptor descriptor = parseModuleDescriptor(resolver, moduleArtifact, options, file, resolvedResource.getResource());
        return new ResolvedModuleRevision(resolver, resolver, descriptor, report);
    }
}
