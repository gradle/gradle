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
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.file.FileRepository;
import org.apache.ivy.plugins.repository.file.FileResource;
import org.apache.ivy.plugins.resolver.BasicResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyDependencyPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.util.ReflectionUtil;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultInternalRepository extends BasicResolver implements InternalRepository {
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final Gradle gradle;

    public DefaultInternalRepository(Gradle gradle, ModuleDescriptorConverter moduleDescriptorConverter) {
        this.gradle = gradle;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        setName(ResolverContainer.INTERNAL_REPOSITORY_NAME);
    }

    @Override
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
        Project project = gradle.getRootProject().project(projectPathValue);
        Module projectModule = ((ProjectInternal) project).getModule();
        ModuleDescriptor projectDescriptor = moduleDescriptorConverter.convert(project.getConfigurations().getAll(),
                projectModule, IvyContext.getContext().getIvy().getSettings());

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

    @Override
    protected ResolvedResource findArtifactRef(Artifact artifact, Date date) {
        String path = artifact.getExtraAttribute(DefaultIvyDependencyPublisher.FILE_PATH_EXTRA_ATTRIBUTE);
        if (path == null) {            
            return null;
        }
        File file = new File(path);
        return new ResolvedResource(new FileResource(new FileRepository(), file), artifact.getId().getRevision());
    }

    @Override
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
    protected Resource getResource(String source) throws IOException {
        return null;
    }

    @Override
    protected Collection findNames(Map tokenValues, String token) {
        return null;
    }

    @Override
    protected long get(Resource resource, File dest) throws IOException {
        return 0;
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        return null;
    }

    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
    }
}
