/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.clientmodule;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.MetadataArtifactDownloadReport;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.internal.artifacts.ivyservice.GradleDependencyResolver;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ClientModuleResolver implements GradleDependencyResolver {
    private ClientModuleRegistry moduleRegistry;

    public ClientModuleResolver(ClientModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dde, ResolveData data) {
        if (dde.getExtraAttribute(ClientModule.CLIENT_MODULE_KEY) == null) {
            return data.getCurrentResolvedModuleRevision();
        }

        ModuleDescriptor moduleDescriptor = moduleRegistry.getClientModule(dde.getExtraAttribute(ClientModule.CLIENT_MODULE_KEY));
        MetadataArtifactDownloadReport downloadReport = new MetadataArtifactDownloadReport(moduleDescriptor.getMetadataArtifact());
        downloadReport.setDownloadStatus(DownloadStatus.NO);
        downloadReport.setSearched(false);
        return new ResolvedModuleRevision(null, null, moduleDescriptor, downloadReport);
    }

    public File resolve(Artifact artifact) {
        throw new UnsupportedOperationException();
    }

    public ArtifactOrigin locate(Artifact artifact) {
        throw new UnsupportedOperationException();
    }
}
