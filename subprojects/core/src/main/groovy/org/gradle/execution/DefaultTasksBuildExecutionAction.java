/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.execution;

import org.gradle.StartParameter;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * A {@link BuildConfigurationAction} that selects the default tasks for a project, or if none are defined, the 'help' task.
 */
public class DefaultTasksBuildExecutionAction implements BuildConfigurationAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTasksBuildExecutionAction.class);
    private final ProjectConfigurer projectConfigurer;

    public DefaultTasksBuildExecutionAction(ProjectConfigurer projectConfigurer) {
        this.projectConfigurer = projectConfigurer;
    }

    public void configure(BuildExecutionContext context) {
        StartParameter startParameter = context.getGradle().getStartParameter();

        for (TaskExecutionRequest request : startParameter.getTaskRequests()) {
            if (!request.getArgs().isEmpty()) {
                context.proceed();
                return;
            }
        }

        // Gather the default tasks from this first group project
        ProjectInternal project = context.getGradle().getDefaultProject();

        //so that we don't miss out default tasks
        projectConfigurer.configure(project);

        List<String> defaultTasks = project.getDefaultTasks();
        if (defaultTasks.size() == 0) {
            defaultTasks = Arrays.asList(ProjectInternal.HELP_TASK);
            LOGGER.info("No tasks specified. Using default task {}", GUtil.toString(defaultTasks));
        } else {
            LOGGER.info("No tasks specified. Using project default tasks {}", GUtil.toString(defaultTasks));
        }

        startParameter.setTaskNames(defaultTasks);
        context.proceed();
    }
}
