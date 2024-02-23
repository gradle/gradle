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
import org.gradle.api.plugins.DeclarativeExtensionRegistry;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.reflect.TypeOf;

import java.util.HashMap;
import java.util.Map;

@NonNullApi
public class DefaultDeclarativeExtensionRegistry implements DeclarativeExtensionRegistry {
    private final PluginContainer pluginContainer;

    private final Map<String, Registration<?>> registrations = new HashMap<>();

    public DefaultDeclarativeExtensionRegistry(PluginContainer pluginContainer) {
        this.pluginContainer = pluginContainer;
    }

    @Override
    public <T> void register(String name, Class<T> type, Class<? extends Plugin<Project>> pluginType) {
        registrations.put(name, new Registration<>(TypeOf.typeOf(type), pluginType));
    }

    @Override
    public void initialize(String name) {
        Class<? extends Plugin<Project>> pluginType = registrations.get(name).getPluginType();
        if (!pluginContainer.hasPlugin(pluginType)) {
            pluginContainer.apply(pluginType);
        }
    }

    @Override
    public Map<String, TypeOf<?>> getNamesToPublicTypes() {
        return registrations.entrySet().stream().collect(
            HashMap::new,
            (map, entry) -> map.put(entry.getKey(), entry.getValue().getPublicType()),
            HashMap::putAll
        );
    }

    private static class Registration<T> {
        private final TypeOf<T> publicType;
        private final Class<? extends Plugin<Project>> pluginType;

        public Registration(TypeOf<T> publicType, Class<? extends Plugin<Project>> pluginType) {
            this.publicType = publicType;
            this.pluginType = pluginType;
        }

        public TypeOf<T> getPublicType() {
            return publicType;
        }

        public Class<? extends Plugin<Project>> getPluginType() {
            return pluginType;
        }
    }
}
