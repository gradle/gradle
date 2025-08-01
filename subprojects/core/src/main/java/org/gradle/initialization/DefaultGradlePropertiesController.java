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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.initialization.properties.MutableGradleProperties;
import org.gradle.initialization.properties.ProjectPropertiesLoader;
import org.gradle.initialization.properties.ResolvedGradleProperties;
import org.gradle.initialization.properties.SystemPropertiesInstaller;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultGradlePropertiesController implements GradlePropertiesController {

    private final Environment environment;
    private final IGradlePropertiesLoader propertiesLoader;
    private final SystemPropertiesInstaller systemPropertiesInstaller;
    private final ProjectPropertiesLoader projectPropertiesLoader;

    private final ConcurrentMap<BuildIdentifier, BuildScopedGradleProperties> buildProperties = new ConcurrentHashMap<>();
    private final ConcurrentMap<ProjectIdentity, ProjectScopedGradleProperties> projectProperties = new ConcurrentHashMap<>();

    public DefaultGradlePropertiesController(
        Environment environment,
        IGradlePropertiesLoader propertiesLoader,
        SystemPropertiesInstaller systemPropertiesInstaller,
        ProjectPropertiesLoader projectPropertiesLoader
    ) {
        this.environment = environment;
        this.propertiesLoader = propertiesLoader;
        this.systemPropertiesInstaller = systemPropertiesInstaller;
        this.projectPropertiesLoader = projectPropertiesLoader;
    }

    @Override
    public GradleProperties getGradleProperties(BuildIdentifier buildId) {
        return getOrCreateGradleProperties(buildId);
    }

    @Override
    public void loadGradleProperties(BuildIdentifier buildId, File buildRootDir, boolean setSystemProperties) {
        getOrCreateGradleProperties(buildId).loadProperties(buildRootDir, setSystemProperties);
    }

    @Override
    public void unloadGradleProperties(BuildIdentifier buildId) {
        if (!projectProperties.isEmpty()) {
            throw new IllegalStateException("Cannot unload Gradle properties after loading project properties.");
        }
        getOrCreateGradleProperties(buildId).unload();
    }

    @Override
    public GradleProperties getGradleProperties(ProjectIdentity projectId) {
        return getOrCreateGradleProperties(projectId);
    }

    @Override
    public void loadGradleProperties(ProjectIdentity projectId, File projectDir) {
        getOrCreateGradleProperties(projectId).loadProperties(projectDir, false);
    }

    private BuildScopedGradleProperties getOrCreateGradleProperties(BuildIdentifier buildId) {
        return buildProperties.computeIfAbsent(buildId, BuildScopedGradleProperties::new);
    }

    private ProjectScopedGradleProperties getOrCreateGradleProperties(ProjectIdentity projectId) {
        return projectProperties.computeIfAbsent(projectId, ProjectScopedGradleProperties::new);
    }

    private abstract static class SharedGradleProperties<I, T extends GradleProperties> implements GradleProperties {

        private final I id;
        private volatile PropertiesState<T> state;

        public SharedGradleProperties(I id) {
            this.id = id;
            this.state = createNotLoadedState(id);
        }

        @Nullable
        @Override
        public String find(String propertyName) {
            return gradleProperties().find(propertyName);
        }

        @Override
        public Map<String, String> getProperties() {
            return gradleProperties().getProperties();
        }

        protected T gradleProperties() {
            return state.gradleProperties();
        }

        protected void loadProperties(File dir, boolean setSystemProperties) {
            state = state.loadProperties(dir, setSystemProperties);
        }

        protected void unload() {
            state = createNotLoadedState(id);
        }

        protected abstract PropertiesState<T> createNotLoadedState(I id);
    }

    private class BuildScopedGradleProperties extends SharedGradleProperties<BuildIdentifier, MutableGradleProperties> {
        public BuildScopedGradleProperties(BuildIdentifier id) {
            super(id);
        }

        @Override
        protected NotLoadedBuildProperties createNotLoadedState(BuildIdentifier id) {
            return new NotLoadedBuildProperties(id);
        }
    }

    private class ProjectScopedGradleProperties extends SharedGradleProperties<ProjectIdentity, GradleProperties> {
        public ProjectScopedGradleProperties(ProjectIdentity id) {
            super(id);
        }

        @Override
        protected NotLoadedProjectProperties createNotLoadedState(ProjectIdentity id) {
            return new NotLoadedProjectProperties(id);
        }
    }

    private interface PropertiesState<T extends GradleProperties> {
        T gradleProperties();

        PropertiesState<T> loadProperties(File dir, boolean setSystemProperties);
    }

    private class NotLoadedBuildProperties implements PropertiesState<MutableGradleProperties> {

        private final BuildIdentifier buildId;

        private NotLoadedBuildProperties(BuildIdentifier buildId) {
            this.buildId = buildId;
        }

        @Override
        public MutableGradleProperties gradleProperties() {
            throw new IllegalStateException(String.format("GradleProperties for %s have not been loaded yet.", buildId));
        }

        @Override
        public PropertiesState<MutableGradleProperties> loadProperties(File buildRootDir, boolean setSystemProperties) {
            MutableGradleProperties loadedProperties = propertiesLoader.loadGradleProperties(buildRootDir);

            if (setSystemProperties) {
                boolean isRootBuild = DefaultBuildIdentifier.ROOT.equals(buildId);
                systemPropertiesInstaller.setSystemPropertiesFrom(loadedProperties, isRootBuild);
            }

            Map<String, String> projectProperties = projectPropertiesLoader.loadProjectProperties();
            loadedProperties.updateOverrideProperties(projectProperties);
            return new Loaded<>(loadedProperties, buildRootDir);
        }
    }

    private class NotLoadedProjectProperties implements PropertiesState<GradleProperties> {

        private final ProjectIdentity projectId;

        private NotLoadedProjectProperties(ProjectIdentity projectId) {
            this.projectId = projectId;
        }

        @Override
        public GradleProperties gradleProperties() {
            throw new IllegalStateException(String.format("GradleProperties for %s have not been loaded yet.", projectId));
        }

        @Override
        public PropertiesState<GradleProperties> loadProperties(File projectDir, boolean setSystemProperties) {
            // setSystemProperties is ignored for project properties
            Map<String, String> loadedProperties = loadProjectLocalProperties(projectDir);
            BuildScopedGradleProperties buildGradleProperties = getOrCreateGradleProperties(projectId.getBuildIdentifier());
            Map<String, String> resolvedProjectGradleProperties =
                buildGradleProperties.gradleProperties().mergeProperties(loadedProperties);
            GradleProperties projectGradleProperties = new ResolvedGradleProperties(resolvedProjectGradleProperties);
            return new Loaded<>(projectGradleProperties, projectDir);
        }

        private Map<String, String> loadProjectLocalProperties(File projectDir) {
            Map<String, String> loadedProperties = environment.propertiesFile(new File(projectDir, Project.GRADLE_PROPERTIES));
            return loadedProperties == null ? ImmutableMap.of() : ImmutableMap.copyOf(loadedProperties);
        }
    }

    private static class Loaded<T extends GradleProperties> implements PropertiesState<T> {

        private final T gradleProperties;
        private final File propertiesDir;

        public Loaded(T gradleProperties, File propertiesDir) {
            this.gradleProperties = gradleProperties;
            this.propertiesDir = propertiesDir;
        }

        @Override
        public T gradleProperties() {
            return gradleProperties;
        }

        @Override
        public PropertiesState<T> loadProperties(File dir, boolean setSystemProperties) {
            checkSameLocation(dir);
            return this;
        }

        private void checkSameLocation(File dir) {
            if (!propertiesDir.equals(dir)) {
                throw new IllegalStateException(String.format(
                    "GradleProperties has already been loaded from '%s' and cannot be loaded from '%s'.",
                    propertiesDir, dir
                ));
            }
        }
    }
}
