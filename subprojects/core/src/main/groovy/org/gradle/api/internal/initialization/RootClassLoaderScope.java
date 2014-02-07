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

import org.gradle.internal.classpath.ClassPath;

public class RootClassLoaderScope implements ClassLoaderScope {

    private final ClassLoader classLoader;
    private final ClassLoaderCache classLoaderCache;

    public RootClassLoaderScope(ClassLoader classLoader, ClassLoaderCache classLoaderCache) {
        this.classLoader = classLoader;
        this.classLoaderCache = classLoaderCache;
    }

    public ClassLoader getScopeClassLoader() {
        return classLoader;
    }

    public ClassLoader getChildClassLoader() {
        return classLoader;
    }

    public ClassLoaderScope getBase() {
        return this;
    }

    public ClassLoader addLocal(ClassPath classpath) {
        throw new UnsupportedOperationException("root class loader scope is immutable");
    }

    public ClassLoader export(ClassPath classpath) {
        throw new UnsupportedOperationException("root class loader scope is immutable");
    }

    public ClassLoaderScope createSibling() {
        return createChild();
    }

    public ClassLoaderScope createChild() {
        return new DefaultClassLoaderScope(this, this, classLoaderCache);
    }

    public ClassLoaderScope createRebasedChild() {
        return createChild();
    }

    public ClassLoaderScope lock() {
        return this;
    }

    public boolean isLocked() {
        return true;
    }
}
