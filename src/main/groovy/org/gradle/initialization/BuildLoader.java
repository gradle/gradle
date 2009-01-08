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

package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.invocation.DefaultBuild;
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.BuildInternal;
import org.gradle.util.Clock;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class BuildLoader {
    private static Logger logger = LoggerFactory.getLogger(BuildLoader.class);

    private IProjectFactory projectFactory;

    public BuildLoader() {

    }

    public BuildLoader(IProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
    }

    /**
     * Creates the {@link BuildInternal} and {@link ProjectInternal} instances for the given root project,
     * ready for the projects to be evaluated.
     */
    public BuildInternal load(ProjectDescriptor rootProjectDescriptor, ClassLoader buildScriptClassLoader,
                              StartParameter startParameter,
                              Map<String, String> externalProjectProperties) {
        logger.debug("Loading Project objects");
        Clock clock = new Clock();
        DefaultBuild build = createProjects(rootProjectDescriptor, buildScriptClassLoader, startParameter,
                externalProjectProperties);
        ProjectInternal currentProject = (ProjectInternal) build.getRootProject().getProjectRegistry().getProject(
                startParameter.getCurrentDir());
        assert currentProject != null;
        build.setCurrentProject(currentProject);
        logger.debug("Timing: Loading projects took: " + clock.getTime());
        return build;
    }

    private DefaultBuild createProjects(ProjectDescriptor rootProjectDescriptor, ClassLoader buildScriptClassLoader,
                                        StartParameter startParameter,
                                        Map<String, String> externalProjectProperties) {
        DefaultBuild build = new DefaultBuild(startParameter, buildScriptClassLoader);
        ProjectInternal rootProject = projectFactory.createProject(rootProjectDescriptor.getName(), null,
                rootProjectDescriptor.getDir(), build);
        build.setRootProject(rootProject);

        addPropertiesToProject(startParameter.getGradleUserHomeDir(), externalProjectProperties, rootProject);
        addProjects(rootProject, rootProjectDescriptor, startParameter, externalProjectProperties);
        return build;
    }

    private void addProjects(ProjectInternal parent, ProjectDescriptor parentProjectDescriptor,
                             StartParameter startParameter,
                             Map<String, String> externalProjectProperties) {
        for (ProjectDescriptor childProjectDescriptor : parentProjectDescriptor.getChildren()) {
            ProjectInternal childProject = (ProjectInternal) parent.addChildProject(childProjectDescriptor.getName(),
                    childProjectDescriptor.getDir());
            addPropertiesToProject(startParameter.getGradleUserHomeDir(), externalProjectProperties,
                    childProject);
            addProjects(childProject, childProjectDescriptor, startParameter, externalProjectProperties);
        }
    }

    private void addPropertiesToProject(File gradleUserHomeDir, Map externalProperties, ProjectInternal project) {
        Properties projectProperties = new Properties();
        File projectPropertiesFile = new File(project.getProjectDir(), Project.GRADLE_PROPERTIES);
        logger.debug("Looking for project properties from: {}", projectPropertiesFile);
        if (projectPropertiesFile.isFile()) {
            projectProperties = GUtil.loadProperties(projectPropertiesFile);
            logger.debug("Adding project properties (if not overwritten by user properties): {}",
                    projectProperties.keySet());
        } else {
            logger.debug("project property file does not exists. We continue!");
        }
        projectProperties.putAll(externalProperties);
        try {
            project.setGradleUserHome(gradleUserHomeDir.getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (Object key : projectProperties.keySet()) {
            project.setProperty((String) key, projectProperties.get(key));
        }
    }

    public IProjectFactory getProjectFactory() {
        return projectFactory;
    }

    public void setProjectFactory(IProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
    }
}
