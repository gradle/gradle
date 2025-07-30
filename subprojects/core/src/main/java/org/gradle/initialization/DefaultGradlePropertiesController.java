/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.initialization.properties.MutableGradleProperties;
import org.gradle.initialization.properties.ProjectPropertiesLoader;
import org.gradle.initialization.properties.SystemPropertiesInstaller;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultGradlePropertiesController implements GradlePropertiesController {

    private final IGradlePropertiesLoader propertiesLoader;
    private final SystemPropertiesInstaller systemPropertiesInstaller;
    private final ProjectPropertiesLoader projectPropertiesLoader;

    private final ConcurrentMap<BuildIdentifier, SharedGradleProperties> buildScopedGradleProperties = new ConcurrentHashMap<>();

    public DefaultGradlePropertiesController(
        IGradlePropertiesLoader propertiesLoader,
        SystemPropertiesInstaller systemPropertiesInstaller,
        ProjectPropertiesLoader projectPropertiesLoader
    ) {
        this.propertiesLoader = propertiesLoader;
        this.systemPropertiesInstaller = systemPropertiesInstaller;
        this.projectPropertiesLoader = projectPropertiesLoader;
    }

    @Override
    public GradleProperties getGradleProperties(BuildIdentifier buildId) {
        return getOrCreateGradleProperties(buildId);
    }

    @Override
    public void loadGradlePropertiesFrom(BuildIdentifier buildId, File buildRootDir, boolean setSystemProperties) {
        getOrCreateGradleProperties(buildId).loadGradlePropertiesFrom(buildRootDir, setSystemProperties);
    }

    @Override
    public void unloadGradleProperties(BuildIdentifier buildId) {
        getOrCreateGradleProperties(buildId).unload();
    }

    private SharedGradleProperties getOrCreateGradleProperties(BuildIdentifier buildId) {
        return buildScopedGradleProperties.computeIfAbsent(buildId, SharedGradleProperties::new);
    }

    private class SharedGradleProperties implements GradleProperties {

        private final String displayName;
        private volatile State state;

        public SharedGradleProperties(BuildIdentifier buildId) {
            displayName = buildId.toString();
            state = new NotLoaded(displayName);
        }

        @Nullable
        @Override
        public String find(String propertyName) {
            return gradleProperties().find(propertyName);
        }

        @Override
        public Map<String, String> mergeProperties(Map<String, String> properties) {
            return gradleProperties().mergeProperties(properties);
        }

        @Override
        public Map<String, String> getProperties() {
            return gradleProperties().getProperties();
        }

        private GradleProperties gradleProperties() {
            return state.gradleProperties();
        }

        private void loadGradlePropertiesFrom(File buildRootDir, boolean setSystemProperties) {
            State loadedState = state.loadGradlePropertiesFrom(buildRootDir, setSystemProperties);
            state = loadedState;
        }

        private void unload() {
            state = new NotLoaded(displayName);
        }
    }

    private interface State {

        GradleProperties gradleProperties();

        State loadGradlePropertiesFrom(File settingsDir, boolean setSystemProperties);
    }

    private class NotLoaded implements State {

        private final String displayName;

        private NotLoaded(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public GradleProperties gradleProperties() {
            throw new IllegalStateException(String.format("GradleProperties for %s have not been loaded yet.", displayName));
        }

        @Override
        public State loadGradlePropertiesFrom(File settingsDir, boolean setSystemProperties) {
            MutableGradleProperties loadedProperties = propertiesLoader.loadGradleProperties(settingsDir);

            if (setSystemProperties) {
                systemPropertiesInstaller.setSystemPropertiesFrom(loadedProperties);
            }

            Map<String, String> projectProperties = projectPropertiesLoader.loadProjectProperties();
            loadedProperties.updateOverrideProperties(projectProperties);
            return new Loaded(loadedProperties, settingsDir);
        }

    }

    private static class Loaded implements State {

        private final GradleProperties gradleProperties;
        private final File propertiesDir;

        public Loaded(MutableGradleProperties gradleProperties, File propertiesDir) {
            this.gradleProperties = gradleProperties;
            this.propertiesDir = propertiesDir;
        }

        @Override
        public GradleProperties gradleProperties() {
            return gradleProperties;
        }

        @Override
        public State loadGradlePropertiesFrom(File settingsDir, boolean setSystemProperties) {
            if (!propertiesDir.equals(settingsDir)) {
                throw new IllegalStateException(
                    String.format(
                        "GradleProperties has already been loaded from '%s' and cannot be loaded from '%s'.",
                        propertiesDir, settingsDir
                    )
                );
            }
            return this;
        }
    }
}
