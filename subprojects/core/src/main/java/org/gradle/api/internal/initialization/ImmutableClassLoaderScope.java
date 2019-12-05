/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.initialization.ClassLoaderScopeRegistryListener;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * A simplified scope that provides only a single local classpath and no exports, and that cannot be mutated.
 */
public class ImmutableClassLoaderScope extends AbstractClassLoaderScope {
    private final ClassLoaderScope parent;
    private final ClassPath classPath;
    @Nullable
    private final HashCode classpathImplementationHash;
    private final ClassLoader localClassLoader;

    public ImmutableClassLoaderScope(ClassLoaderScopeIdentifier id, ClassLoaderScope parent, ClassPath classPath, @Nullable HashCode classpathImplementationHash,
                                     @Nullable Function<ClassLoader, ClassLoader> localClassLoaderFactory, ClassLoaderCache classLoaderCache, ClassLoaderScopeRegistryListener listener) {
        super(id, classLoaderCache, listener);
        this.parent = parent;
        this.classPath = classPath;
        this.classpathImplementationHash = classpathImplementationHash;
        listener.childScopeCreated(parent.getId(), id);
        ClassLoaderId classLoaderId = id.localId();
        if (localClassLoaderFactory != null) {
            localClassLoader = classLoaderCache.createIfAbsent(classLoaderId, classPath, parent.getExportClassLoader(), localClassLoaderFactory, classpathImplementationHash);
        } else {
            localClassLoader = classLoaderCache.get(classLoaderId, classPath, parent.getExportClassLoader(), null, classpathImplementationHash);
        }
        listener.classloaderCreated(id, classLoaderId, localClassLoader, classPath, classpathImplementationHash);
    }

    @Override
    public ClassLoaderScope getParent() {
        return parent;
    }

    @Override
    public ClassLoader getExportClassLoader() {
        return parent.getExportClassLoader();
    }

    @Override
    public ClassLoader getLocalClassLoader() {
        return localClassLoader;
    }

    @Override
    public boolean defines(Class<?> clazz) {
        return localClassLoader.equals(clazz.getClassLoader());
    }

    @Override
    public void onReuse() {
        parent.onReuse();
        listener.childScopeCreated(parent.getId(), id);
        listener.classloaderCreated(id, id.localId(), localClassLoader, classPath, classpathImplementationHash);
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
