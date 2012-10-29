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
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.repositories.cachemanager.EnhancedArtifactDownloadReport;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;

import java.io.File;

/**
 * A {@link ModuleVersionRepository} wrapper around an Ivy {@link DependencyResolver}.
 */
public class IvyDependencyResolverAdapter extends AbstractDependencyResolverAdapter {
    private final DownloadOptions downloadOptions = new DownloadOptions();

    public IvyDependencyResolverAdapter(DependencyResolver resolver) {
        super(resolver);
    }

    public void download(Artifact artifact, BuildableArtifactResolveResult result) {
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

        ArtifactOrigin artifactOrigin = artifactDownloadReport.getArtifactOrigin();

        File localFile = artifactDownloadReport.getLocalFile();
        if (localFile != null) {
            ExternalResourceMetaData metaData = null;
            if (artifactOrigin instanceof ArtifactOriginWithMetaData) {
                metaData = ((ArtifactOriginWithMetaData) artifactOrigin).getMetaData();
            }
            result.resolved(localFile, metaData);
        } else {
            result.notFound(artifact);
        }
    }
}
