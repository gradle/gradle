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

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.cache.*;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.MetadataArtifactDownloadReport;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.IvySettingsAware;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.repository.ArtifactResourceResolver;
import org.apache.ivy.plugins.repository.ResourceDownloader;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.util.Message;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

/**
 * A cache manager for remote repositories.
 */
public class RemoteFileRepositoryCacheManager implements RepositoryCacheManager, IvySettingsAware {
    private static final String DEFAULT_ARTIFACT_PATTERN =
            "[organisation]/[module](/[branch])/[revision]/[type]/[artifact]-[revision](-[classifier])(.[ext])";

    private final String name;
    private final File cacheRoot;
    private IvySettings settings;


    public RemoteFileRepositoryCacheManager(String name, File cacheRoot) {
        this.name = name;
        this.cacheRoot = cacheRoot;
    }

    public void setSettings(IvySettings settings) {
        this.settings = settings;
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

    public void originalToCachedModuleDescriptor(DependencyResolver resolver, ResolvedResource originalMetadataRef, Artifact requestedMetadataArtifact, ResolvedModuleRevision rmr, ModuleDescriptorWriter writer) {
    }

    public void clean() {
    }

    public void saveResolvedRevision(ModuleRevisionId dynamicMrid, String revision) {
    }

    public ArtifactDownloadReport download(Artifact artifact, ArtifactResourceResolver resourceResolver,
                                           ResourceDownloader resourceDownloader, CacheDownloadOptions options) {
        final ArtifactDownloadReport adr = new ArtifactDownloadReport(artifact);

        DownloadListener listener = options.getListener();
        if (listener != null) {
            listener.needArtifact(this, artifact);
        }

        long start = System.currentTimeMillis();
        File archiveFile = getArchiveFileInCache(artifact);
        try {
            ResolvedResource artifactRef = resourceResolver.resolve(artifact);
            if (artifactRef != null) {
                ArtifactOrigin origin = new ArtifactOrigin(artifact, artifactRef.getResource().isLocal(), artifactRef.getResource().getName());
                if (listener != null) {
                    listener.startArtifactDownload(this, artifactRef, artifact, origin);
                }

                resourceDownloader.download(artifact, artifactRef.getResource(), archiveFile);

                adr.setSize(archiveFile.length());
                adr.setDownloadTimeMillis(System.currentTimeMillis() - start);
                adr.setDownloadStatus(DownloadStatus.SUCCESSFUL);
                adr.setArtifactOrigin(origin);
                adr.setLocalFile(archiveFile);
            } else {
                adr.setDownloadStatus(DownloadStatus.FAILED);
                adr.setDownloadDetails(ArtifactDownloadReport.MISSING_ARTIFACT);
                adr.setDownloadTimeMillis(System.currentTimeMillis() - start);
            }
        } catch (Exception ex) {
            adr.setDownloadStatus(DownloadStatus.FAILED);
            adr.setDownloadDetails(ex.getMessage());
            adr.setDownloadTimeMillis(System.currentTimeMillis() - start);
        }
        if (listener != null) {
            listener.endArtifactDownload(this, artifact, adr, archiveFile);
        }
        return adr;
    }

    public File getArchiveFileInCache(Artifact artifact) {
        return new File(cacheRoot, getArchivePathInCache(artifact));
    }

    public String getArchivePathInCache(Artifact artifact) {
        return IvyPatternHelper.substitute(DEFAULT_ARTIFACT_PATTERN, artifact);
    }

    public ResolvedModuleRevision cacheModuleDescriptor(DependencyResolver resolver, final ResolvedResource mdRef, DependencyDescriptor dd, Artifact moduleArtifact, ResourceDownloader downloader, CacheMetadataOptions options)
            throws ParseException {
        // TODO:DAZ Handle caching of artifacts of changing modules - they will no longer be deleted
        // TODO:DAZ locking
        if (!moduleArtifact.isMetadata()) {
            return null;
        }

        try {
            ArtifactResourceResolver artifactResourceResolver = new ArtifactResourceResolver() {
                public ResolvedResource resolve(Artifact artifact) {
                    return mdRef;
                }
            };
            ArtifactDownloadReport report = download(moduleArtifact, artifactResourceResolver, downloader, new CacheDownloadOptions().setListener(options.getListener()).setForce(true));

            if (report.getDownloadStatus() == DownloadStatus.FAILED) {
                Message.warn("problem while downloading module descriptor: " + mdRef.getResource()
                        + ": " + report.getDownloadDetails()
                        + " (" + report.getDownloadTimeMillis() + "ms)");
                return null;
            }

            ModuleDescriptor md = parseModuleDescriptor(resolver, mdRef, options, report.getLocalFile());
            Message.debug("\t" + getName() + ": parsed downloaded md file for " + moduleArtifact.getModuleRevisionId() + "; parsed=" + md.getModuleRevisionId());

            MetadataArtifactDownloadReport madr
                    = new MetadataArtifactDownloadReport(md.getMetadataArtifact());
            madr.setSearched(true);
            madr.setDownloadStatus(report.getDownloadStatus());
            madr.setDownloadDetails(report.getDownloadDetails());
            madr.setArtifactOrigin(report.getArtifactOrigin());
            madr.setDownloadTimeMillis(report.getDownloadTimeMillis());
            madr.setOriginalLocalFile(report.getLocalFile());
            madr.setSize(report.getSize());

            return new ResolvedModuleRevision(resolver, resolver, md, madr);
        } catch (IOException ex) {
            Message.warn("io problem while parsing ivy file: " + mdRef.getResource() + ": "
                    + ex.getMessage());
            return null;
        }
    }

    private ModuleDescriptor parseModuleDescriptor(DependencyResolver resolver, ResolvedResource mdRef, CacheMetadataOptions options, File artifactFile) throws ParseException, IOException {
        ModuleDescriptorParser parser = ModuleDescriptorParserRegistry.getInstance().getParser(mdRef.getResource());
        ParserSettings parserSettings = settings;
        if (resolver instanceof AbstractResolver) {
            parserSettings = ((AbstractResolver) resolver).getParserSettings();
        }
        return parser.parseDescriptor(parserSettings, artifactFile.toURI().toURL(), mdRef.getResource(), options.isValidate());
    }

}
