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
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.Clock;
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
public class ProjectsLoader {
    private static Logger logger = LoggerFactory.getLogger(ProjectsLoader.class);

    private IProjectFactory projectFactory;

    private ProjectInternal rootProject;

    private ProjectInternal currentProject;

    public ProjectsLoader() {

    }

    public ProjectsLoader(IProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
    }

    public ProjectsLoader load(ProjectDescriptor rootProjectDescriptor, ClassLoader buildScriptClassLoader, StartParameter startParameter,
                               Map<String, String> externalProjectProperties) {
        logger.info("++ Loading Project objects");
        Clock clock = new Clock();
        rootProject = createProjects(rootProjectDescriptor, buildScriptClassLoader, startParameter, externalProjectProperties);
        currentProject = (ProjectInternal) rootProject.getProjectRegistry().getProject(startParameter.getCurrentDir());
        logger.debug("Timing: Loading projects took: " + clock.getTime());
        return this;
    }

    private ProjectInternal createProjects(ProjectDescriptor rootProjectDescriptor, ClassLoader buildScriptClassLoader, StartParameter startParameter,
                                           Map<String, String> externalProjectProperties) {
        logger.debug("Creating the projects and evaluating the project files!");
        logger.debug("Adding external properties: {}", externalProjectProperties.keySet());
        logger.debug("Looking for system project properties");
        ProjectInternal rootProject = projectFactory.createProject(rootProjectDescriptor.getName(), null,
                rootProjectDescriptor.getDir(), buildScriptClassLoader);
        addPropertiesToProject(startParameter.getGradleUserHomeDir(), externalProjectProperties, rootProject);
        addProjects(rootProject, rootProjectDescriptor, startParameter, externalProjectProperties);
        return rootProject;
    }

    private void addProjects(ProjectInternal parent, ProjectDescriptor parentProjectDescriptor, StartParameter startParameter,
                             Map<String, String> externalProjectProperties) {
        for (DefaultProjectDescriptor childProjectDescriptor : parentProjectDescriptor.getChildren()) {
            ProjectInternal childProject = (ProjectInternal) parent.addChildProject(childProjectDescriptor.getName(), childProjectDescriptor.getDir());
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
            try {
                projectProperties.load(new FileInputStream(projectPropertiesFile));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            logger.debug("Adding project properties (if not overwritten by user properties): {}", projectProperties.keySet());
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

    public void reset() {
        projectFactory.reset();    
    }

    public IProjectFactory getProjectFactory() {
        return projectFactory;
    }

    public void setProjectFactory(IProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
    }

    public ProjectInternal getRootProject() {
        return rootProject;
    }

    public void setRootProject(ProjectInternal rootProject) {
        this.rootProject = rootProject;
    }

    public ProjectInternal getCurrentProject() {
        return currentProject;
    }

    public void setCurrentProject(ProjectInternal currentProject) {
        this.currentProject = currentProject;
    }
}
