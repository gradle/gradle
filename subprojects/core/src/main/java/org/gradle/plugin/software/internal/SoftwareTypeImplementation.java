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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;

/**
 * Represents a resolved software type implementation including the public model type and the plugin that exposes it.
 */
public interface SoftwareTypeImplementation<T> {
    String getSoftwareType();

    Class<? extends T> getModelPublicType();

    Class<? extends Plugin<Project>> getPluginClass();

    Class<? extends Plugin<Settings>> getRegisteringPluginClass();

    void addModelDefault(ModelDefault<?> modelDefault);

    /**
     * Visits all model defaults of the given type with the provided visitor.
     */
    <V extends ModelDefault.Visitor<?>> void visitModelDefaults(Class<? extends ModelDefault<V>> type, V visitor);
}
