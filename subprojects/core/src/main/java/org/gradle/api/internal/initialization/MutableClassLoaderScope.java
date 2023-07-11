/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.initialization.ClassLoaderScopeId;
import org.gradle.initialization.ClassLoaderScopeOrigin;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.function.Function;

public class MutableClassLoaderScope implements ClassLoaderScope {

    private ClassLoaderScope delegate;

    public MutableClassLoaderScope(
        ClassLoaderScope delegate
    ) {
        this.delegate = delegate;
    }


    @Override
    public ClassLoaderScopeId getId() {
        return delegate.getId();
    }

    @Nullable
    @Override
    public ClassLoaderScopeOrigin getOrigin() {
        return delegate.getOrigin();
    }

    @Override
    public ClassLoader getLocalClassLoader() {
        return delegate.getLocalClassLoader();
    }

    @Override
    public ClassLoader getExportClassLoader() {
        return delegate.getExportClassLoader();
    }

    @Override
    public ClassLoaderScope getParent() {
        return delegate.getParent();
    }

    @Override
    public boolean defines(Class<?> clazz) {
        return delegate.defines(clazz);
    }

    @Override
    public ClassLoaderScope local(ClassPath classPath) {
        return delegate.local(classPath);
    }

    @Override
    public ClassLoaderScope export(ClassPath classPath) {
        return delegate.export(classPath);
    }

    @Override
    public ClassLoaderScope export(ClassLoader classLoader) {
        return delegate.export(classLoader);
    }

    @Override
    public ClassLoaderScope createChild(String id, @Nullable ClassLoaderScopeOrigin origin) {
        return delegate.createChild(id, origin);
    }

    @Override
    public ClassLoaderScope createLockedChild(String id, @Nullable ClassLoaderScopeOrigin origin, ClassPath localClasspath, @Nullable HashCode classpathImplementationHash, @Nullable Function<ClassLoader, ClassLoader> localClassLoaderFactory) {
        return delegate.createLockedChild(id, origin, localClasspath, classpathImplementationHash, localClassLoaderFactory);
    }

    @Override
    public ClassLoaderScope lock() {
        return delegate.lock();
    }

    @Override
    public boolean isLocked() {
        return delegate.isLocked();
    }

    @Override
    public void onReuse() {
        delegate.onReuse();
    }

    @Override
    public ClassLoaderScope getOriginalScope() {
        return delegate;
    }

    public void mutate(String childSuffix) {
        delegate = delegate.createChild(delegate.getId() + childSuffix, null);
    }
}
