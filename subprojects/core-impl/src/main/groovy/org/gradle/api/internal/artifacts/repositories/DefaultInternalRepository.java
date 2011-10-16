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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.MetadataArtifactDownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DefaultResolutionStrategy;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyDependencyPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.NoOpRepositoryCacheManager;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.IvyConfig;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.ReflectionUtil;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

/**
 * @author Hans Dockter
 */
public class DefaultInternalRepository extends AbstractResolver implements InternalRepository {
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final ProjectFinder projectFinder;

    public DefaultInternalRepository(ProjectFinder projectFinder, ModuleDescriptorConverter moduleDescriptorConverter) {
        this.projectFinder = projectFinder;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        setName(ArtifactRepositoryContainer.INTERNAL_REPOSITORY_NAME);
        setRepositoryCacheManager(new NoOpRepositoryCacheManager(getName()));
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        ModuleDescriptor moduleDescriptor = findProject(dd);
        if (moduleDescriptor == null) {
            return data.getCurrentResolvedModuleRevision();
        }

        IvyContext context = IvyContext.pushNewCopyContext();
        try {
            context.setDependencyDescriptor(dd);
            context.setResolveData(data);
            MetadataArtifactDownloadReport downloadReport = new MetadataArtifactDownloadReport(moduleDescriptor.getMetadataArtifact());
            downloadReport.setDownloadStatus(DownloadStatus.NO);
            downloadReport.setSearched(false);
            return new ResolvedModuleRevision(this, this, moduleDescriptor, downloadReport);
        } finally {
            IvyContext.popContext();
        }
    }

    private ModuleDescriptor findProject(DependencyDescriptor descriptor) {
        String projectPathValue = descriptor.getAttribute(DependencyDescriptorFactory.PROJECT_PATH_KEY);
        if (projectPathValue == null) {
            return null;
        }
        ProjectInternal project = projectFinder.getProject(projectPathValue);
        Module projectModule = project.getModule();
        IvySettings ivySettings = IvyContext.getContext().getIvy().getSettings();
        //in this instance we don't care about the resolution strategy because we're not resolving
        DefaultResolutionStrategy whateverStrategy = new DefaultResolutionStrategy();
        IvyConfig ivyConfig = new IvyConfig(ivySettings, whateverStrategy);
        ModuleDescriptor projectDescriptor = moduleDescriptorConverter.convert(project.getConfigurations(), projectModule, ivyConfig);

        for (DependencyArtifactDescriptor artifactDescriptor : descriptor.getAllDependencyArtifacts()) {
            for (Artifact artifact : projectDescriptor.getAllArtifacts()) {
                if (artifact.getName().equals(artifactDescriptor.getName()) && artifact.getExt().equals(
                        artifactDescriptor.getExt())) {
                    String path = artifact.getExtraAttribute(DefaultIvyDependencyPublisher.FILE_PATH_EXTRA_ATTRIBUTE);
                    ReflectionUtil.invoke(artifactDescriptor, "setExtraAttribute",
                            new Object[]{DefaultIvyDependencyPublisher.FILE_PATH_EXTRA_ATTRIBUTE, path});
                }
            }
        }

        return projectDescriptor;
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        DownloadReport dr = new DownloadReport();
        for (Artifact artifact : artifacts) {
            ArtifactDownloadReport artifactDownloadReport = new ArtifactDownloadReport(artifact);
            String path = artifact.getExtraAttribute(DefaultIvyDependencyPublisher.FILE_PATH_EXTRA_ATTRIBUTE);
            if (path == null) {
                artifactDownloadReport.setDownloadStatus(DownloadStatus.FAILED);
            } else {
                File file = new File(path);
                artifactDownloadReport.setDownloadStatus(DownloadStatus.SUCCESSFUL);
                artifactDownloadReport.setArtifactOrigin(new ArtifactOrigin(artifact, true, getName()));
                artifactDownloadReport.setLocalFile(file);
                artifactDownloadReport.setSize(file.length());
            }
            dr.addArtifactReport(artifactDownloadReport);
        }
        return dr;
    }

    @Override
    public void reportFailure() {
    }

    @Override
    public void reportFailure(Artifact art) {
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        throw new UnsupportedOperationException();
    }

    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException();
    }
}
