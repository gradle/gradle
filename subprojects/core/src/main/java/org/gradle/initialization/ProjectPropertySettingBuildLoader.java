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
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.internal.Pair;
import org.gradle.internal.reflect.PropertyMutator;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.gradle.api.internal.project.ProjectHierarchyUtils.getChildProjectsForInternalUse;
import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.internal.reflect.JavaPropertyReflectionUtil.writeablePropertyIfExists;

public class ProjectPropertySettingBuildLoader implements BuildLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectPropertySettingBuildLoader.class);

    private final GradleProperties gradleProperties;
    private final FileResourceListener fileResourceListener;
    private final BuildLoader buildLoader;

    public ProjectPropertySettingBuildLoader(GradleProperties gradleProperties, BuildLoader buildLoader, FileResourceListener fileResourceListener) {
        this.buildLoader = buildLoader;
        this.gradleProperties = gradleProperties;
        this.fileResourceListener = fileResourceListener;
    }

    @Override
    public void load(SettingsInternal settings, GradleInternal gradle) {
        buildLoader.load(settings, gradle);
        Project rootProject = gradle.getRootProject();
        setProjectProperties(rootProject, new CachingPropertyApplicator(rootProject.getClass()));
    }

    private void setProjectProperties(Project project, CachingPropertyApplicator applicator) {
        addPropertiesToProject(project, applicator);
        for (Project childProject : getChildProjectsForInternalUse(project)) {
            setProjectProperties(childProject, applicator);
        }
    }

    private void addPropertiesToProject(Project project, CachingPropertyApplicator applicator) {
        applicator.beginProjectProperties();
        File projectPropertiesFile = new File(project.getProjectDir(), Project.GRADLE_PROPERTIES);
        LOGGER.debug("Looking for project properties from: {}", projectPropertiesFile);
        fileResourceListener.fileObserved(projectPropertiesFile);
        if (projectPropertiesFile.isFile()) {
            Properties projectProperties = GUtil.loadProperties(projectPropertiesFile);
            LOGGER.debug("Adding project properties (if not overwritten by user properties): {}",
                projectProperties.keySet());
            configurePropertiesOf(project, applicator, uncheckedCast(projectProperties));
        } else {
            LOGGER.debug("project property file does not exists. We continue!");
            configurePropertiesOf(project, applicator, ImmutableMap.of());
        }
        ((ExtraPropertiesExtensionInternal) project.getExtensions().getExtraProperties())
            .setGradleProperties(applicator.endProjectProperties());
    }

    // {@code mergedProperties} should really be <String, Object>, however properties loader signature expects a <String, String>
    // even if in practice it was never enforced (one can pass other property types, such as boolean) and
    // fixing the method signature would be a binary breaking change in a public API.
    private void configurePropertiesOf(Project project, CachingPropertyApplicator applicator, Map<String, Object> properties) {
        for (Map.Entry<String, Object> entry : gradleProperties.mergeProperties(properties).entrySet()) {
            applicator.configureProperty(project, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Applies the given properties to the project and its subprojects, caching property mutators whenever possible
     * to avoid too many searches.
     */
    private static class CachingPropertyApplicator {
        private final Class<? extends Project> projectClass;
        private final Map<Pair<String, ? extends Class<?>>, Optional<PropertyMutator>> mutators = new HashMap<>();
        private ImmutableMap.Builder<String, Object> extraProjectProperties;

        CachingPropertyApplicator(Class<? extends Project> projectClass) {
            this.projectClass = projectClass;
        }

        void configureProperty(Project project, String name, Object value) {
            if (isPossibleProperty(name)) {
                assert project.getClass() == projectClass;
                PropertyMutator propertyMutator = propertyMutatorFor(name, typeOf(value));
                if (propertyMutator != null) {
                    propertyMutator.setValue(project, value);
                } else {
                    extraProjectProperties.put(name, value);
                }
            }
        }

        @Nullable
        private static Class<?> typeOf(@Nullable Object value) {
            return value == null ? null : value.getClass();
        }

        @Nullable
        private PropertyMutator propertyMutatorFor(String propertyName, @Nullable Class<?> valueType) {
            return mutators.computeIfAbsent(
                Pair.of(propertyName, valueType),
                key -> {
                    assert key.left != null;
                    return Optional.ofNullable(writeablePropertyIfExists(projectClass, key.left, key.right));
                }
            ).orElse(null);
        }

        /**
         * In a properties file, entries like '=' or ':' on a single line define a property with an empty string name and value.
         * We know that no property will have an empty property name.
         *
         * @see java.util.Properties#load(java.io.Reader)
         */
        private static boolean isPossibleProperty(String name) {
            return !name.isEmpty();
        }

        public void beginProjectProperties() {
            extraProjectProperties = ImmutableMap.builder();
        }

        public Map<String, Object> endProjectProperties() {
            try {
                return extraProjectProperties.build();
            } finally {
                extraProjectProperties = null;
            }
        }
    }
}
