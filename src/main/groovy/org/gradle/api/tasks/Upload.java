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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.dependencies.ResolverContainer;
import org.gradle.api.dependencies.ConfigurationPublishInstruction;
import org.gradle.api.dependencies.ConfigurationResolver;
import org.gradle.api.internal.DefaultTask;
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

    private ConfigurationPublishInstruction publishInstruction;

    /**
     * The resolvers to delegate the uploads to. Usually a resolver corresponds to a repository.
     */
    private ResolverContainer uploadResolvers;

    public Upload(Project project, String name) {
        super(project, name);
        uploadResolvers = project.getDependencies().createResolverContainer();
        doFirst(new TaskAction() {
            public void execute(Task task) {
                upload(task);
            }
        });
    }

    private void upload(Task task) {
        logger.info("Publishing configurations: " + publishInstruction.getConfiguration());
        ConfigurationResolver configuration = getProject().getDependencies().configuration(publishInstruction.getConfiguration());
        configuration.publish(uploadResolvers, publishInstruction);
    }

    public ConfigurationPublishInstruction getPublishInstruction() {
        return publishInstruction;
    }

    public void setPublishInstruction(ConfigurationPublishInstruction publishInstruction) {
        this.publishInstruction = publishInstruction;
    }

    public ResolverContainer getUploadResolvers() {
        return uploadResolvers;
    }

    public void setUploadResolvers(ResolverContainer uploadResolvers) {
        this.uploadResolvers = uploadResolvers;
    }
}
