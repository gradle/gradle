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
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishInstruction;
import org.gradle.api.internal.DefaultTask;
import org.gradle.api.internal.artifacts.dsl.RepositoryHandler;
import org.gradle.util.ConfigureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An upload task uploads files to the repositories assigned to it.  The files that get uploaded are the artifacts
 * of your project, if they belong to the configuration associated with the upload task.
 * 
 * @author Hans Dockter
 */
public class Upload extends DefaultTask {
    private static Logger logger = LoggerFactory.getLogger(Upload.class);

    private PublishInstruction publishInstruction;

    private Configuration configuration;

    /**
     * The resolvers to delegate the uploads to. Usually a resolver corresponds to a repository.
     */
    private RepositoryHandler repositories;

    public Upload(Project project, String name) {
        super(project, name);
        repositories = project.createRepositoryHandler();
        doFirst(new TaskAction() {
            public void execute(Task task) {
                upload(task);
            }
        });
    }

    private void upload(Task task) {
        logger.info("Publishing configurations: " + configuration);
        configuration.publish(repositories.getResolverList(), publishInstruction);
    }

    public PublishInstruction getPublishInstruction() {
        return publishInstruction;
    }

    public void setPublishInstruction(PublishInstruction publishInstruction) {
        this.publishInstruction = publishInstruction;
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
        return (RepositoryHandler) ConfigureUtil.configure(configureClosure, repositories);
    }
}
