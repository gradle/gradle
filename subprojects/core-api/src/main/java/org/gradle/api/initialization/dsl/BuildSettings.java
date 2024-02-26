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

package org.gradle.api.initialization.dsl;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.declarative.dsl.model.annotations.Adding;

public interface BuildSettings {
    /**
     * Registers a plugin to be made available to build scripts and registers any extensions that the plugin provides to
     * be available in declarative build scripts.
     *
     * @param id The id of the plugin to register.
     */
    @Adding
    void registerPlugin(String id);

    interface DeclarativeExtension {
        String getName();

        Class<?> getPublicType();

        Class<? extends Plugin<Project>> getPluginClass();

        void initialize(Project project);
    }
}
