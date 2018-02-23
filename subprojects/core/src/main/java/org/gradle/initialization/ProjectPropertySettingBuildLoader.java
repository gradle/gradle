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

import com.google.common.collect.Maps;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.internal.Pair;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.PropertyMutator;
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

    @Override
    public void load(SettingsInternal settings, GradleInternal gradle) {
        buildLoader.load(settings, gradle);
        setProjectProperties(gradle.getRootProject(), new CachingPropertyApplicator());
    }

    private void setProjectProperties(Project project, CachingPropertyApplicator applicator) {
        addPropertiesToProject(project, applicator);
        for (Project childProject : project.getChildProjects().values()) {
            setProjectProperties(childProject, applicator);
        }
    }

    private void addPropertiesToProject(Project project, CachingPropertyApplicator applicator) {
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

        // this should really be <String, Object>, however properties loader signature expects a <String, String>
        // even if in practice it was never enforced (one can pass other property types, such as boolean) an
        // fixing the method signature would be a binary breaking change in a public API.
        Map<String, String> mergedProperties = propertiesLoader.mergeProperties(new HashMap(projectProperties));
        for (Map.Entry<String, String> entry : mergedProperties.entrySet()) {
            applicator.configureProperty(project, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Applies the given properties to the project and its subprojects, caching property mutators whenever possible
     * to avoid too many searches.
     */
    private static class CachingPropertyApplicator {
        private final Map<Pair<String, ? extends Class<?>>, PropertyMutator> mutators = Maps.newHashMap();
        private Class<? extends Project> projectClazz;

        void configureProperty(Project project, String name, Object value) {
            Class<? extends Project> clazz = project.getClass();
            if (clazz != projectClazz) {
                mutators.clear();
                projectClazz = clazz;
            }
            Class<?> valueType = value == null ? null : value.getClass();
            Pair<String, ? extends Class<?>> key = Pair.of(name, valueType);
            PropertyMutator propertyMutator = mutators.get(key);
            if (propertyMutator != null) {
                propertyMutator.setValue(project, value);
            } else {
                if (!mutators.containsKey(key)) {
                    propertyMutator = JavaReflectionUtil.writeablePropertyIfExists(clazz, name, valueType);
                    mutators.put(key, propertyMutator);
                    if (propertyMutator != null) {
                        propertyMutator.setValue(project, value);
                        return;
                    }
                }
                ExtraPropertiesExtension extraProperties = project.getExtensions().getExtraProperties();
                extraProperties.set(name, value);
            }
        }
    }
}
