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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.plugins.ExtraPropertiesExtensionInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.properties.GradlePropertiesController;
import org.gradle.initialization.properties.FilteringGradleProperties;

import java.util.Set;

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
        Set<String> consumedProperties = assignSelectedPropertiesDirectly(project, projectGradleProperties);
        installProjectExtraPropertiesDefaults(project, projectGradleProperties, consumedProperties);
    }

    /**
     * Assigns selected properties from the provided Gradle properties to the given project instance.
     *
     * @implNote The properties are looked up by known names to avoid eager access of all Gradle-properties.
     */
    @SuppressWarnings({"deprecation"})
    private static Set<String> assignSelectedPropertiesDirectly(
        ProjectInternal project,
        GradleProperties projectGradleProperties
    ) {
        ImmutableSet.Builder<String> consumedProperties = ImmutableSet.builder();
        // Historically, we filtered out properties with empty names here.
        // They could appear in case the properties file has lines containing only '=' or ':'
        consumedProperties.add("");

        // The `Object` type of variables below is intentional.
        // This is a relaxation of the type to support an edge-case of GradleBuild task
        // that allows passing non-String properties via `startParameter.projectProperties`.
        // As they make their way into `GradleProperties`, the `find` method can return non-String values
        // despite the declared String type
        // TODO: Remove non-String project properties support in Gradle 10 - https://github.com/gradle/gradle/issues/34454

        String versionName = "version";
        Object versionValue = projectGradleProperties.findUnsafe(versionName);
        if (versionValue != null) {
            project.setVersion(versionValue);
            consumedProperties.add(versionName);
        }

        String groupName = "group";
        Object groupValue = projectGradleProperties.findUnsafe(groupName);
        if (groupValue != null) {
            project.setGroup(groupValue);
            consumedProperties.add(groupName);
        }

        String statusName = "status";
        Object statusValue = projectGradleProperties.findUnsafe(statusName);
        if (statusValue != null) {
            project.setStatus(statusValue);
            consumedProperties.add(statusName);
        }

        String buildDirName = "buildDir";
        Object buildDirValue = projectGradleProperties.findUnsafe(buildDirName);
        if (buildDirValue != null) {
            project.setBuildDir(buildDirValue);
            consumedProperties.add(buildDirName);
        }

        String descriptionName = "description";
        Object descriptionValue = projectGradleProperties.findUnsafe(descriptionName);
        // This intentionally differs from others for backward-compatibility.
        // Other setters accept `Object` as an argument and therefore consume a property of any type.
        // If it so happens that the description value is not a String, it would not match the
        // `Project.setDescription(String)` setter and thus the property would end up in the map of extra-properties.
        if (descriptionValue instanceof String) {
            project.setDescription((String) descriptionValue);
            consumedProperties.add(descriptionName);
        }
        return consumedProperties.build();
    }

    private static void installProjectExtraPropertiesDefaults(ProjectInternal project, GradleProperties projectGradleProperties, Set<String> consumedProperties) {
        ExtraPropertiesExtensionInternal extraPropertiesContainer = (ExtraPropertiesExtensionInternal) project.getExtensions().getExtraProperties();
        // TODO:configuration-cache avoid the FilteringGradleProperties indirection when no properties are consumed
        extraPropertiesContainer.setGradleProperties(
            new FilteringGradleProperties(
                projectGradleProperties,
                it -> !consumedProperties.contains(it)
            )
        );
    }

}
