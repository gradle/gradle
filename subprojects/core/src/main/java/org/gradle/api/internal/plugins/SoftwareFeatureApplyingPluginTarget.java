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

package org.gradle.api.internal.plugins;

import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.Cast;
import org.gradle.plugin.software.internal.SoftwareFeatureApplicator;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

import javax.annotation.Nullable;

@NonNullApi
public class SoftwareFeatureApplyingPluginTarget implements PluginTarget {
    private final PluginTarget delegate;
    private final Project target;
    private final SoftwareTypeRegistry softwareTypeRegistry;
    private final SoftwareFeatureApplicator softwareFeatureApplicator;

    public SoftwareFeatureApplyingPluginTarget(Project target, PluginTarget delegate, SoftwareTypeRegistry softwareTypeRegistry, SoftwareFeatureApplicator softwareFeatureApplicator) {
        this.target = target;
        this.delegate = delegate;
        this.softwareTypeRegistry = softwareTypeRegistry;
        this.softwareFeatureApplicator = softwareFeatureApplicator;
    }

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return delegate.getConfigurationTargetIdentifier();
    }

    @Override
    public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
        delegate.applyImperative(pluginId, plugin);
    }

    @Override
    public void applyRules(@Nullable String pluginId, Class<?> clazz) {
        delegate.applyRules(pluginId, clazz);
    }

    @Override
    public void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin, Class<?> declaringClass) {
        delegate.applyImperativeRulesHybrid(pluginId, plugin, declaringClass);
    }

    @Override
    public void applySoftwareFeatures(Plugin<?> plugin) {
        softwareTypeRegistry.implementationFor(Cast.uncheckedCast(plugin.getClass())).ifPresent(softwareTypeImplementation -> {
            softwareFeatureApplicator.applyFeatureTo(target, softwareTypeImplementation);
        });
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
