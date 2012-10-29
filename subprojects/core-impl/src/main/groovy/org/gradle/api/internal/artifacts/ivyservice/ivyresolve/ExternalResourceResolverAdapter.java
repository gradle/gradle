/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BrokenArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.FileBackedArtifactResolveResult;
import org.gradle.api.internal.artifacts.repositories.cachemanager.EnhancedArtifactDownloadReport;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;

import java.io.File;

/**
 * A {@link ModuleVersionRepository} wrapper around an {@link ExternalResourceResolver}.
 */
public class ExternalResourceResolverAdapter extends AbstractDependencyResolverAdapter {
    private final ExternalResourceResolver resolver;

    public ExternalResourceResolverAdapter(ExternalResourceResolver resolver) {
        super(resolver);
        this.resolver = resolver;
    }

    public ArtifactResolveResult download(Artifact artifact) {
        EnhancedArtifactDownloadReport artifactDownloadReport = resolver.download(artifact);
        if (downloadFailed(artifactDownloadReport)) {
            return new BrokenArtifactResolveResult(new ArtifactResolveException(artifactDownloadReport.getArtifact(), artifactDownloadReport.getFailure()));
        }

        ArtifactOriginWithMetaData artifactOrigin = artifactDownloadReport.getArtifactOrigin();

        File localFile = artifactDownloadReport.getLocalFile();
        if (localFile != null) {
            ExternalResourceMetaData metaData = artifactOrigin.getMetaData();
            return new FileBackedArtifactResolveResult(localFile, metaData);
        } else {
            return new BrokenArtifactResolveResult(new ArtifactNotFoundException(artifact));
        }
    }
}
