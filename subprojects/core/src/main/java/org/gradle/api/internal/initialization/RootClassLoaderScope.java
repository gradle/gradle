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
import org.gradle.internal.classpath.ClassPath;

public class RootClassLoaderScope extends AbstractClassLoaderScope {

    private final ClassLoader localClassLoader;
    private final ClassLoader exportClassLoader;

    public RootClassLoaderScope(ClassLoader localClassLoader, ClassLoader exportClassLoader, ClassLoaderCache classLoaderCache) {
        super(new ClassLoaderScopeIdentifier(null, "root"), classLoaderCache);
        this.localClassLoader = localClassLoader;
        this.exportClassLoader = exportClassLoader;
    }

    @Override
    public ClassLoader getLocalClassLoader() {
        return localClassLoader;
    }

    @Override
    public ClassLoader getExportClassLoader() {
        return exportClassLoader;
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
    public ClassLoaderScope local(ClassPath classPath) {
        throw new UnsupportedOperationException("root class loader scope is immutable");
    }

    @Override
    public ClassLoaderScope export(ClassPath classPath) {
        throw new UnsupportedOperationException("root class loader scope is immutable");
    }

    @Override
    public ClassLoaderScope export(ClassLoader classLoader) {
        throw new UnsupportedOperationException("root class loader scope is immutable");
    }

    @Override
    public ClassLoaderScope lock() {
        return this;
    }

    @Override
    public boolean isLocked() {
        return true;
    }
}
