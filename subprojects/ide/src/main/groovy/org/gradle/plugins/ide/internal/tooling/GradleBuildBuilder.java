/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.tooling.internal.gradle.BasicGradleProject;
import org.gradle.tooling.internal.gradle.DefaultGradleBuild;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

public class GradleBuildBuilder implements ToolingModelBuilder {
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.GradleBuild");
    }

    public DefaultGradleBuild buildAll(String modelName, Project target) {
        Map<Project, BasicGradleProject> convertedProjects = new LinkedHashMap<Project, BasicGradleProject>();
        BasicGradleProject rootProject = convert(target.getRootProject(), convertedProjects);
        DefaultGradleBuild model = new DefaultGradleBuild().setRootProject(rootProject);
        for (Project project : target.getRootProject().getAllprojects()) {
            model.addProject(convertedProjects.get(project));
        }
        return model;
    }

    private BasicGradleProject convert(Project project, Map<Project, BasicGradleProject> convertedProjects) {
        BasicGradleProject converted = new BasicGradleProject().setName(project.getName()).setPath(project.getPath());
        converted.setProjectDirectory(project.getProjectDir());
        if (project.getParent() != null) {
            converted.setParent(convertedProjects.get(project.getParent()));
        }
        convertedProjects.put(project, converted);
        for (Project child : project.getChildProjects().values()) {
            converted.addChild(convert(child, convertedProjects));
        }
        return converted;
    }
}
