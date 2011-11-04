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
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.ivyservice.AbstractLimitedDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyDependencyPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptor;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.ReflectionUtil;

import java.io.File;
import java.text.ParseException;

/**
 * @author Hans Dockter
 */
public class DefaultInternalRepository extends AbstractLimitedDependencyResolver implements InternalRepository {
    private final ModuleDescriptorConverter moduleDescriptorConverter;

    public DefaultInternalRepository(ModuleDescriptorConverter moduleDescriptorConverter) {
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        setName(ArtifactRepositoryContainer.INTERNAL_REPOSITORY_NAME);
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        ModuleDescriptor moduleDescriptor = findProject(dd);
        if (moduleDescriptor == null) {
            return data.getCurrentResolvedModuleRevision();
        }

        MetadataArtifactDownloadReport downloadReport = new MetadataArtifactDownloadReport(moduleDescriptor.getMetadataArtifact());
        downloadReport.setDownloadStatus(DownloadStatus.NO);
        downloadReport.setSearched(false);
        return new ResolvedModuleRevision(this, this, moduleDescriptor, downloadReport);
    }

    private ModuleDescriptor findProject(DependencyDescriptor descriptor) {
        if (!(descriptor instanceof ProjectDependencyDescriptor)) {
            return null;
        }
        ProjectDependencyDescriptor projectDependencyDescriptor = (ProjectDependencyDescriptor) descriptor;
        ProjectInternal project = projectDependencyDescriptor.getTargetProject();
        Module projectModule = project.getModule();
        ModuleDescriptor projectDescriptor = moduleDescriptorConverter.convert(project.getConfigurations(), projectModule);

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

    public ArtifactOrigin locate(Artifact artifact) {
        return null;
    }
}
