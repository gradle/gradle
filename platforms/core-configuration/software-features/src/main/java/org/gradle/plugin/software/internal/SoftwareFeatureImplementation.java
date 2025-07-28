/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.api.internal.plugins.SoftwareFeatureTransform;
import org.gradle.api.internal.plugins.TargetTypeInformation;
import org.jspecify.annotations.Nullable;

/**
 * Represents a resolved software type implementation including the public model type and the plugin that exposes it.
 *
 * This interface does not require the type arguments T and V to follow the bound software feature
 * definition and model type limitations.
 * Therefore, it can be used for software features with any definition and model types.
 *
 * TODO: Move the type constraints from {@link BoundSoftwareFeatureImplementation} to here once the migration from legacy software types is done.
 */
public interface SoftwareFeatureImplementation<T, V> {
    String getFeatureName();

    Class<T> getDefinitionPublicType();

    Class<? extends T> getDefinitionImplementationType();

    TargetTypeInformation<?> getTargetDefinitionType();

    Class<V> getBuildModelType();

    Class<? extends V> getBuildModelImplementationType();

    Class<? extends Plugin<Project>> getPluginClass();

    Class<? extends Plugin<Settings>> getRegisteringPluginClass();

    @Nullable String getRegisteringPluginId();

    SoftwareFeatureTransform<T, V, ?> getBindingTransform();

    void addModelDefault(ModelDefault<?> modelDefault);

    /**
     * Visits all model defaults of the given type with the provided visitor.
     */
    <M extends ModelDefault.Visitor<?>> void visitModelDefaults(Class<? extends ModelDefault<M>> type, M visitor);
}
