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

package org.gradle.initialization;

import org.gradle.api.internal.initialization.AbstractClassLoaderScope;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.RootClassLoaderScope;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.internal.event.ListenerManager;

import java.io.Closeable;
import java.io.IOException;

public class DefaultClassLoaderScopeRegistry implements ClassLoaderScopeRegistry, Closeable {

    public static final String CORE_NAME = "core";
    public static final String CORE_AND_PLUGINS_NAME = "coreAndPlugins";

    private final AbstractClassLoaderScope coreAndPluginsScope;
    private final AbstractClassLoaderScope coreScope;
    private final ClassLoaderScopeRegistryListener scopeBroadcaster;
    private final ClassLoaderScopeListeners listeners;

    public DefaultClassLoaderScopeRegistry(ClassLoaderRegistry loaderRegistry, ClassLoaderCache classLoaderCache, ListenerManager listenerManager, ClassLoaderScopeListeners listeners) {
        scopeBroadcaster = listenerManager.getBroadcaster(ClassLoaderScopeRegistryListener.class);
        this.listeners = listeners;
        listeners.addListener(scopeBroadcaster);
        ClassLoaderScopeRegistryListener globalBroadcast = listeners.getBroadcast();
        this.coreScope = new RootClassLoaderScope(CORE_NAME, loaderRegistry.getRuntimeClassLoader(), loaderRegistry.getGradleCoreApiClassLoader(), classLoaderCache, globalBroadcast);
        this.coreAndPluginsScope = new RootClassLoaderScope(CORE_AND_PLUGINS_NAME, loaderRegistry.getPluginsClassLoader(), loaderRegistry.getGradleApiClassLoader(), classLoaderCache, globalBroadcast);
        rootScopesCreated(globalBroadcast);
    }

    @Override
    public void close() throws IOException {
        listeners.removeListener(scopeBroadcaster);
    }

    @Override
    public ClassLoaderScope getCoreAndPluginsScope() {
        return coreAndPluginsScope;
    }

    @Override
    public ClassLoaderScope getCoreScope() {
        return coreScope;
    }

    private void rootScopesCreated(ClassLoaderScopeRegistryListener listener) {
        listener.rootScopeCreated(coreScope.getId());
        listener.rootScopeCreated(coreAndPluginsScope.getId());
    }
}
