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

package org.gradle.api.tasks

import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask
import org.gradle.api.dependencies.ResolverContainer
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Bundle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.dependencies.ResolverContainer

/**
 * An upload task uploads files to the repositories assigned to it.  The files that get uploaded are the artifacts
 * of your project, if they belong to the configuration associated with the upload task.
 * 
 * @author Hans Dockter
 */
class Upload extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Upload)

    /**
     * Whether to publish the xml module descriptor to the upload destination.
     */
    boolean uploadModuleDescriptor = false

    /**
     * Configurations that should be uploaded (beside the configurations of the bundles) 
     */
    List configurations = []

    /**
     * The resolvers to delegate the uploads to. Usuallte a resolver corresponds to a repository.
     */
    ResolverContainer uploadResolvers = new ResolverContainer()

    /**
     * Assigning a bundle to an Upload task has one and only one effect. All the different configurations of the archive
     * tasks belonging to this bundle are delegated to the publish action.
     */
    Set bundles = []

    Upload(DefaultProject project, String name) {
        super(project, name)
        actions << this.&upload
    }

    private void upload(Task task) {
        Set bundleConfigurations = []
        bundles.each { Bundle bundle ->
            bundle.archiveNames.each {
                AbstractArchiveTask archiveTask = project.task(it)
                if (archiveTask.publish) {
                    bundleConfigurations.addAll(archiveTask.configurations as List)
                }
            }
        }
        List allConfigurations = configurations + (bundleConfigurations as List)
        logger.debug("Associated bundles: $bundles")
        logger.info("Publishing configurations: $allConfigurations")
        task.project.dependencies.publish(allConfigurations, uploadResolvers, uploadModuleDescriptor)
    }
}
