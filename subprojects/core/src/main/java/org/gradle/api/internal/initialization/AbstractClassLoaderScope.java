/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.initialization.ClassLoaderScopeId;
import org.gradle.initialization.ClassLoaderScopeOrigin;
import org.gradle.initialization.ClassLoaderScopeRegistryListener;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Provides common {@link #getPath} and {@link #createChild} behaviour for {@link ClassLoaderScope} implementations.
 */
public abstract class AbstractClassLoaderScope implements ClassLoaderScope {

    protected final ClassLoaderScopeIdentifier id;
    @Nullable
    protected final ClassLoaderScopeOrigin origin;
    protected final ClassLoaderCache classLoaderCache;
    protected final ClassLoaderScopeRegistryListener listener;

    protected AbstractClassLoaderScope(ClassLoaderScopeIdentifier id, @Nullable ClassLoaderScopeOrigin origin, ClassLoaderCache classLoaderCache, ClassLoaderScopeRegistryListener listener) {
        this.id = id;
        this.origin = origin;
        this.classLoaderCache = classLoaderCache;
        this.listener = listener;
    }

    /**
     * Unique identifier of this scope in the hierarchy.
     */
    public ClassLoaderScopeId getId() {
        return id;
    }

    @Nullable
    @Override
    public ClassLoaderScopeOrigin getOrigin() {
        return origin;
    }

    /**
     * A string representing the path of this {@link ClassLoaderScope} in the {@link ClassLoaderScope} graph.
     */
    public String getPath() {
        return id.getPath();
    }

    @Override
    public ClassLoaderScope local(ClassPath classPath) {
        return immutable();
    }

    @Override
    public ClassLoaderScope export(ClassPath classPath) {
        return immutable();
    }

    @Override
    public ClassLoaderScope export(ClassLoader classLoader) {
        return immutable();
    }

    private ClassLoaderScope immutable() {
        throw new UnsupportedOperationException(String.format("Class loader scope %s is immutable", id));
    }

    @Override
    public ClassLoaderScope createChild(String name, @Nullable ClassLoaderScopeOrigin origin) {
        return new MutableClassLoaderScope(new DefaultClassLoaderScope(id.child(name), this, origin, classLoaderCache, listener));
    }

    @Override
    public ClassLoaderScope createLockedChild(String name, @Nullable ClassLoaderScopeOrigin origin, ClassPath localClasspath, @Nullable HashCode classpathImplementationHash, @Nullable Function<ClassLoader, ClassLoader> localClassLoaderFactory) {
        return new ImmutableClassLoaderScope(id.child(name), this, origin, localClasspath, classpathImplementationHash, localClassLoaderFactory, classLoaderCache, listener);
    }

    @Override
    public ClassLoaderScope getOriginalScope() {
        return this;
    }
}
