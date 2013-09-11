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

import org.gradle.tooling.internal.gradle.BasicGradleProject;
import org.gradle.tooling.internal.gradle.DefaultGradleBuild;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GradleBuildConverter {

    public DefaultGradleBuild convert(EclipseProject project) {
        DefaultGradleBuild gradleBuild = new DefaultGradleBuild();
        BasicGradleProject rootProject = toBasicGradleProject(project);
        gradleBuild.setRootProject(rootProject);
        gradleBuild.addProject(rootProject);
        convertChildren(gradleBuild, rootProject, project);
        return gradleBuild;
    }

    public DefaultGradleBuild convert(EclipseProjectVersion3 project) {
        DefaultGradleBuild gradleBuild = new DefaultGradleBuild();
        BasicGradleProject rootProject = toBasicGradleProject(project);
        gradleBuild.setRootProject(rootProject);
        gradleBuild.addProject(rootProject);
        convertChildren(gradleBuild, rootProject, project);
        return gradleBuild;
    }


    private void convertChildren(DefaultGradleBuild gradleBuild, BasicGradleProject rootProject, EclipseProject project) {
        final DomainObjectSet<? extends EclipseProject> children = project.getChildren();
        final List<? extends EclipseProject> childProjects = new ArrayList<EclipseProject>(children);
        Collections.sort(childProjects, new Comparator<EclipseProject>() {
            public int compare(EclipseProject gp1, EclipseProject gp2) {
                return gp1.getGradleProject().getName().compareTo(gp2.getGradleProject().getName());
            }
        });
        for (EclipseProject childProject : childProjects) {
            BasicGradleProject basicGradleProject = toBasicGradleProject(childProject);
            gradleBuild.addProject(basicGradleProject);
            basicGradleProject.setParent(rootProject);
            rootProject.addChild(basicGradleProject);
            convertChildren(gradleBuild, basicGradleProject, childProject);
        }
    }


    private void convertChildren(DefaultGradleBuild gradleBuild, BasicGradleProject rootProject, EclipseProjectVersion3 project) {
        final List<EclipseProjectVersion3> childProjects = new ArrayList<EclipseProjectVersion3>();
        for (EclipseProjectVersion3 eclipseProjectVersion3 : project.getChildren()) {
            childProjects.add(eclipseProjectVersion3);
        }
        Collections.sort(childProjects, new Comparator<EclipseProjectVersion3>() {
            public int compare(EclipseProjectVersion3 gp1, EclipseProjectVersion3 gp2) {
                return gp1.getName().compareTo(gp2.getName());
            }
        });
        for (EclipseProjectVersion3 childProject : childProjects) {
            BasicGradleProject basicGradleProject = toBasicGradleProject(childProject);
            gradleBuild.addProject(basicGradleProject);
            basicGradleProject.setParent(rootProject);
            rootProject.addChild(basicGradleProject);
            convertChildren(gradleBuild, basicGradleProject, childProject);
        }
    }

    private BasicGradleProject toBasicGradleProject(EclipseProject eclipseProject) {
        BasicGradleProject basicGradleProject = new BasicGradleProject();
        final GradleProject gradleProject = eclipseProject.getGradleProject();
        basicGradleProject.setPath(gradleProject.getPath());
        basicGradleProject.setName(gradleProject.getName());
        basicGradleProject.setProjectDirectory(eclipseProject.getProjectDirectory());
        return basicGradleProject;
    }

    private BasicGradleProject toBasicGradleProject(EclipseProjectVersion3 eclipseProjectVersion3) {
        BasicGradleProject basicGradleProject = new BasicGradleProject();
        basicGradleProject.setPath(eclipseProjectVersion3.getPath());
        // TODO is EclipseProjectVersion3#getName() == GradleProject#getName() ?
        basicGradleProject.setName(eclipseProjectVersion3.getName());
        basicGradleProject.setProjectDirectory(eclipseProjectVersion3.getProjectDirectory());
        return basicGradleProject;
    }
}
