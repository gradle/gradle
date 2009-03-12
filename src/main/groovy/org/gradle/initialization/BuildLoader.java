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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.GradleException;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.BuildInternal;
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
                              StartParameter startParameter, Map<String, String> externalProjectProperties) {
        logger.debug("Loading Project objects");
        Clock clock = new Clock();
        DefaultBuild build = createProjects(rootProjectDescriptor, buildScriptClassLoader, startParameter,
                externalProjectProperties);
        attachDefaultProject(startParameter, build);
        logger.debug("Timing: Loading projects took: " + clock.getTime());
        return build;
    }

    private void attachDefaultProject(StartParameter startParameter, DefaultBuild build) {
        ProjectSpec selector = startParameter.getDefaultProjectSelector();
        ProjectInternal defaultProject;
        try {
            defaultProject = selector.selectProject(build.getRootProject().getProjectRegistry());
        } catch (InvalidUserDataException e) {
            throw new GradleException(String.format("Could not select the default project for this build. %s",
                    e.getMessage()), e);
        }
        build.setDefaultProject(defaultProject);
    }

    private DefaultBuild createProjects(ProjectDescriptor rootProjectDescriptor, ClassLoader buildScriptClassLoader,
                                        StartParameter startParameter,
                                        Map<String, String> externalProjectProperties) {
        DefaultBuild build = new DefaultBuild(startParameter, buildScriptClassLoader);
        ProjectInternal rootProject = projectFactory.createProject(rootProjectDescriptor, null, build);
        build.setRootProject(rootProject);

        addPropertiesToProject(externalProjectProperties, rootProject);
        addProjects(rootProject, rootProjectDescriptor, build, externalProjectProperties);
        return build;
    }

    private void addProjects(ProjectInternal parent, ProjectDescriptor parentProjectDescriptor, BuildInternal build,
                             Map<String, String> externalProjectProperties) {
        for (ProjectDescriptor childProjectDescriptor : parentProjectDescriptor.getChildren()) {
            ProjectInternal childProject = projectFactory.createProject(childProjectDescriptor, parent, build);
            addPropertiesToProject(externalProjectProperties, childProject);
            addProjects(childProject, childProjectDescriptor, build, externalProjectProperties);
        }
    }

    private void addPropertiesToProject(Map<String, String> externalProperties, ProjectInternal project) {
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
