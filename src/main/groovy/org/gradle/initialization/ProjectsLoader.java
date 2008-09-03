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
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.initialization.DefaultSettings;
import org.gradle.util.Clock;
import org.gradle.util.PathHelper;
import org.gradle.util.GradleUtil;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.util.HashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class ProjectsLoader {
    private static Logger logger = LoggerFactory.getLogger(ProjectsLoader.class);

    public static final String SYSTEM_PROJECT_PROPERTIES_PREFIX = "org.gradle.project.";

    public static final String ENV_PROJECT_PROPERTIES_PREFIX = "ORG_GRADLE_PROJECT_";

    private IProjectFactory projectFactory;

    private ProjectInternal rootProject;

    private ProjectInternal currentProject;

    public ProjectsLoader() {

    }

    public ProjectsLoader(IProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
    }

    public ProjectsLoader load(DefaultSettings settings, ClassLoader buildScriptClassLoader, StartParameter startParameter,
                               Map<String, String> externalProjectProperties, Map systemProperties, Map envProperties) {
        logger.info("++ Loading Project objects");
        Clock clock = new Clock();
        rootProject = createProjects(settings, buildScriptClassLoader, startParameter, externalProjectProperties, systemProperties, envProperties);
        currentProject = (ProjectInternal) rootProject.project(PathHelper.getCurrentProjectPath(rootProject.getRootDir(), startParameter.getCurrentDir()));
        logger.debug("Timing: Loading projects took: " + clock.getTime());
        return this;
    }

    // todo Why are the projectProperties passed only to the root project and the userHomeProperties passed to every Project
    private ProjectInternal createProjects(DefaultSettings settings, ClassLoader buildScriptClassLoader, StartParameter startParameter,
                                           Map<String, String> externalProjectProperties, Map systemProperties, Map envProperties) {
        logger.debug("Creating the projects and evaluating the project files!");
        Map systemAndEnvProjectProperties = GUtil.addMaps(getSystemProjectProperties(systemProperties),
                getEnvProjectProperties(envProperties));
        if (GUtil.isTrue(systemAndEnvProjectProperties)) {
            logger.debug("Added system and env project properties: {}", systemAndEnvProjectProperties.keySet());
        }
        logger.debug("Adding external properties (if not overwritten by system project properties: {}", externalProjectProperties.keySet());
        logger.debug("Looking for system project properties");
        ProjectInternal rootProject = projectFactory.createProject(settings.getSettingsFinder().getSettingsDir().getName(), null,
                settings.getSettingsFinder().getSettingsDir(), buildScriptClassLoader);
        addPropertiesToProject(startParameter.getGradleUserHomeDir(), GUtil.addMaps(externalProjectProperties, startParameter.getProjectProperties()),
                systemAndEnvProjectProperties, rootProject);
        for (String path : settings.getProjectPaths()) {
            String[] folders = path.split(Project.PATH_SEPARATOR);
            ProjectInternal parent = rootProject;
            for (String name : folders) {
                if (parent.getChildProjects().get(name) == null) {
                    parent.getChildProjects().put(name, parent.addChildProject(name));
                    addPropertiesToProject(startParameter.getGradleUserHomeDir(), externalProjectProperties, systemAndEnvProjectProperties,
                            (ProjectInternal) parent.getChildProjects().get(name));
                }
                parent = (ProjectInternal) parent.getChildProjects().get(name);
            }
        }
        return rootProject;
    }

    private void addPropertiesToProject(File gradleUserHomeDir, Map userProperties, Map systemProjectProperties, ProjectInternal project) {
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
        projectProperties.putAll(userProperties);
        projectProperties.putAll(systemProjectProperties);
        try {
            project.setGradleUserHome(gradleUserHomeDir.getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (Object key : projectProperties.keySet()) {
            project.setProperty((String) key, projectProperties.get(key));
        }
    }

    private Map getSystemProjectProperties(Map<String, String> systemProperties) {
        Map<String, String> systemProjectProperties = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
            if (entry.getKey().startsWith(SYSTEM_PROJECT_PROPERTIES_PREFIX) &&
                    entry.getKey().length() > SYSTEM_PROJECT_PROPERTIES_PREFIX.length()) {
                systemProjectProperties.put(entry.getKey().substring(SYSTEM_PROJECT_PROPERTIES_PREFIX.length()), entry.getValue());
            }
        }
        return systemProjectProperties;
    }

    private Map getEnvProjectProperties(Map<String, String> env) {
        Map<String, String> envProjectProperties = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getKey().startsWith(ENV_PROJECT_PROPERTIES_PREFIX) &&
                    entry.getKey().length() > ENV_PROJECT_PROPERTIES_PREFIX.length()) {
                envProjectProperties.put(entry.getKey().substring(ENV_PROJECT_PROPERTIES_PREFIX.length()), entry.getValue());
            }
        }
        return envProjectProperties;
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
