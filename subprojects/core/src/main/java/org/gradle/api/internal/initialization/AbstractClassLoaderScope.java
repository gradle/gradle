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

/**
 * Provides common {@code toString} and {@code createChild} behaviour for {@see ClassLoaderScope} implementations.
 */
public abstract class AbstractClassLoaderScope implements ClassLoaderScope {

    protected final ClassLoaderScopeIdentifier id;
    protected final ClassLoaderCache classLoaderCache;

    protected AbstractClassLoaderScope(ClassLoaderScopeIdentifier id, ClassLoaderCache classLoaderCache) {
        this.id = id;
        this.classLoaderCache = classLoaderCache;
    }

    @Override
    public ClassLoaderScope createChild(String name) {
        if (name == null) {
            throw new IllegalArgumentException("'name' cannot be null");
        }
        return new DefaultClassLoaderScope(id.child(name), this, classLoaderCache);
    }

    @Override
    public String toString() {
        return super.toString() + "{id=" + id.toString() + "}";
    }
}
