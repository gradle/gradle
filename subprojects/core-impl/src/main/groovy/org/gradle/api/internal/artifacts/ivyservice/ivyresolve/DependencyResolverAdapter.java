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
import org.gradle.api.internal.artifacts.repositories.cachemanager.LocalFileRepositoryCacheManager;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.text.ParseException;

/**
 * A {@link ModuleVersionRepository} wrapper around an Ivy {@link DependencyResolver}.
 */
public class DependencyResolverAdapter implements ModuleVersionRepository {
    private final String id;
    private final DependencyResolver resolver;
    private final DownloadOptions downloadOptions = new DownloadOptions();

    public DependencyResolverAdapter(String id, DependencyResolver resolver) {
        this.id = id;
        this.resolver = resolver;
    }

    public String getId() {
        return id;
    }

    public boolean isLocal() {
        return resolver.getRepositoryCacheManager() instanceof LocalFileRepositoryCacheManager;
    }

    public File download(Artifact artifact) {
        ArtifactDownloadReport artifactDownloadReport = resolver.download(new Artifact[]{artifact}, downloadOptions).getArtifactReport(artifact);
        if (downloadFailed(artifactDownloadReport)) {
            throw ArtifactResolutionExceptionBuilder.downloadFailure(artifactDownloadReport.getArtifact(), artifactDownloadReport.getDownloadDetails());
        }
        return artifactDownloadReport.getLocalFile();
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
                return null;
            }
            return new DefaultModuleVersionDescriptor(revision.getDescriptor(), getOriginalMetadataArtifact(revision), getOriginalMetadataFile(revision), isChanging(revision));
        } catch (ParseException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private Artifact getOriginalMetadataArtifact(ResolvedModuleRevision revision) {
        ArtifactOrigin artifactOrigin = revision.getReport().getArtifactOrigin();
        return artifactOrigin == null ? null : artifactOrigin.getArtifact();
    }

    private File getOriginalMetadataFile(ResolvedModuleRevision revision) {
        return revision.getReport().getOriginalLocalFile();
    }

    private boolean isChanging(ResolvedModuleRevision resolvedModuleRevision) {
        if (resolver instanceof IvyDependencyResolver) {
            return ((IvyDependencyResolver) resolver).isChangingModule(resolvedModuleRevision.getDescriptor());
        }
        return false;
    }
}
