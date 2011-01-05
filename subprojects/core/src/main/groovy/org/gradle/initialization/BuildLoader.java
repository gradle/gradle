/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.Clock;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class BuildLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildLoader.class);

    private final IProjectFactory projectFactory;

    public BuildLoader(IProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
    }

    /**
     * Creates the {@link org.gradle.api.internal.GradleInternal} and {@link ProjectInternal} instances for the given root project,
     * ready for the projects to be evaluated.
     */
    public void load(ProjectDescriptor rootProjectDescriptor, GradleInternal gradle,
                     Map<String, String> externalProjectProperties) {
        LOGGER.debug("Loading Project objects");
        Clock clock = new Clock();
        createProjects(rootProjectDescriptor, gradle, externalProjectProperties);
        attachDefaultProject(gradle);
        LOGGER.debug("Timing: Loading projects took: " + clock.getTime());
    }

    private void attachDefaultProject(GradleInternal gradle) {
        ProjectSpec selector = gradle.getStartParameter().getDefaultProjectSelector();
        ProjectInternal defaultProject;
        try {
            defaultProject = selector.selectProject(gradle.getRootProject().getProjectRegistry());
        } catch (InvalidUserDataException e) {
            throw new GradleException(String.format("Could not select the default project for this build. %s",
                    e.getMessage()), e);
        }
        gradle.setDefaultProject(defaultProject);
    }

    private void createProjects(ProjectDescriptor rootProjectDescriptor, GradleInternal gradle,
                                Map<String, String> externalProjectProperties) {
        ProjectInternal rootProject = projectFactory.createProject(rootProjectDescriptor, null, gradle);
        gradle.setRootProject(rootProject);

        addPropertiesToProject(externalProjectProperties, rootProject);
        addProjects(rootProject, rootProjectDescriptor, gradle, externalProjectProperties);
    }

    private void addProjects(ProjectInternal parent, ProjectDescriptor parentProjectDescriptor, GradleInternal gradle,
                             Map<String, String> externalProjectProperties) {
        for (ProjectDescriptor childProjectDescriptor : parentProjectDescriptor.getChildren()) {
            ProjectInternal childProject = projectFactory.createProject(childProjectDescriptor, parent, gradle);
            addPropertiesToProject(externalProjectProperties, childProject);
            addProjects(childProject, childProjectDescriptor, gradle, externalProjectProperties);
        }
    }

    private void addPropertiesToProject(Map<String, String> externalProperties, ProjectInternal project) {
        Properties projectProperties = new Properties();
        File projectPropertiesFile = new File(project.getProjectDir(), Project.GRADLE_PROPERTIES);
        LOGGER.debug("Looking for project properties from: {}", projectPropertiesFile);
        if (projectPropertiesFile.isFile()) {
            projectProperties = GUtil.loadProperties(projectPropertiesFile);
            LOGGER.debug("Adding project properties (if not overwritten by user properties): {}",
                    projectProperties.keySet());
        } else {
            LOGGER.debug("project property file does not exists. We continue!");
        }
        projectProperties.putAll(externalProperties);
        for (Object key : projectProperties.keySet()) {
            project.setProperty((String) key, projectProperties.get(key));
        }
    }
}
