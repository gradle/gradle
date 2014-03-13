/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.resolve.internal;

import org.gradle.api.Plugin;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.reflect.Instantiator;

class ClassPathPluginResolution implements PluginResolution {

    private final String pluginId;
    private final Instantiator instantiator;
    private final Factory<? extends ClassPath> classPathFactory;

    public ClassPathPluginResolution(Instantiator instantiator, String pluginId, Factory<? extends ClassPath> classPathFactory) {
        this.pluginId = pluginId;
        this.instantiator = instantiator;
        this.classPathFactory = classPathFactory;
    }

    public Class<? extends Plugin> resolve(ClassLoaderScope classLoaderScope) {
        ClassPath classPath = classPathFactory.create();
        ClassLoader classLoader = classLoaderScope.addLocal(classPath);
        PluginRegistry pluginRegistry = new DefaultPluginRegistry(classLoader, instantiator);
        Class<? extends Plugin> typeForId = pluginRegistry.getTypeForId(pluginId);
        return typeForId;
    }
}
