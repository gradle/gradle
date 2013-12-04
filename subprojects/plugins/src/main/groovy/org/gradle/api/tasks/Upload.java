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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.internal.Transformers;
import org.gradle.util.ConfigureUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

import static org.gradle.util.CollectionUtils.collect;

/**
 * Uploads the artifacts of a {@link Configuration} to a set of repositories.
 */
public class Upload extends ConventionTask {

    private Configuration configuration;
    private boolean uploadDescriptor;
    private File descriptorDestination;
    private RepositoryHandler repositories;

    private final ArtifactPublicationServices publicationServices;

    @Inject
    public Upload(ArtifactPublicationServices publicationServices) {
        this.publicationServices = publicationServices;
        repositories = publicationServices.createRepositoryHandler();
    }

    @TaskAction
    protected void upload() {
        getLogger().info("Publishing configuration: " + configuration);
        ModuleInternal module = ((ConfigurationInternal) configuration).getModule();

        ArtifactPublisher artifactPublisher = publicationServices.createArtifactPublisher();
        File descriptorDestination = isUploadDescriptor() ? getDescriptorDestination() : null;
        List<PublicationAwareRepository> publishRepositories = collect(repositories, Transformers.cast(PublicationAwareRepository.class));

        try {
            artifactPublisher.publish(publishRepositories, module, configuration, descriptorDestination);
        } catch (Exception e) {
            throw new PublishException(String.format("Could not publish configuration '%s'", configuration.getName()), e);
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

    @SuppressWarnings("UnusedDeclaration")
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

}
