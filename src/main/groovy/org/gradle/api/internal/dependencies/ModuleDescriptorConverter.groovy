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

package org.gradle.api.internal.dependencies

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.dependencies.Dependency
import org.gradle.api.dependencies.GradleArtifact
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.DependencyManager

/**
 * @author Hans Dockter
 */
class ModuleDescriptorConverter {
    private static Logger logger = LoggerFactory.getLogger(ModuleDescriptorConverter)

    ModuleDescriptorConverter() {
    }

    ModuleDescriptor convert(DefaultDependencyManager dependencyManager) {
        def status = DependencyManager.DEFAULT_STATUS
        if (dependencyManager.project.hasProperty('status')) {
            status = dependencyManager.project.status
        }
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(dependencyManager.createModuleRevisionId(),
                status, null)
        dependencyManager.configurations.values().each {moduleDescriptor.addConfiguration(it)}
        addDependencyDescriptors(moduleDescriptor, dependencyManager)
        addArtifacts(moduleDescriptor, dependencyManager)
        moduleDescriptor
    }

    void addDependencyDescriptors(DefaultModuleDescriptor moduleDescriptor, DefaultDependencyManager dependencyManager) {
        List dependencyDescriptors = dependencyManager.dependencies.collect() {Dependency dependency ->
            dependency.createDepencencyDescriptor()
        }
        (dependencyDescriptors + dependencyManager.dependencyDescriptors).each {moduleDescriptor.addDependency(it)}
    }

    void addArtifacts(DefaultModuleDescriptor moduleDescriptor, DefaultDependencyManager dependencyManager) {
        dependencyManager.artifacts.each {String configuration, List artifacts ->
            artifacts.each {GradleArtifact gradleArtifact ->
                logger.debug("Add gradleArtifact: {} to configuration={}", gradleArtifact, configuration)
                moduleDescriptor.addArtifact(configuration, gradleArtifact.createIvyArtifact(dependencyManager.createModuleRevisionId()))
            }
        }
        dependencyManager.artifactDescriptors.each {String configuration, List artifactDescriptors ->
            artifactDescriptors.each {Artifact artifact ->
                moduleDescriptor.addArtifact(configuration, artifact)
            }
        }
    }
}
