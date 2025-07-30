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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.plugins.ExtraPropertiesExtensionInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.properties.GradleProperties;

import java.util.Map;

import static org.gradle.api.internal.project.ProjectHierarchyUtils.getChildProjectsForInternalUse;

public class ProjectPropertySettingBuildLoader implements BuildLoader {

    private final GradlePropertiesController gradlePropertiesController;
    private final BuildLoader buildLoader;

    public ProjectPropertySettingBuildLoader(
        GradlePropertiesController gradlePropertiesController,
        BuildLoader buildLoader
    ) {
        this.gradlePropertiesController = gradlePropertiesController;
        this.buildLoader = buildLoader;
    }

    @Override
    public void load(SettingsInternal settings, GradleInternal gradle) {
        buildLoader.load(settings, gradle);
        setProjectProperties(gradle.getRootProject());
    }

    private void setProjectProperties(ProjectInternal project) {
        addPropertiesToProject(project);
        for (Project childProject : getChildProjectsForInternalUse(project)) {
            setProjectProperties((ProjectInternal) childProject);
        }
    }

    private void addPropertiesToProject(ProjectInternal project) {
        gradlePropertiesController.loadGradleProperties(project.getProjectIdentity(), project.getProjectDir());
        GradleProperties projectGradleProperties = gradlePropertiesController.getGradleProperties(project.getProjectIdentity());
        Map<String, String> mergedProjectProperties = projectGradleProperties.getProperties();

        ImmutableMap.Builder<String, Object> extraProjectPropertiesBuilder = ImmutableMap.builder();

        for (Map.Entry<String, String> entry : mergedProjectProperties.entrySet()) {
            String propertyName = entry.getKey();
            // This is intentional relaxation of the type to support an edge-case of GradleBuild task
            // that allows passing non-String properties via `startParameter.projectProperties`.
            // The latter map is typed `Map<String, String>` but due to type erasure, the actual values were never explicitly checked.
            Object propertyValue = entry.getValue();
            assignOrCollectProperty(project, extraProjectPropertiesBuilder, propertyName, propertyValue);
        }

        installProjectExtraPropertiesDefaults(project, extraProjectPropertiesBuilder.build());
    }

    @SuppressWarnings("deprecation")
    private static void assignOrCollectProperty(
        Project project,
        ImmutableMap.Builder<String, Object> extraProjectProperties,
        String propertyName,
        Object propertyValue
    ) {
        if (propertyName.isEmpty()) {
            // Historically, we filtered out properties with empty names here.
            // They could appear in case the properties file has lines containing only '=' or ':'
            return;
        }

        switch (propertyName) {
            case "version":
                project.setVersion(propertyValue);
                break;
            case "group":
                project.setGroup(propertyValue);
                break;
            case "description":
                // TODO: Remove non-String project properties support in Gradle 10 - https://github.com/gradle/gradle/issues/34454
                if (propertyValue instanceof String) {
                    project.setDescription((String) propertyValue);
                } else {
                    extraProjectProperties.put(propertyName, propertyValue);
                }
                break;
            case "status":
                project.setStatus(propertyValue);
                break;
            case "buildDir":
                project.setBuildDir(propertyValue);
                break;
            default:
                extraProjectProperties.put(propertyName, propertyValue);
                break;
        }
    }

    private static void installProjectExtraPropertiesDefaults(Project project, Map<String, Object> extraProperties) {
        ExtraPropertiesExtensionInternal extraPropertiesContainer = (ExtraPropertiesExtensionInternal) project.getExtensions().getExtraProperties();
        extraPropertiesContainer.setGradleProperties(extraProperties);
    }
}
