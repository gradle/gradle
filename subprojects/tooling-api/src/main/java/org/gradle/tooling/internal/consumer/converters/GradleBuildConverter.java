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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.gradle.BasicGradleProject;
import org.gradle.tooling.internal.gradle.DefaultGradleBuild;
import org.gradle.tooling.model.GradleProject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GradleBuildConverter {
    public DefaultGradleBuild convert(GradleProject project, ConsumerOperationParameters operationParameters) {
        DefaultGradleBuild gradleBuild = new DefaultGradleBuild();
        BasicGradleProject rootProject = toBasicGradleProject(project);
        rootProject.setProjectDirectory(operationParameters.getProjectDir());
        gradleBuild.setRootProject(rootProject);
        gradleBuild.addProject(rootProject);
        convertChildren(gradleBuild, rootProject, project);
        return gradleBuild;
    }

    private void convertChildren(DefaultGradleBuild gradleBuild, BasicGradleProject rootProject, GradleProject project) {
        final List<? extends GradleProject> childProjects = new ArrayList<GradleProject>(project.getChildren());
        Collections.sort(childProjects, new Comparator<GradleProject>() {
            public int compare(GradleProject gp1, GradleProject gp2) {
                return gp1.getName().compareTo(gp2.getName());
            }
        });
        for (GradleProject childProject : childProjects) {
            BasicGradleProject basicGradleProject = toBasicGradleProject(childProject);
            gradleBuild.addProject(basicGradleProject);
            basicGradleProject.setParent(rootProject);
            rootProject.addChild(basicGradleProject);
            convertChildren(gradleBuild, basicGradleProject, childProject);
        }
    }

    private BasicGradleProject toBasicGradleProject(GradleProject childProject) {
        BasicGradleProject basicGradleProject = new BasicGradleProject();
        basicGradleProject.setPath(childProject.getPath());
        basicGradleProject.setName(childProject.getName());
        return basicGradleProject;
    }
}
