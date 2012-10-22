/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.UnknownPluginException;

public class ImplicitTasksConfigurer implements Action<ProjectInternal> {
    public static final String HELP_GROUP = "help";
    public static final String HELP_TASK = "help";
    public static final String PROJECTS_TASK = "projects";
    public static final String TASKS_TASK = "tasks";
    public static final String PROPERTIES_TASK = "properties";
    public static final String DEPENDENCIES_TASK = "dependencies";
    public static final String DEPENDENCY_INSIGHT_TASK = "dependencyInsight";

    public void execute(ProjectInternal project) {
        try {
            project.getPlugins().apply("dependency-reporting");
        } catch (UnknownPluginException e) {
            //some of our in-process integrations tests live in a subproject
            //which does not depend on the subproject 'dependency-reporting' lives.
            //I couldn't figure out a better workaround.
            //This should be still pretty safe as we have forking coverage to catch problems.
            //This workaround should go away once we have the auto-apply plugins/tasks implementation.
        }
    }
}
