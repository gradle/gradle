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

package org.gradle.api.internal.initialization;

import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.initialization.ClassLoaderScopeRegistryListener;
import org.gradle.internal.classloader.CachingClassLoader;

public class RootClassLoaderScope extends AbstractClassLoaderScope {

    private final ClassLoader localClassLoader;
    private final CachingClassLoader cachingLocalClassLoader;
    private final ClassLoader exportClassLoader;
    private final CachingClassLoader cachingExportClassLoader;

    public RootClassLoaderScope(String name, ClassLoader localClassLoader, ClassLoader exportClassLoader, ClassLoaderCache classLoaderCache, ClassLoaderScopeRegistryListener listener) {
        super(new ClassLoaderScopeIdentifier(null, name), classLoaderCache, listener);
        this.localClassLoader = localClassLoader;
        this.cachingLocalClassLoader = new CachingClassLoader(localClassLoader);
        this.exportClassLoader = exportClassLoader;
        this.cachingExportClassLoader = new CachingClassLoader(exportClassLoader);
    }

    @Override
    public ClassLoader getLocalClassLoader() {
        return cachingLocalClassLoader;
    }

    @Override
    public ClassLoader getExportClassLoader() {
        return cachingExportClassLoader;
    }

    @Override
    public ClassLoaderScope getParent() {
        return this; // should this be null?
    }

    @Override
    public boolean defines(Class<?> clazz) {
        return localClassLoader.equals(clazz.getClassLoader()) || exportClassLoader.equals(clazz.getClassLoader());
    }

    @Override
    public ClassLoaderScope lock() {
        return this;
    }

    @Override
    public boolean isLocked() {
        return true;
    }

    @Override
    public void onReuse() {
        // Nothing to do
    }
}
