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

import java.io.File;

/**
 * An {@link ArtifactToFileResolver} implementation that uses an Ivy {@link DependencyResolver} to download the artifact.
 */
public class IvyResolverBackedArtifactToFileResolver implements ArtifactToFileResolver {
    private final DependencyResolver resolver;

    public IvyResolverBackedArtifactToFileResolver(DependencyResolver resolver) {
        this.resolver = resolver;
    }

    public File resolve(Artifact artifact) {
        DownloadReport downloadReport = resolver.download(new Artifact[]{artifact}, new DownloadOptions());
        return downloadReport.getArtifactReport(artifact).getLocalFile();
    }
}
