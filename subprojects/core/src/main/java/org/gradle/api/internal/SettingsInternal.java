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

package org.gradle.api.internal;

import org.gradle.StartParameter;
import org.gradle.api.Incubating;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.PluginAwareInternal;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.declarative.dsl.model.annotations.Adding;
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.ProjectDescriptorInternal;
import org.gradle.initialization.ProjectDescriptorRegistry;
import org.gradle.internal.FinalizableValue;
import org.gradle.internal.initialization.BuildLogicFiles;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.service.ServiceRegistry;

import java.net.URI;
import java.util.List;

public interface SettingsInternal extends Settings, PluginAwareInternal, FinalizableValue {

    String BUILD_SRC = BuildLogicFiles.BUILD_SOURCE_DIRECTORY;

    @Override
    @HiddenInDefinition
    StartParameter getStartParameter();

    @HiddenInDefinition
    ScriptSource getSettingsScript();

    @HiddenInDefinition
    ProjectDescriptorRegistry getProjectRegistry();

    @HiddenInDefinition
    ProjectDescriptorInternal getDefaultProject();

    @HiddenInDefinition
    void setDefaultProject(ProjectDescriptorInternal defaultProject);

    @Override
    @HiddenInDefinition
    GradleInternal getGradle();

    @HiddenInDefinition
    List<IncludedBuildSpec> getIncludedBuilds();

    /**
     * The parent scope for this and all settings objects.
     *
     * Gradle runtime.
     */
    @HiddenInDefinition
    ClassLoaderScope getBaseClassLoaderScope();

    /**
     * The scope for this settings object.
     *
     * Gradle runtime + this object's script's additions.
     */
    @HiddenInDefinition
    ClassLoaderScope getClassLoaderScope();

    @HiddenInDefinition
    ServiceRegistry getServices();

    @Override
    @HiddenInDefinition
    BuildCacheConfigurationInternal getBuildCache();

    @Override
    @HiddenInDefinition
    DependencyResolutionManagementInternal getDependencyResolutionManagement();

    @Override
    @HiddenInDefinition
    CacheConfigurationsInternal getCaches();

    /**
     * This is a version of {@link Settings#include(String...)} for just one argument.
     * If varargs get supoprted in Declarative DSL this overload will no longer be needed.
     */
    @Adding
    @Incubating
    default void include(String projectPath) {
        include(new String[]{projectPath});
    }

    /**
     * A {@link URI} factory function exposed to DCL.
     * Mimics {@code SettingsScriptApi.uri} available in Kotlin DSL.
     * Primarily for use in {@link org.gradle.api.artifacts.repositories.MavenArtifactRepository#setUrl}
     *
     * @see FileOperations#uri
     */
    @Incubating
    default URI uri(String path) {
        return getServices().get(FileOperations.class).uri(path);
    }
}
