/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.StartParameter;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultRunClosedProjectBuildDependencies;
import org.gradle.tooling.model.eclipse.EclipseRuntime;
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject;
import org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.api.internal.project.ProjectHierarchyUtils.getChildProjectsForInternalUse;

@NonNullApi
public class RunBuildDependenciesTaskBuilder implements ParameterizedToolingModelBuilder<EclipseRuntime> {
    private Map<String, Boolean> projectOpenStatus;

    @Override
    public Class<EclipseRuntime> getParameterType() {
        return EclipseRuntime.class;
    }

    @Override
    public RunClosedProjectBuildDependencies buildAll(String modelName, EclipseRuntime eclipseRuntime, Project project) {
        this.projectOpenStatus = eclipseRuntime.getWorkspace().getProjects().stream()
            .collect(Collectors.toMap(EclipseWorkspaceProject::getName, EclipseModelBuilder::isProjectOpen, (a, b) -> a | b));

        List<TaskDependency> buildDependencies = populate(project.getRootProject());
        if (!buildDependencies.isEmpty()) {
            Gradle rootGradle = getRootGradle(project.getGradle());
            Project rootProject = rootGradle.getRootProject();
            StartParameter startParameter = rootGradle.getStartParameter();
            List<String> taskPaths = new ArrayList<>(startParameter.getTaskNames());
            String parentTaskName = parentTaskName(rootProject, "eclipseClosedDependencies");
            Task task = rootProject.task(parentTaskName);
            task.dependsOn(buildDependencies.toArray(new Object[0]));
            taskPaths.add(parentTaskName);
            startParameter.setTaskNames(taskPaths);
        }
        return DefaultRunClosedProjectBuildDependencies.INSTANCE;
    }

    private Gradle getRootGradle(Gradle gradle) {
        Gradle parent = gradle.getParent();
        if (parent == null) {
            return gradle;
        }
        return getRootGradle(parent);
    }

    private List<TaskDependency> populate(Project project) {
        project.getPluginManager().apply(EclipsePlugin.class);
        EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);
        EclipseClasspath eclipseClasspath = eclipseModel.getClasspath();

        EclipseModelBuilder.ClasspathElements elements = EclipseModelBuilder.gatherClasspathElements(projectOpenStatus, eclipseClasspath, false);
        List<TaskDependency> buildDependencies = new ArrayList<>(elements.getBuildDependencies());

        for (Project childProject : getChildProjectsForInternalUse(project)) {
            buildDependencies.addAll(populate(childProject));
        }
        return buildDependencies;
    }

    @Override
    public boolean canBuild(String modelName) {
        return "org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies".equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        // nothing to do if no EclipseRuntime is supplied.
        return DefaultRunClosedProjectBuildDependencies.INSTANCE;
    }

    private static String parentTaskName(Project project, String baseName) {
        if (project.getTasks().findByName(baseName) == null) {
            return baseName;
        } else {
            return parentTaskName(project, baseName + "_");
        }
    }

}
