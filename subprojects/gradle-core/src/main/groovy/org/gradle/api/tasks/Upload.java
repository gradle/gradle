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
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.util.ConfigureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

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

    public Upload() {
        repositories = getProject().createRepositoryHandler();
    }

    @TaskAction
    protected void upload() {
        logger.info("Publishing configurations: " + configuration);
        configuration.publish(repositories.getResolvers(), isUploadDescriptor() ? getDescriptorDestination() : null);
    }

    public boolean isUploadDescriptor() {
        return uploadDescriptor;
    }

    public void setUploadDescriptor(boolean uploadDescriptor) {
        this.uploadDescriptor = uploadDescriptor;
    }

    public File getDescriptorDestination() {
        return descriptorDestination;
    }

    public void setDescriptorDestination(File descriptorDestination) {
        this.descriptorDestination = descriptorDestination;
    }

    public RepositoryHandler getRepositories() {
        return repositories;
    }

    public void setRepositories(RepositoryHandler repositories) {
        this.repositories = repositories;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

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
        return configuration == null ? null : configuration.getAllArtifactFiles();
    }
}
