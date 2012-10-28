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

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.repositories.cachemanager.EnhancedArtifactDownloadReport;
import org.gradle.api.internal.artifacts.repositories.cachemanager.LocalFileRepositoryCacheManager;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.ParseException;

/**
 * A {@link ModuleVersionRepository} wrapper around an Ivy {@link DependencyResolver}.
 */
public class DependencyResolverAdapter implements ModuleVersionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyResolverAdapter.class);

    private final DependencyResolverIdentifier identifier;
    private final DependencyResolver resolver;
    private final DownloadOptions downloadOptions = new DownloadOptions();

    public DependencyResolverAdapter(DependencyResolver resolver) {
        this.identifier = new DependencyResolverIdentifier(resolver);
        this.resolver = resolver;
    }

    public String getId() {
        return identifier.getUniqueId();
    }

    public String getName() {
        return identifier.getName();
    }

    @Override
    public String toString() {
        return String.format("Repository '%s'", resolver.getName());
    }

    public boolean isLocal() {
        return resolver.getRepositoryCacheManager() instanceof LocalFileRepositoryCacheManager;
    }

    public DownloadedArtifact download(Artifact artifact) {
        ArtifactDownloadReport artifactDownloadReport = resolver.download(new Artifact[]{artifact}, downloadOptions).getArtifactReport(artifact);
        if (downloadFailed(artifactDownloadReport)) {
            if (artifactDownloadReport instanceof EnhancedArtifactDownloadReport) {
                EnhancedArtifactDownloadReport enhancedReport = (EnhancedArtifactDownloadReport) artifactDownloadReport;
                throw new ArtifactResolveException(artifactDownloadReport.getArtifact(), enhancedReport.getFailure());
            }
            throw new ArtifactResolveException(artifactDownloadReport.getArtifact(), artifactDownloadReport.getDownloadDetails());
        }

        ArtifactOrigin artifactOrigin = artifactDownloadReport.getArtifactOrigin();

        File localFile = artifactDownloadReport.getLocalFile();
        if (localFile != null) {
            ExternalResourceMetaData metaData = null;
            if (artifactOrigin instanceof ArtifactOriginWithMetaData) {
                metaData = ((ArtifactOriginWithMetaData) artifactOrigin).getMetaData();
            }
            return new DownloadedArtifact(localFile, metaData);
        } else {
            return null;
        }
    }

    private boolean downloadFailed(ArtifactDownloadReport artifactReport) {
        // Ivy reports FAILED with MISSING_ARTIFACT message when the artifact doesn't exist.
        return artifactReport.getDownloadStatus() == DownloadStatus.FAILED
                && !artifactReport.getDownloadDetails().equals(ArtifactDownloadReport.MISSING_ARTIFACT);
    }

    public ModuleVersionDescriptor getDependency(final DependencyDescriptor dd) {
        ResolveData resolveData = IvyContextualiser.getIvyContext().getResolveData();
        try {
            ResolvedModuleRevision revision = resolver.getDependency(dd, resolveData);
            if (revision == null) {
                LOGGER.debug("Performed resolved of module '{}' in repository '{}': not found", dd.getDependencyRevisionId(), getName());
                return null;
            }
            LOGGER.debug("Performed resolved of module '{}' in repository '{}': found", dd.getDependencyRevisionId(), getName());
            return new DefaultModuleVersionDescriptor(revision.getDescriptor(), isChanging(revision));
        } catch (ParseException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private boolean isChanging(ResolvedModuleRevision resolvedModuleRevision) {
        return new ChangingModuleDetector(resolver).isChangingModule(resolvedModuleRevision.getDescriptor());
    }
}
