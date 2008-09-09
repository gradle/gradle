/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies

import org.apache.ivy.core.IvyContext
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.report.DownloadStatus
import org.apache.ivy.core.report.MetadataArtifactDownloadReport
import org.apache.ivy.core.resolve.ResolveData
import org.apache.ivy.core.resolve.ResolvedModuleRevision
import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.resolver.BasicResolver
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.util.ResolvedResource
import org.gradle.api.dependencies.ClientModule

/**
 * @author Hans Dockter
 */
class ClientModuleResolver extends BasicResolver {
    Map moduleRegistry
    DependencyResolver mainResolver

    public ResolvedModuleRevision getDependency(DependencyDescriptor dde, ResolveData data) {
        if (dde.extraAttributes[ClientModule.CLIENT_MODULE_KEY]) {
            IvyContext context = IvyContext.pushNewCopyContext();
            context.setDependencyDescriptor(dde);
            context.setResolveData(data);
            DefaultModuleDescriptor moduleDescriptor = moduleRegistry[dde.extraAttributes[ClientModule.CLIENT_MODULE_KEY]]
            MetadataArtifactDownloadReport downloadReport = new MetadataArtifactDownloadReport(moduleDescriptor.metadataArtifact)
            downloadReport.setDownloadStatus(DownloadStatus.NO);
            downloadReport.setSearched(false);
            IvyContext.popContext();
            return new ResolvedModuleRevision(mainResolver, mainResolver, moduleDescriptor, downloadReport)
        }
        null
    }

    ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        null
    }


    protected Collection findNames(Map tokenValues, String token) {
        null
    }

    protected ResolvedResource findArtifactRef(Artifact artifact, Date date) {
        null
    }

    protected long get(Resource resource, File dest) {
        null
    }

    protected Resource getResource(String s) {
        return null;  
    }

    public void publish(Artifact artifact, File src, boolean overwrite) {
        null
    }


}
