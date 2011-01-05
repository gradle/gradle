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

import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.MetadataArtifactDownloadReport;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.lock.NoLockStrategy;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.BasicResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.util.DeleteOnExit;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class ClientModuleResolver extends BasicResolver {
    private Map moduleRegistry;
    private DependencyResolver userResolver;

    public ClientModuleResolver(String name, Map moduleRegistry, DependencyResolver userResolver) {
        setName(name);
        this.moduleRegistry = moduleRegistry;
        this.userResolver = userResolver;
        setRepositoryCacheManager(createUseOriginCacheManager(name));
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dde, ResolveData data) {
        if (dde.getExtraAttribute(ClientModule.CLIENT_MODULE_KEY) == null) {
            return null;
        }

        IvyContext context = IvyContext.pushNewCopyContext();
        try {
            context.setDependencyDescriptor(dde);
            context.setResolveData(data);
            DefaultModuleDescriptor moduleDescriptor =
                    (DefaultModuleDescriptor) moduleRegistry.get(dde.getExtraAttribute(ClientModule.CLIENT_MODULE_KEY));
            MetadataArtifactDownloadReport downloadReport = new MetadataArtifactDownloadReport(moduleDescriptor.getMetadataArtifact());
            downloadReport.setDownloadStatus(DownloadStatus.NO);
            downloadReport.setSearched(false);
            return new ResolvedModuleRevision(userResolver, userResolver, moduleDescriptor, downloadReport);
        } finally {
            IvyContext.popContext();
        }
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        return null;
    }

    protected Collection findNames(Map tokenValues, String token) {
        return null;
    }

    protected ResolvedResource findArtifactRef(Artifact artifact, Date date) {
        return null;
    }

    protected long get(Resource resource, File dest) {
        return resource.getContentLength();
    }

    protected Resource getResource(String s) {
        return null;
    }

    public void publish(Artifact artifact, File src, boolean overwrite) {
    }

    private RepositoryCacheManager createUseOriginCacheManager(String name) {
        File tmpIvyCache = createTmpDir();
        DefaultRepositoryCacheManager cacheManager = new DefaultRepositoryCacheManager();
        cacheManager.setBasedir(tmpIvyCache);
        cacheManager.setName(name);
        cacheManager.setUseOrigin(true);
        cacheManager.setLockStrategy(new NoLockStrategy());
        cacheManager.setIvyPattern(ResolverContainer.DEFAULT_CACHE_IVY_PATTERN);
        cacheManager.setArtifactPattern(ResolverContainer.DEFAULT_CACHE_ARTIFACT_PATTERN);
        return cacheManager;
    }

    private File createTmpDir() {
        File tmpFile;
        try {
            tmpFile = File.createTempFile("gradle_ivy_cache_" + getName(), "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        tmpFile.delete();
        tmpFile.mkdir();
        DeleteOnExit.addFile(tmpFile);
        return tmpFile;
    }
}
