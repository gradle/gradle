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
import org.gradle.api.internal.dependencies.ResolverContainer
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Bundle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class Upload extends ConventionTask {
    Logger logger = LoggerFactory.getLogger(Upload)
    
    boolean uploadModuleDescriptor = false
    List configurations = []
    ResolverContainer uploadResolvers = new ResolverContainer()
    Set bundles = []

    Upload(DefaultProject project, String name) {
        super(project, name)
        actions << this.&upload
    }

    private void upload(Task task) {
        Set bundleConfigurations = []
        bundles.each { Bundle bundle ->
            bundle.bundleNames.each {
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
