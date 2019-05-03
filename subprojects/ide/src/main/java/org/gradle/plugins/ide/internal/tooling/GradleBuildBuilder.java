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
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.plugins.ide.internal.tooling.model.BasicGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleBuild;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class GradleBuildBuilder implements ToolingModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.GradleBuild");
    }

    @Override
    public DefaultGradleBuild buildAll(String modelName, Project target) {
        Gradle gradle = target.getGradle();
        return convert(gradle, new ArrayList<DefaultGradleBuild>());
    }

    private DefaultGradleBuild convert(Gradle gradle, Collection<DefaultGradleBuild> all) {
        DefaultGradleBuild model = new DefaultGradleBuild();
        Map<Project, BasicGradleProject> convertedProjects = new LinkedHashMap<Project, BasicGradleProject>();

        Project rootProject = gradle.getRootProject();
        BasicGradleProject convertedRootProject = convert(rootProject, convertedProjects);
        model.setRootProject(convertedRootProject);

        for (Project project : rootProject.getAllprojects()) {
            model.addProject(convertedProjects.get(project));
        }

        if (gradle.getParent() != null) {
            all.add(model);
        }

        for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
            Gradle includedGradle = ((IncludedBuildState) includedBuild).getConfiguredBuild();
            DefaultGradleBuild convertedIncludedBuild = convert(includedGradle, all);
            model.addIncludedBuild(convertedIncludedBuild);
        }

        if (gradle.getParent() == null) {
            model.addBuilds(all);
        }

        return model;
    }

    private BasicGradleProject convert(Project project, Map<Project, BasicGradleProject> convertedProjects) {
        DefaultProjectIdentifier id = new DefaultProjectIdentifier(project.getRootDir(), project.getPath());
        BasicGradleProject converted = new BasicGradleProject().setName(project.getName()).setProjectIdentifier(id);
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
