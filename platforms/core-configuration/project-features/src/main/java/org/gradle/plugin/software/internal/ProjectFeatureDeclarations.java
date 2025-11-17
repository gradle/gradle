/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.software.internal;

import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Declarations of project features implemented by plugins.
 */
@ServiceScope(Scope.Build.class) // TODO: Might be too specific a scope, but needed something there
public interface ProjectFeatureDeclarations {
    /**
     * Declares a plugin as providing a project feature.  Cannot be called again once the list of project features has been
     * queried via {@link #getProjectFeatureImplementations()}.
     */
    void addDeclaration(@Nullable String pluginId, Class<? extends Plugin<Project>> pluginClass, Class<? extends Plugin<Settings>> registeringPluginClass);

    /**
     * Returns a map of available project features, along with their types and associated plugins, keyed by project feature name.  Note that once
     * method is called, calling {@link #addDeclaration(String, Class, Class)} will result in an error.
     */
    Map<String, ProjectFeatureImplementation<?, ?>> getProjectFeatureImplementations();

    /**
     * Returns the schema for the declared project features.
     */
    NamedDomainObjectCollectionSchema getSchema();
}
