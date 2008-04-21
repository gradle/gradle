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

package org.gradle.api.dependencies

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.gradle.api.internal.project.DefaultProject

/**
* @author Hans Dockter
*/
class ProjectDependency extends AbstractDependency {
    String dependencyConfiguration = Dependency.DEFAULT_CONFIGURATION

    ProjectDependency(DefaultProject dependencyProject, String dependencyConfiguration) {
        super(null, dependencyProject, null)
        this.dependencyConfiguration = dependencyConfiguration
    }

    ProjectDependency(Set confs, Object userDependencyDescription, DefaultProject project) {
        super(confs, userDependencyDescription, project)
    }

    boolean isValidDescription(Object userDependencyDescription) {
        true
    }

    Class[] userDepencencyDescriptionType() {
        [DefaultProject]
    }

    DependencyDescriptor createDepencencyDescriptor() {
        DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(dependencyProject.dependencies.createModuleRevisionId(), false, true)
        confs.each {dd.addDependencyConfiguration(it, dependencyConfiguration)}
        dd
    }

    ProjectDependency dependencyConfiguration(String dependencyConfiguration) {
        this.dependencyConfiguration = dependencyConfiguration
    }

    DefaultProject getDependencyProject() {
        userDependencyDescription
    }

    void initialize() {
        confs.each {String conf ->
            (project.dependencies.conf2Tasks[conf]).each {taskName ->
                project.task(taskName).dependsOn dependencyProject.task(dependencyProject.dependencies.artifactProductionTaskName).path
            }
        }
    }

}