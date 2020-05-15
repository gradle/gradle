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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.internal.properties.GradleProperties;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.function.BiConsumer;

public class DefaultGradlePropertiesController implements GradlePropertiesController {

    private State state = new NotLoaded();
    private final GradleProperties sharedGradleProperties = new SharedGradleProperties();
    private final IGradlePropertiesLoader propertiesLoader;
    private final BiConsumer<String, String> systemPropertySetter;

    public DefaultGradlePropertiesController(IGradlePropertiesLoader propertiesLoader) {
        this(propertiesLoader, System::setProperty);
    }

    @VisibleForTesting
    DefaultGradlePropertiesController(
        IGradlePropertiesLoader propertiesLoader,
        BiConsumer<String, String> systemPropertySetter
    ) {
        this.propertiesLoader = propertiesLoader;
        this.systemPropertySetter = systemPropertySetter;
    }

    @Override
    public GradleProperties getGradleProperties() {
        return sharedGradleProperties;
    }

    @Override
    public void loadGradlePropertiesFrom(File settingsDir) {
        state = state.loadGradlePropertiesFrom(settingsDir);
    }

    @Override
    public void applyToSystemProperties(File settingsDir) {
        state = state.applyToSystemProperties(settingsDir);
    }

    private class SharedGradleProperties implements GradleProperties {

        @Nullable
        @Override
        public String find(String propertyName) {
            return gradleProperties().find(propertyName);
        }

        @Override
        public Map<String, String> mergeProperties(Map<String, String> properties) {
            return gradleProperties().mergeProperties(properties);
        }

        private GradleProperties gradleProperties() {
            return state.gradleProperties();
        }
    }

    private interface State {

        GradleProperties gradleProperties();

        State loadGradlePropertiesFrom(File settingsDir);

        State applyToSystemProperties(File settingsDir);
    }

    private class NotLoaded implements State {

        @Override
        public GradleProperties gradleProperties() {
            throw new IllegalStateException("GradleProperties has not been loaded yet.");
        }

        @Override
        public State loadGradlePropertiesFrom(File settingsDir) {
            return new Loaded(
                propertiesLoader.loadGradleProperties(settingsDir),
                settingsDir
            );
        }

        @Override
        public State applyToSystemProperties(File settingsDir) {
            State loaded = loadGradlePropertiesFrom(settingsDir);
            return loaded.applyToSystemProperties(settingsDir);
        }
    }

    private class Loaded implements State {

        private final LoadedGradleProperties loadedProperties;
        private final File propertiesDir;

        public Loaded(LoadedGradleProperties loadedProperties, File propertiesDir) {
            this.loadedProperties = loadedProperties;
            this.propertiesDir = propertiesDir;
        }

        @Override
        public GradleProperties gradleProperties() {
            return loadedProperties.getGradleProperties();
        }

        @Override
        public State loadGradlePropertiesFrom(File settingsDir) {
            validatePropertiesDir(propertiesDir, settingsDir);
            return this;
        }

        @Override
        public State applyToSystemProperties(File settingsDir) {
            validatePropertiesDir(propertiesDir, settingsDir);
            applyToSystemProperties();
            return new Applied(loadedProperties.getGradleProperties(), propertiesDir);
        }

        private void applyToSystemProperties() {
            for (Map.Entry<String, String> entry : loadedProperties.getSystemProperties().entrySet()) {
                systemPropertySetter.accept(entry.getKey(), entry.getValue());
            }
        }
    }

    private static class Applied implements State {

        private final GradleProperties gradleProperties;
        private final File propertiesDir;

        private Applied(GradleProperties loadedProperties, File propertiesDir) {
            this.gradleProperties = loadedProperties;
            this.propertiesDir = propertiesDir;
        }

        @Override
        public GradleProperties gradleProperties() {
            return gradleProperties;
        }

        @Override
        public State loadGradlePropertiesFrom(File settingsDir) {
            validatePropertiesDir(propertiesDir, settingsDir);
            return this;
        }

        @Override
        public State applyToSystemProperties(File settingsDir) {
            validatePropertiesDir(propertiesDir, settingsDir);
            return this;
        }
    }

    private static void validatePropertiesDir(File previousDir, File newDir) {
        if (!previousDir.equals(newDir)) {
            throw new IllegalStateException(
                String.format(
                    "GradleProperties has already been loaded from '%s' and cannot be loaded from '%s'.",
                    previousDir, newDir
                )
            );
        }
    }
}
