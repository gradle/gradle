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
package org.gradle.api.plugins.internal.gradleplatform;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interners;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.initialization.dsl.DependenciesModelBuilder;
import org.gradle.api.internal.std.AllDependenciesModel;
import org.gradle.api.internal.std.DefaultDependenciesModelBuilder;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.plugin.use.PluginDependencySpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Map;

public class DefaultGradlePlatformExtension implements GradlePlatformExtensionInternal {
    private final DefaultDependenciesModelBuilder builder;
    private final SimplifiedPluginDependenciesSpec plugins;
    private final Provider<AllDependenciesModel> model;
    private final Provider<Map<String, String>> pluginsModel;

    @Inject
    public DefaultGradlePlatformExtension(ObjectFactory objects, ProviderFactory providers) {
        this.plugins = new SimplifiedPluginDependenciesSpec();
        this.builder = objects.newInstance(DefaultDependenciesModelBuilder.class,
            "gradlePlatform",
            Interners.newStrongInterner(),
            Interners.newStrongInterner(),
            objects,
            providers,
            plugins
        );
        this.model = providers.provider(builder::build);
        this.pluginsModel = providers.provider(() -> ImmutableMap.copyOf(plugins.pluginVersions));
    }

    @Override
    public void dependenciesModel(Action<? super DependenciesModelBuilder> spec) {
        spec.execute(builder);
    }

    @Override
    public void plugins(Action<? super PluginDependenciesSpec> spec) {
        spec.execute(plugins);
    }

    @Override
    public Provider<AllDependenciesModel> getDependenciesModel() {
        return model;
    }

    @Override
    public Provider<Map<String, String>> getPluginVersions() {
        return pluginsModel;
    }

    private static class SimplifiedPluginDependenciesSpec implements PluginDependenciesSpec {
        private final Map<String, String> pluginVersions = Maps.newHashMap();

        @Override
        public PluginDependencySpec id(String id) {
            return new PluginDependencySpec() {
                @Override
                public PluginDependencySpec version(@Nullable String version) {
                    if (version == null || version.isEmpty()) {
                        throw new InvalidUserDataException("Plugin version shouldn't be null or empty");
                    }
                    pluginVersions.put(id, version);
                    return this;
                }

                @Override
                public PluginDependencySpec apply(boolean apply) {
                    throw new UnsupportedOperationException("Plugin application cannot be configured in a platform");
                }
            };
        }
    }
}
