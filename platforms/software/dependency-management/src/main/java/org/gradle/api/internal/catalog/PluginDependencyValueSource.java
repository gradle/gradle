/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog;

import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultPluginDependency;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.plugin.use.PluginDependency;

public abstract class PluginDependencyValueSource implements ValueSource<PluginDependency, PluginDependencyValueSource.Params> {

    interface Params extends ValueSourceParameters {
        Property<String> getPluginName();

        Property<DefaultVersionCatalog> getConfig();
    }

    @Override
    public PluginDependency obtain() {
        String pluginName = getParameters().getPluginName().get();
        PluginModel data = getParameters().getConfig().get().getPlugin(pluginName);
        ImmutableVersionConstraint version = data.getVersion();
        return new DefaultPluginDependency(
            data.getId(), new DefaultMutableVersionConstraint(version)
        );
    }
}
