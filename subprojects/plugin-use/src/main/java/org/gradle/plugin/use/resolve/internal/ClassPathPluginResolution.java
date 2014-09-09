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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.Plugin;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.internal.PluginId;

public class ClassPathPluginResolution implements PluginResolution {

    private final PluginId pluginId;
    private final Instantiator instantiator;
    private final ClassLoaderScope parent;
    private final Factory<? extends ClassPath> classPathFactory;

    public ClassPathPluginResolution(Instantiator instantiator, PluginId pluginId, ClassLoaderScope parent, Factory<? extends ClassPath> classPathFactory) {
        this.pluginId = pluginId;
        this.instantiator = instantiator;
        this.parent = parent;
        this.classPathFactory = classPathFactory;
    }

    public PluginId getPluginId() {
        return pluginId;
    }

    public Class<? extends Plugin> resolve() {
        ClassPath classPath = classPathFactory.create();
        ClassLoaderScope loaderScope = parent.createChild();
        loaderScope.local(classPath);
        loaderScope.lock();
        PluginRegistry pluginRegistry = new DefaultPluginRegistry(loaderScope.getLocalClassLoader(), instantiator);
        return pluginRegistry.getTypeForId(pluginId.toString());
    }
}
