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

package org.gradle.configuration;

import org.gradle.api.internal.project.ProjectsTraverser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gradle.api.ProjectAction;
import org.gradle.api.Project;
import org.gradle.util.WrapUtil;

/**
 * @author Hans Dockter
 */
public class ProjectDependencies2TaskResolver {
    private static Logger logger = LoggerFactory.getLogger(ProjectDependencies2TaskResolver.class);

    // This is an implementation detail of ProjectDependencies2TasksResolver. Therefore we don't use IoC here.
    ProjectsTraverser projectsTraverser = new ProjectsTraverser();

    ProjectDependencies2TaskResolver(ProjectsTraverser projectsTraverser) {
    }

    public void resolve(Project rootProject) {
        ProjectAction projectAction = new ProjectAction() {
            public void execute(Project project) {
                for (Project dependsOnProject : project.getDependsOnProjects()) {
                    logger.debug("Checking task dependencies for project: $project dependsOn: $dependsOnProject");
                    for (String taskName : project.getTasks().keySet()) {
                        if (dependsOnProject.getTasks().get(taskName) != null) {
                            logger.debug("Setting task dependencies for task: $taskName");
                            project.getTasks().get(taskName).dependsOn(new String[]{dependsOnProject.getTasks().get(taskName).getPath()});
                        }
                    }
                }
            }
        };
        projectsTraverser.traverse(WrapUtil.toSet(rootProject), projectAction);
    }
}