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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.MetadataArtifactDownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.ClientModule;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class ClientModuleResolver extends AbstractLimitedDependencyResolver {
    private Map<String, ModuleDescriptor> moduleRegistry;
    private DependencyResolver userResolver;

    public ClientModuleResolver(String name, Map<String, ModuleDescriptor> moduleRegistry, DependencyResolver userResolver) {
        setName(name);
        this.moduleRegistry = moduleRegistry;
        this.userResolver = userResolver;
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dde, ResolveData data) {
        if (dde.getExtraAttribute(ClientModule.CLIENT_MODULE_KEY) == null) {
            return data.getCurrentResolvedModuleRevision();
        }

        ModuleDescriptor moduleDescriptor = moduleRegistry.get(dde.getExtraAttribute(ClientModule.CLIENT_MODULE_KEY));
        MetadataArtifactDownloadReport downloadReport = new MetadataArtifactDownloadReport(moduleDescriptor.getMetadataArtifact());
        downloadReport.setDownloadStatus(DownloadStatus.NO);
        downloadReport.setSearched(false);
        return new ResolvedModuleRevision(userResolver, userResolver, moduleDescriptor, downloadReport);
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        throw new UnsupportedOperationException();
    }

    public ArtifactOrigin locate(Artifact artifact) {
        throw new UnsupportedOperationException();
    }
}
