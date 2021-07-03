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
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.internal.Transformers;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.List;

import static org.gradle.util.internal.CollectionUtils.collect;

/**
 * Uploads the artifacts of a {@link Configuration} to a set of repositories.
 *
 * @deprecated This class is scheduled for removal in Gradle 8.0. To upload artifacts, use the maven-publish plugin instead.
 */
@Deprecated
@DisableCachingByDefault(because = "Produces no cacheable output")
public class Upload extends ConventionTask {

    private Configuration configuration;
    private boolean uploadDescriptor;
    private File descriptorDestination;
    private RepositoryHandler repositories;

    @Inject
    protected ArtifactPublicationServices getPublicationServices() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    protected void upload() {
        getLogger().info("Publishing configuration: {}", configuration);
        DeprecationLogger.deprecateTaskType(Upload.class, getPath()).willBeRemovedInGradle8().withUpgradeGuideSection(7, "upload_task_deprecation").nagUser();

        Module module = ((ConfigurationInternal) configuration).getModule();

        ArtifactPublisher artifactPublisher = getPublicationServices().createArtifactPublisher();
        File descriptorDestination = isUploadDescriptor() ? getDescriptorDestination() : null;
        List<PublicationAwareRepository> publishRepositories = collect(getRepositories(), Transformers.cast(PublicationAwareRepository.class));

        try {
            artifactPublisher.publish(publishRepositories, module, configuration, descriptorDestination);
        } catch (Exception e) {
            throw new PublishException(String.format("Could not publish configuration '%s'", configuration.getName()), e);
        }
    }

    /**
     * Specifies whether the dependency descriptor should be uploaded.
     */
    @Input
    public boolean isUploadDescriptor() {
        return uploadDescriptor;
    }

    public void setUploadDescriptor(boolean uploadDescriptor) {
        this.uploadDescriptor = uploadDescriptor;
    }

    /**
     * Returns the path to generate the dependency descriptor to.
     */
    @Internal
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
    @Internal
    public RepositoryHandler getRepositories() {
        if (repositories == null) {
            repositories = getPublicationServices().createRepositoryHandler();
        }
        return repositories;
    }

    /**
     * Returns the configuration to upload.
     */
    @Internal
    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Configures the set of repositories to upload to.
     */
    public RepositoryHandler repositories(@Nullable Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getRepositories());
    }

    /**
     * Configures the set of repositories to upload to.
     * @since 3.5
     */
    public RepositoryHandler repositories(Action<? super RepositoryHandler> configureAction) {
        RepositoryHandler repositories = getRepositories();
        configureAction.execute(repositories);
        return repositories;
    }

    /**
     * Returns the artifacts which will be uploaded.
     *
     * @return the artifacts.
     */
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFiles
    public FileCollection getArtifacts() {
        Configuration configuration = getConfiguration();
        return configuration.getAllArtifacts().getFiles();
    }

}
