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
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.util.ConfigureUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.Cast.cast;
import static org.gradle.util.CollectionUtils.collect;

/**
 * Uploads the artifacts of a {@link Configuration} to a set of repositories.
 *
 * @author Hans Dockter
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
        Module module = ((ConfigurationInternal) configuration).getModule();
        Set<Configuration> configurationsToPublish = configuration.getHierarchy();

        ArtifactPublisher artifactPublisher = publicationServices.createArtifactPublisher();

        try {
            File descriptorDestination = isUploadDescriptor() ? getDescriptorDestination() : null;
            if (descriptorDestination != null) {
                Set<Configuration> allConfigurations = configurationsToPublish.iterator().next().getAll();
                ModuleDescriptorConverter moduleDescriptorConverter = publicationServices.getDescriptorFileModuleConverter();
                ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(allConfigurations, module);
                IvyModuleDescriptorWriter ivyModuleDescriptorWriter = publicationServices.getIvyModuleDescriptorWriter();
                ivyModuleDescriptorWriter.write(moduleDescriptor, descriptorDestination);
            }

            List<DependencyResolver> resolvers = collect(repositories, new Transformer<DependencyResolver, ArtifactRepository>() {
                public DependencyResolver transform(ArtifactRepository repository) {
                    return cast(ArtifactRepositoryInternal.class, repository).createResolver();
                }
            });
            artifactPublisher.publish(resolvers,  module, configurationsToPublish, descriptorDestination);
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
