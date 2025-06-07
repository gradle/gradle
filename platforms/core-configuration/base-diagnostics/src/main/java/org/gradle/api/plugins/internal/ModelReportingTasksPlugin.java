/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.plugins.internal;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.diagnostics.internal.DiagnosticsTaskNames;
import org.jspecify.annotations.NullMarked;

/**
 * Plugin that adds tasks to report on the deprecated software models configured for the project.
 *
 * @since 9.0.0
 */

@NullMarked
abstract public class ModelReportingTasksPlugin implements Plugin<Project> {

    @Override
    @SuppressWarnings("deprecation")
    public void apply(Project project) {
        project.getTasks().register(DiagnosticsTaskNames.MODEL_TASK, org.gradle.api.reporting.model.ModelReport.class, new ModelReportAction(project.toString()));
    }

    @SuppressWarnings("deprecation")
    @NullMarked
    private static class ModelReportAction implements Action<org.gradle.api.reporting.model.ModelReport> {
        private final String projectName;

        public ModelReportAction(String projectName) {
            this.projectName = projectName;
        }

        @Override
        public void execute(org.gradle.api.reporting.model.ModelReport task) {
            task.setDescription("Displays the configuration model of " + projectName + ". [deprecated]");
            task.setImpliesSubProjects(true);
        }
    }
}
