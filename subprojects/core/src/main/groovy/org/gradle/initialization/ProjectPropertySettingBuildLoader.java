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

import groovy.lang.MissingPropertyException;
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ProjectPropertySettingBuildLoader implements BuildLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectPropertySettingBuildLoader.class);

    private final IGradlePropertiesLoader propertiesLoader;
    private final BuildLoader buildLoader;

    public ProjectPropertySettingBuildLoader(IGradlePropertiesLoader propertiesLoader, BuildLoader buildLoader) {
        this.buildLoader = buildLoader;
        this.propertiesLoader = propertiesLoader;
    }

    public void load(ProjectDescriptor rootProjectDescriptor, ProjectDescriptor defaultProject, GradleInternal gradle, ClassLoaderScope classLoaderScope) {
        buildLoader.load(rootProjectDescriptor, defaultProject, gradle, classLoaderScope);
        setProjectProperties(gradle.getRootProject());
    }

    private void setProjectProperties(Project project) {
        addPropertiesToProject(project);
        for (Project childProject : project.getChildProjects().values()) {
            setProjectProperties(childProject);
        }
    }

    private void addPropertiesToProject(Project project) {
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
        
        Map<String, String> mergedProperties = propertiesLoader.mergeProperties(new HashMap(projectProperties));
        ExtraPropertiesExtension extraProperties = new DslObject(project).getExtensions().getExtraProperties();
        for (Map.Entry<String, String> entry: mergedProperties.entrySet()) {
            try {
                project.setProperty(entry.getKey(), entry.getValue());
            } catch (MissingPropertyException e) {
                if (!entry.getKey().equals(e.getProperty())) {
                    throw e;
                }
                // Ignore and define as an extra property
                extraProperties.set(entry.getKey(), entry.getValue());
            }
        }
    }
}
