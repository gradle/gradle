/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Optional;

@ServiceScope({Scope.Build.class, Scope.Gradle.class, Scope.Settings.class, Scope.Project.class})
@ThreadSafe
public interface PluginRegistry {
    <T> PluginImplementation<T> inspect(Class<T> clazz);

    /**
     * Extracts plugin information for the given class, if known to this registry.
     */
    @Nullable
    <T> PluginImplementation<T> maybeInspect(Class<T> clazz);

    /**
     * Locates the plugin with the given id. Note that the id of the result may be different to the requested id.
     */
    @Nullable
    PluginImplementation<?> lookup(PluginId pluginId);

    PluginRegistry createChild(ClassLoaderScope lookupScope);

    /**
     * Finds the plugin id which corresponds to the supplied class name.
     * @param clazz the class to look for
     * @return the plugin id for this class.
     */
    Optional<PluginId> findPluginForClass(Class<?> clazz);

}
