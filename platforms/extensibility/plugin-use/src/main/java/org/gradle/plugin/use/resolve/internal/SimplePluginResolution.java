/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.plugin.use.PluginId;

import java.util.Collections;
import java.util.List;

public class SimplePluginResolution implements PluginResolution {
    private final PluginImplementation<?> plugin;
    private final List<Dependency> companionDependencies;

    public SimplePluginResolution(PluginImplementation<?> plugin) {
        this(plugin, Collections.emptyList());
    }

    /**
     * @param companionDependencies dependencies the plugin's descriptor declares as required on the
     * script classpath alongside the plugin (see
     * {@link org.gradle.api.internal.plugins.PluginDescriptor#getDistributionCompanionModules()});
     * visited so the applicator adds them to the script classpath before it resolves.
     */
    public SimplePluginResolution(PluginImplementation<?> plugin, List<Dependency> companionDependencies) {
        this.plugin = plugin;
        this.companionDependencies = companionDependencies;
    }

    @Override
    public PluginId getPluginId() {
        return plugin.getPluginId();
    }

    @Override
    public void accept(PluginResolutionVisitor visitor) {
        for (Dependency dependency : companionDependencies) {
            visitor.visitDependency(dependency);
        }
    }

    @Override
    public void applyTo(PluginManagerInternal pluginManager) {
        pluginManager.apply(plugin);
    }
}
