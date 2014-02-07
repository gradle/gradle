/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Plugin;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.internal.reflect.Instantiator;

public interface PluginRegistry {
    <T extends Plugin> T loadPlugin(Class<T> pluginClass) throws PluginInstantiationException;

    Class<? extends Plugin> getTypeForId(String pluginId) throws UnknownPluginException, PluginInstantiationException;

    /**
     * Creates a child registry which uses the plugins declared in the given script scope.
     */
    PluginRegistry createChild(ClassLoaderScope lookupScope, Instantiator instantiator);
}
