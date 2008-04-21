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

package org.gradle.configuration

import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectsTraverser
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
class ProjectDependencies2TasksResolver {
    private static Logger logger = LoggerFactory.getLogger(ProjectDependencies2TasksResolver)

    // This is an implementation detail of ProjectDependencies2TasksResolver. Therefore we don't use IoC here.
    ProjectsTraverser projectsTraverser = new ProjectsTraverser()

    ProjectDependencies2TasksResolver(ProjectsTraverser projectsTraverser) {
    }
    
    void resolve(DefaultProject rootProject) {
        projectsTraverser.traverse([rootProject]) {DefaultProject project ->
            project.dependsOnProjects.each {DefaultProject dependsOnProject ->
                logger.debug("Checking task dependencies for project: $project dependsOn: $dependsOnProject")
                project.tasks.each {String taskName, DefaultTask task ->
                    if (dependsOnProject.tasks[taskName]) {
                        logger.debug("Setting task dependencies for task: $taskName")
                        task.dependsOn(dependsOnProject.tasks[taskName].path)
                    }
                }
            }
        }    
    }
    
}