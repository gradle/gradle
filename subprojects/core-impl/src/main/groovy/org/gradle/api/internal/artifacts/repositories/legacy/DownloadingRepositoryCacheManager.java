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
import org.apache.ivy.core.cache.DownloadListener;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.MetadataArtifactDownloadReport;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.repository.ArtifactResourceResolver;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.ResourceDownloader;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.Factory;
import org.gradle.internal.filestore.FileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

/**
 * A cache manager for remote repositories, that downloads files and stores them in the FileStore provided.
 */
public class DownloadingRepositoryCacheManager extends AbstractRepositoryCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadingRepositoryCacheManager.class);

    private final FileStore<ModuleVersionArtifactMetaData> fileStore;
    private final TemporaryFileProvider temporaryFileProvider;
    private final CacheLockingManager cacheLockingManager;

    public DownloadingRepositoryCacheManager(String name, FileStore<ModuleVersionArtifactMetaData> fileStore,
                                             TemporaryFileProvider temporaryFileProvider, CacheLockingManager cacheLockingManager) {
        super(name);
        this.fileStore = fileStore;
        this.temporaryFileProvider = temporaryFileProvider;
        this.cacheLockingManager = cacheLockingManager;
    }

    public boolean isLocal() {
        return false;
    }

    public EnhancedArtifactDownloadReport download(Artifact artifact, ArtifactResourceResolver resourceResolver,
                                                   ResourceDownloader resourceDownloader, CacheDownloadOptions options) {
        EnhancedArtifactDownloadReport adr = new EnhancedArtifactDownloadReport(artifact);

        DownloadListener listener = options.getListener();
        if (listener != null) {
            listener.needArtifact(this, artifact);
        }

        long start = System.currentTimeMillis();
        try {
            ResolvedResource artifactRef = resourceResolver.resolve(artifact);
            if (artifactRef != null) {
                final Resource resource = artifactRef.getResource();
                ArtifactOrigin origin = new ArtifactOrigin(artifact, resource.isLocal(), resource.getName());
                if (listener != null) {
                    listener.startArtifactDownload(this, artifactRef, artifact, origin);
                }

                ModuleRevisionId moduleRevisionId = artifact.getModuleRevisionId();
                ModuleComponentIdentifier componentIdentifier = DefaultModuleComponentIdentifier.newId(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
                File artifactFile = downloadAndCacheArtifactFile(new DefaultModuleVersionArtifactMetaData(componentIdentifier, artifact), artifact, resourceDownloader, artifactRef.getResource());

                adr.setDownloadTimeMillis(System.currentTimeMillis() - start);
                adr.setSize(artifactFile.length());
                adr.setDownloadStatus(DownloadStatus.SUCCESSFUL);
                adr.setArtifactOrigin(origin);
                adr.setLocalFile(artifactFile);
            } else {
                adr.setDownloadTimeMillis(System.currentTimeMillis() - start);
                adr.setDownloadStatus(DownloadStatus.FAILED);
                adr.setDownloadDetails(ArtifactDownloadReport.MISSING_ARTIFACT);
            }
        } catch (Throwable throwable) {
            adr.setDownloadTimeMillis(System.currentTimeMillis() - start);
            adr.failed(throwable);
        }
        if (listener != null) {
            listener.endArtifactDownload(this, artifact, adr, adr.getLocalFile());
        }
        return adr;
    }

    private File downloadAndCacheArtifactFile(final ModuleVersionArtifactMetaData id, Artifact artifact, ResourceDownloader resourceDownloader, Resource resource) throws IOException {
        final File tmpFile = temporaryFileProvider.createTemporaryFile("gradle_download", "bin");
        try {
            resourceDownloader.download(artifact, resource, tmpFile);
            return cacheLockingManager.useCache(String.format("Store %s", id), new Factory<File>() {
                public File create() {
                    return fileStore.move(id, tmpFile).getFile();
                }
            });
        } finally {
            tmpFile.delete();
        }
    }

    public ResolvedModuleRevision cacheModuleDescriptor(DependencyResolver resolver, final ResolvedResource resolvedResource, DependencyDescriptor dd, Artifact moduleArtifact, ResourceDownloader downloader, CacheMetadataOptions options) throws ParseException {
        if (!moduleArtifact.isMetadata()) {
            return null;
        }

        ArtifactResourceResolver artifactResourceResolver = new ArtifactResourceResolver() {
            public ResolvedResource resolve(Artifact artifact) {
                return resolvedResource;
            }
        };
        ArtifactDownloadReport report = download(moduleArtifact, artifactResourceResolver, downloader, new CacheDownloadOptions().setListener(options.getListener()).setForce(true));

        if (report.getDownloadStatus() == DownloadStatus.FAILED) {
            LOGGER.warn("problem while downloading module descriptor: {}: {} ({} ms)", resolvedResource.getResource(), report.getDownloadDetails(), report.getDownloadTimeMillis());
            return null;
        }

        ModuleDescriptor md = parseModuleDescriptor(resolver, moduleArtifact, options, report.getLocalFile(), resolvedResource.getResource());
        LOGGER.debug("\t{}: parsed downloaded md file for {}; parsed={}" + getName(), moduleArtifact.getModuleRevisionId(), md.getModuleRevisionId());

        MetadataArtifactDownloadReport madr = new MetadataArtifactDownloadReport(md.getMetadataArtifact());
        madr.setSearched(true);
        madr.setDownloadStatus(report.getDownloadStatus());
        madr.setDownloadDetails(report.getDownloadDetails());
        madr.setArtifactOrigin(report.getArtifactOrigin());
        madr.setDownloadTimeMillis(report.getDownloadTimeMillis());
        madr.setOriginalLocalFile(report.getLocalFile());
        madr.setSize(report.getSize());

        return new ResolvedModuleRevision(resolver, resolver, md, madr);
    }

}
