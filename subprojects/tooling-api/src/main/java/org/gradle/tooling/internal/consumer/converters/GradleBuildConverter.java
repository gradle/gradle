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

import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.gradle.DefaultGradleBuild;
import org.gradle.tooling.internal.gradle.PartialBasicGradleProject;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.ProjectIdentifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GradleBuildConverter {
    public DefaultGradleBuild convert(GradleProject project) {
        DefaultGradleBuild gradleBuild = new DefaultGradleBuild();
        PartialBasicGradleProject rootProject = toPartialGradleProject(project);
        gradleBuild.setRootProject(rootProject);
        gradleBuild.addProject(rootProject);
        convertChildren(gradleBuild, rootProject, project);
        return gradleBuild;
    }

    private void convertChildren(DefaultGradleBuild gradleBuild, PartialBasicGradleProject rootProject, GradleProject project) {
        final List<? extends GradleProject> childProjects = new ArrayList<GradleProject>(project.getChildren());
        Collections.sort(childProjects, new Comparator<GradleProject>() {
            public int compare(GradleProject gp1, GradleProject gp2) {
                return gp1.getName().compareTo(gp2.getName());
            }
        });
        for (GradleProject childProject : childProjects) {
            PartialBasicGradleProject basicGradleProject = toPartialGradleProject(childProject);
            gradleBuild.addProject(basicGradleProject);
            basicGradleProject.setParent(rootProject);
            rootProject.addChild(basicGradleProject);
            convertChildren(gradleBuild, basicGradleProject, childProject);
        }
    }

    private PartialBasicGradleProject toPartialGradleProject(GradleProject childProject) {
        PartialBasicGradleProject basicGradleProject = new PartialBasicGradleProject();
        basicGradleProject.setName(childProject.getName());
        ProjectIdentifier id = childProject.getProjectIdentifier();
        basicGradleProject.setProjectIdentifier(new DefaultProjectIdentifier(id.getBuildIdentifier().getRootDir(), id.getProjectPath()));
        return basicGradleProject;
    }
}
