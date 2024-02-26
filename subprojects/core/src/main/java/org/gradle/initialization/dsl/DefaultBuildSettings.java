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

package org.gradle.initialization.dsl;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.declarative.dsl.model.annotations.CreatesExtension;
import org.gradle.internal.Cast;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.internal.DefaultPluginId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

public class DefaultBuildSettings implements BuildSettingsInternal {
    private final Set<String> registeredPlugins = new LinkedHashSet<>();
    private final HashMap<Class<? extends Plugin<Project>>, List<DeclarativeExtension>> pluginToExtensions = new HashMap<>();

    @Override
    public void registerPlugin(String id) {
        registeredPlugins.add(id);
    }

    @Override
    public PluginRequests getRegisteredPluginRequests() {
        return PluginRequests.of(
            registeredPlugins.stream().map(id ->
                new DefaultPluginRequest(
                    DefaultPluginId.of(id),
                    false,
                    DefaultPluginRequest.Origin.OTHER,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)
            ).collect(Collectors.toList())
        );
    }

    @Override
    public Map<String, DeclarativeExtension> getDeclarativeExtensions(PluginRegistry pluginRegistry) {
        List<PluginImplementation<?>> declarativePlugins = stream(getRegisteredPluginRequests().spliterator(), false)
            .map(request -> pluginRegistry.lookup(request.getId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        List<DeclarativeExtension> extensions = new ArrayList<>();
        declarativePlugins.forEach(plugin -> {
            Class<? extends Plugin<Project>> pluginClass = Cast.uncheckedCast(plugin.asClass());
            extensions.addAll(pluginToExtensions.computeIfAbsent(pluginClass, p ->
                Arrays.stream(pluginClass.getAnnotationsByType(CreatesExtension.class))
                    .map(annotation -> new DefaultDeclarativeExtension(annotation.name(), annotation.publicType(), pluginClass))
                    .collect(Collectors.toList())
            ));
        });
        return extensions.stream().collect(Collectors.toMap(DeclarativeExtension::getName, e -> e));
    }

    private static class DefaultDeclarativeExtension implements DeclarativeExtension {
        private final String name;
        private final Class<?> publicType;
        private final Class<? extends Plugin<Project>> pluginClass;

        public DefaultDeclarativeExtension(String name, Class<?> publicType, Class<? extends Plugin<Project>> pluginClass) {
            this.name = name;
            this.publicType = publicType;
            this.pluginClass = pluginClass;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<?> getPublicType() {
            return publicType;
        }

        @Override
        public Class<? extends Plugin<Project>> getPluginClass() {
            return pluginClass;
        }

        @Override
        public void initialize(Project project) {
            if (!project.getPlugins().hasPlugin(pluginClass)) {
                project.getPlugins().apply(pluginClass);
            }
        }
    }
}
