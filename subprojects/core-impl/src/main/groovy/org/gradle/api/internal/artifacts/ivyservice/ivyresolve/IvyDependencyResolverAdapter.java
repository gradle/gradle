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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.repositories.cachemanager.EnhancedArtifactDownloadReport;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.ParseException;

/**
 * A {@link ModuleVersionRepository} wrapper around an Ivy {@link DependencyResolver}.
 */
public class IvyDependencyResolverAdapter extends AbstractDependencyResolverAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(IvyDependencyResolverAdapter.class);
    private final DownloadOptions downloadOptions = new DownloadOptions();

    public IvyDependencyResolverAdapter(DependencyResolver resolver) {
        super(resolver);
    }

    public void getDependency(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionMetaData result) {
        ResolveData resolveData = IvyContextualiser.getIvyContext().getResolveData();
        try {
            ResolvedModuleRevision revision = resolver.getDependency(dependencyDescriptor, resolveData);
            if (revision == null) {
                LOGGER.debug("Performed resolved of module '{}' in repository '{}': not found", dependencyDescriptor.getDependencyRevisionId(), getName());
                result.missing();
            } else {
                LOGGER.debug("Performed resolved of module '{}' in repository '{}': found", dependencyDescriptor.getDependencyRevisionId(), getName());
                result.resolved(revision.getDescriptor(), isChanging(revision), null);
            }
        } catch (ParseException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void resolve(Artifact artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
        ArtifactDownloadReport artifactDownloadReport = resolver.download(new Artifact[]{artifact}, downloadOptions).getArtifactReport(artifact);
        if (downloadFailed(artifactDownloadReport)) {
            if (artifactDownloadReport instanceof EnhancedArtifactDownloadReport) {
                EnhancedArtifactDownloadReport enhancedReport = (EnhancedArtifactDownloadReport) artifactDownloadReport;
                result.failed(new ArtifactResolveException(artifactDownloadReport.getArtifact(), enhancedReport.getFailure()));
            } else {
                result.failed(new ArtifactResolveException(artifactDownloadReport.getArtifact(), artifactDownloadReport.getDownloadDetails()));
            }
            return;
        }

        File localFile = artifactDownloadReport.getLocalFile();
        if (localFile != null) {
            result.resolved(localFile);
        } else {
            result.notFound(artifact);
        }
    }

    private boolean downloadFailed(ArtifactDownloadReport artifactReport) {
        // Ivy reports FAILED with MISSING_ARTIFACT message when the artifact doesn't exist.
        return artifactReport.getDownloadStatus() == DownloadStatus.FAILED
                && !artifactReport.getDownloadDetails().equals(ArtifactDownloadReport.MISSING_ARTIFACT);
    }

    private boolean isChanging(ResolvedModuleRevision resolvedModuleRevision) {
        return new ChangingModuleDetector(resolver).isChangingModule(resolvedModuleRevision.getDescriptor());
    }
}
