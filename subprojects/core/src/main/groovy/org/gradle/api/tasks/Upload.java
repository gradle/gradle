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

package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.internal.Factory;
import org.gradle.util.ConfigureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;

/**
 * Uploads the artifacts of a {@link Configuration} to a set of repositories.
 *
 * @author Hans Dockter
 */
public class Upload extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Upload.class);

    private Configuration configuration;

    private boolean uploadDescriptor;

    private File descriptorDestination;

    /**
     * The resolvers to delegate the uploads to. Usually a resolver corresponds to a repository.
     */
    private RepositoryHandler repositories;

    private ArtifactPublisher artifactPublisher;

    private ModuleDescriptorConverter moduleDescriptorConverter;
    private IvyModuleDescriptorWriter ivyModuleDescriptorWriter;

    public Upload(Factory<ArtifactPublicationServices> artifactPublicationServicesFactory) {
        ArtifactPublicationServices publicationServices = artifactPublicationServicesFactory.create();
        repositories = publicationServices.getRepositoryHandler();
        artifactPublisher = publicationServices.getArtifactPublisher();
        moduleDescriptorConverter = publicationServices.getDescriptorFileModuleConverter();
        ivyModuleDescriptorWriter = publicationServices.getIvyModuleDescriptorWriter();
    }

    @TaskAction
    protected void upload() {
        logger.info("Publishing configuration: " + configuration);
        Module module = ((ConfigurationInternal) configuration).getModule();
        Set<Configuration> configurationsToPublish = configuration.getHierarchy();

        if (isUploadDescriptor()) {
            File descriptorDestination = getDescriptorDestination();
            Set<Configuration> allConfigurations = configurationsToPublish.iterator().next().getAll();
            ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(allConfigurations, module);
            ivyModuleDescriptorWriter.write(moduleDescriptor, descriptorDestination);
            artifactPublisher.publish(module, configurationsToPublish, descriptorDestination);
        } else {
            artifactPublisher.publish(module, configurationsToPublish, null);
        }
    }

    /**
     * Specifies whether the dependency descriptor should be uploaded.
     */
    public boolean isUploadDescriptor() {
        return uploadDescriptor;
    }

    public void setUploadDescriptor(boolean uploadDescriptor) {
        this.uploadDescriptor = uploadDescriptor;
    }

    /**
     * Returns the path to generate the dependency descriptor to.
     */
    public File getDescriptorDestination() {
        return descriptorDestination;
    }

    public void setDescriptorDestination(File descriptorDestination) {
        this.descriptorDestination = descriptorDestination;
    }

    /**
     * Returns the repositories to upload to.
     */
    public RepositoryHandler getRepositories() {
        return repositories;
    }

    /**
     * Returns the configuration to upload.
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Configures the set of repositories to upload to.
     */
    public RepositoryHandler repositories(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, repositories);
    }

    /**
     * Returns the artifacts which will be uploaded.
     *
     * @return the artifacts.
     */
    @InputFiles
    public FileCollection getArtifacts() {
        Configuration configuration = getConfiguration();
        return configuration == null ? null : configuration.getAllArtifacts().getFiles();
    }

    void setRepositories(RepositoryHandler repositories) {
        this.repositories = repositories;
    }

    void setArtifactPublisher(ArtifactPublisher artifactPublisher) {
        this.artifactPublisher = artifactPublisher;
    }

    void setModuleDescriptorConverter(ModuleDescriptorConverter moduleDescriptorConverter) {
        this.moduleDescriptorConverter = moduleDescriptorConverter;
    }

    void setIvyModuleDescriptorWriter(IvyModuleDescriptorWriter ivyModuleDescriptorWriter) {
        this.ivyModuleDescriptorWriter = ivyModuleDescriptorWriter;
    }
}
